package com.example.anthropicproxy.service;

import com.example.anthropicproxy.model.anthropic.*;
import com.example.anthropicproxy.model.openai.*;
import com.example.anthropicproxy.model.openai.OpenAIStreamChunk;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversionService {
    private final ModelMappingService modelMappingService;
    private final ObjectMapper objectMapper;

    /**
     * Extract text content from Anthropic message content
     * Content can be String or List<AnthropicMessageContent> or List<Map>
     */
    public String extractTextContent(Object content) {
        if (content == null) {
            return "";
        }

        if (content instanceof String) {
            return (String) content;
        }

        if (content instanceof List) {
            List<?> contentList = (List<?>) content;
            List<String> textParts = new ArrayList<>();

            for (Object item : contentList) {
                if (item instanceof AnthropicMessageContent) {
                    AnthropicMessageContent messageContent = (AnthropicMessageContent) item;
                    if ("text".equals(messageContent.getType())) {
                        textParts.add(messageContent.getText());
                    } else {
                        textParts.add(messageContent.getText());
                    }
                } else if (item instanceof Map) {
                    Map<?, ?> itemMap = (Map<?, ?>) item;
                    if ("text".equals(itemMap.get("type"))) {
                        textParts.add((String) itemMap.get("text"));
                    } else if (itemMap.containsKey("text")) {
                        textParts.add((String) itemMap.get("text"));
                    } else {
                        textParts.add(itemMap.toString());
                    }
                } else {
                    textParts.add(item.toString());
                }
            }

            return String.join("\n", textParts);
        }

        return content.toString();
    }

    /**
     * Convert Anthropic messages to OpenAI messages
     */
    public List<OpenAIMessage> convertMessages(List<AnthropicMessage> anthropicMessages, String systemPrompt) {
        List<OpenAIMessage> openaiMessages = new ArrayList<>();

        // Add system message if present
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            openaiMessages.add(OpenAIMessage.builder()
                    .role(OpenAIRole.SYSTEM)
                    .content(systemPrompt)
                    .build());
        }

        // Convert user and assistant messages
        for (AnthropicMessage msg : anthropicMessages) {
            String content = extractTextContent(msg.getContent());

            switch (msg.getRole()) {
                case USER:
                    openaiMessages.add(OpenAIMessage.builder()
                            .role(OpenAIRole.USER)
                            .content(content)
                            .build());
                    break;
                case ASSISTANT:
                    openaiMessages.add(OpenAIMessage.builder()
                            .role(OpenAIRole.ASSISTANT)
                            .content(content)
                            .build());
                    break;
                default:
                    log.warn("Unknown Anthropic role: {}, converting to user", msg.getRole());
                    openaiMessages.add(OpenAIMessage.builder()
                            .role(OpenAIRole.USER)
                            .content("[" + msg.getRole() + "]: " + content)
                            .build());
            }
        }

        return openaiMessages;
    }

    /**
     * Convert Anthropic completion request to OpenAI completion request
     */
    public OpenAICompletionRequest convertRequest(AnthropicCompletionRequest anthropicRequest) {
        // Map model
        String openaiModel = modelMappingService.mapModel(anthropicRequest.getModel());

        // Convert messages
        List<OpenAIMessage> openaiMessages = convertMessages(
                anthropicRequest.getMessages(),
                anthropicRequest.getSystem()
        );

        // Build OpenAI request
        OpenAICompletionRequest.OpenAICompletionRequestBuilder requestBuilder = OpenAICompletionRequest.builder()
                .model(openaiModel)
                .messages(openaiMessages)
                .temperature(adjustTemperature(anthropicRequest.getTemperature()))
                .topP(anthropicRequest.getTopP())
                .stream(anthropicRequest.getStream())
                .maxTokens(anthropicRequest.getMaxTokens())
                .n(1); // Anthropic only supports n=1

        // Handle stop sequences
        if (anthropicRequest.getStopSequences() != null && !anthropicRequest.getStopSequences().isEmpty()) {
            if (anthropicRequest.getStopSequences().size() == 1) {
                requestBuilder.stop(anthropicRequest.getStopSequences().get(0));
            } else {
                requestBuilder.stop(new ArrayList<>(anthropicRequest.getStopSequences()));
            }
        }

        // Log unsupported parameters
        if (anthropicRequest.getMetadata() != null) {
            log.warn("Anthropic parameter 'metadata' not supported in OpenAI");
        }
        if (anthropicRequest.getThinking() != null) {
            log.warn("Anthropic parameter 'thinking' not supported in OpenAI");
        }

        log.info("Request conversion complete: Anthropic model '{}' -> OpenAI model '{}'",
                anthropicRequest.getModel(), openaiModel);
        log.info("Message count: {}, streaming: {}",
                openaiMessages.size(), anthropicRequest.getStream() != null && anthropicRequest.getStream());

        return requestBuilder.build();
    }

    /**
     * Adjust temperature: Anthropic uses 0-1, OpenAI uses 0-2
     */
    private Double adjustTemperature(Double temperature) {
        if (temperature == null) {
            return 1.0;
        }
        return Math.min(temperature * 2.0, 2.0);
    }

    /**
     * Convert OpenAI response to Anthropic response format
     */
    public AnthropicCompletionResponse convertResponse(
            OpenAICompletionResponse openaiResponse,
            String anthropicModel,
            String requestId
    ) {
        if (openaiResponse.getChoices() == null || openaiResponse.getChoices().isEmpty()) {
            throw new IllegalArgumentException("OpenAI response has no choices");
        }

        OpenAIChoice choice = openaiResponse.getChoices().get(0);
        OpenAIMessage message = choice.getMessage();
        String contentText = message.getContent() != null ? message.getContent().toString() : "";

        // Build Anthropic response
        return AnthropicCompletionResponse.builder()
                .id(requestId)
                .model(anthropicModel)
                .content(Collections.singletonList(
                        AnthropicContentBlock.builder()
                                .type("text")
                                .text(contentText)
                                .build()
                ))
                .usage(AnthropicUsage.builder()
                        .inputTokens(openaiResponse.getUsage() != null ? openaiResponse.getUsage().getPromptTokens() : 0)
                        .outputTokens(openaiResponse.getUsage() != null ? openaiResponse.getUsage().getCompletionTokens() : 0)
                        .build())
                .stopReason(choice.getFinishReason())
                .build();
    }

    /**
     * Convert OpenAI streaming chunk to Anthropic streaming format
     * Returns JSON string for Anthropic response, or null for [DONE] or invalid chunks
     */
    public String convertStreamChunk(String openaiChunkLine, String anthropicModel, String requestId) {
        try {
            // Check if it's the final "[DONE]" message
            if (openaiChunkLine.trim().equals("data: [DONE]")) {
                // Return null to indicate completion
                return null;
            }

            // Parse the SSE line: "data: {...}"
            if (!openaiChunkLine.startsWith("data: ")) {
                log.warn("Unexpected streaming line format: {}", openaiChunkLine);
                return null;
            }

            String jsonStr = openaiChunkLine.substring(6); // Remove "data: "
            if (jsonStr.trim().isEmpty()) {
                return null;
            }

            // Parse OpenAI chunk
            OpenAIStreamChunk openaiChunk = objectMapper.readValue(jsonStr, OpenAIStreamChunk.class);

            // Build Anthropic streaming response
            Map<String, Object> anthropicChunk = new HashMap<>();

            // Set basic fields
            anthropicChunk.put("type", "message");
            anthropicChunk.put("role", "assistant");
            anthropicChunk.put("model", anthropicModel);
            anthropicChunk.put("id", requestId);

            // Extract content from OpenAI delta
            List<Map<String, Object>> contentList = new ArrayList<>();
            if (openaiChunk.getChoices() != null && !openaiChunk.getChoices().isEmpty()) {
                OpenAIStreamChunk.OpenAIStreamChoice choice = openaiChunk.getChoices().get(0);
                if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                    Map<String, Object> contentBlock = new HashMap<>();
                    contentBlock.put("type", "text");
                    contentBlock.put("text", choice.getDelta().getContent());
                    contentList.add(contentBlock);
                }

                // Set stop reason if present
                if (choice.getFinishReason() != null) {
                    anthropicChunk.put("stop_reason", choice.getFinishReason());
                }
            }

            anthropicChunk.put("content", contentList);

            // Convert to JSON string (without SSE wrapper)
            return objectMapper.writeValueAsString(anthropicChunk);

        } catch (Exception e) {
            log.error("Error converting streaming chunk", e);
            return null;
        }
    }
}