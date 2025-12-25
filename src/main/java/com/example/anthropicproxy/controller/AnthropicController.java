package com.example.anthropicproxy.controller;

import com.example.anthropicproxy.model.anthropic.AnthropicCompletionRequest;
import com.example.anthropicproxy.model.anthropic.AnthropicCompletionResponse;
import com.example.anthropicproxy.model.openai.OpenAICompletionRequest;
import com.example.anthropicproxy.model.openai.OpenAICompletionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.anthropicproxy.service.ConversionService;
import com.example.anthropicproxy.service.OpenAIClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1")
@Slf4j
@RequiredArgsConstructor
public class AnthropicController {
    private final ConversionService conversionService;
    private final OpenAIClientService openAIClientService;
    private final ObjectMapper objectMapper;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "Anthropic Proxy API");
        response.put("description", "Provides Anthropic API interface, calls OpenAI ChatGPT under the hood");
        response.put("status", "running");
        response.put("endpoints", Map.of(
                "/v1/messages", "Chat messages (main endpoint)",
                "/v1/models", "Available models list",
                "/health", "Health check",
                "/docs", "API documentation"
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        // Simple health check - always healthy if service is running
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels() {
        // Return supported Anthropic models (mapped to OpenAI)
        List<Map<String, Object>> models = new ArrayList<>();

        // Model mapping from ModelMappingService (hardcoded for now)
        String[][] modelPairs = {
                {"claude-3-haiku-20240307", "gpt-3.5-turbo"},
                {"claude-3-haiku", "gpt-3.5-turbo"},
                {"claude-3-sonnet-20240229", "gpt-4.1"},
                {"claude-3-sonnet", "gpt-4.1"},
                {"claude-3-opus-20240229", "gpt-4o"},
                {"claude-3-opus", "gpt-4o"},
                {"claude-2.1", "gpt-4.1"},
                {"claude-2.0", "gpt-4.1"},
                {"claude-instant-1.2", "gpt-3.5-turbo"},
                {"claude-4.5-sonnet-20251209", "gpt-5"},
                {"claude-4.5-sonnet", "gpt-5"}
        };

        for (String[] pair : modelPairs) {
            Map<String, Object> model = new HashMap<>();
            model.put("id", pair[0]);
            model.put("object", "model");
            model.put("created", 1686935000L); // Fixed timestamp
            model.put("owned_by", "openai-via-proxy");
            model.put("permission", Collections.emptyList());
            model.put("root", pair[0]);
            model.put("parent", null);
            models.add(model);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("object", "list");
        response.put("data", models);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/messages")
    public Object createMessage(@RequestBody AnthropicCompletionRequest request) {
        log.info("Received message request, model: {}, streaming: {}",
                request.getModel(), request.getStream());

        // Generate request ID
        String requestId = "msg_" + UUID.randomUUID().toString().substring(0, 8);

        // Convert request
        OpenAICompletionRequest openaiRequest = conversionService.convertRequest(request);

        // Check if streaming
        if (request.getStream() != null && request.getStream()) {
            // Streaming response - return SseEmitter directly
            log.info("Creating streaming response");
            return createStreamingResponse(openaiRequest, request, requestId);
        } else {
            // Non-streaming response - return Mono<ResponseEntity<Object>>
            return createNonStreamingResponse(openaiRequest, request, requestId);
        }
    }

    private Mono<ResponseEntity<Object>> createNonStreamingResponse(
            OpenAICompletionRequest openaiRequest,
            AnthropicCompletionRequest anthropicRequest,
            String requestId
    ) {
        return openAIClientService.createCompletion(openaiRequest)
                .map(openaiResponse -> {
                    AnthropicCompletionResponse anthropicResponse = conversionService.convertResponse(
                            openaiResponse,
                            anthropicRequest.getModel(),
                            requestId
                    );
                    return ResponseEntity.ok().body((Object) anthropicResponse);
                })
                .onErrorResume(error -> {
                    log.error("Error processing request", error);
                    Map<String, Object> errorResponse = new HashMap<>();
                    Map<String, Object> errorDetail = new HashMap<>();
                    errorDetail.put("type", "api_error");
                    errorDetail.put("message", "Error processing request: " + error.getMessage());
                    errorResponse.put("error", errorDetail);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((Object) errorResponse));
                });
    }

    private SseEmitter createStreamingResponse(
            OpenAICompletionRequest openaiRequest,
            AnthropicCompletionRequest anthropicRequest,
            String requestId
    ) {
        log.info("Starting streaming response for request: {}", requestId);

        // Create SSE emitter with long timeout (required for streaming)
        SseEmitter emitter = new SseEmitter(60_000L); // 60 seconds timeout

        // Get streaming flux from OpenAI
        Flux<String> openaiStream = openAIClientService.createCompletionStream(openaiRequest);

        // Convert each chunk to Anthropic format
        Flux<String> anthropicChunks = openaiStream
                .map(chunkLine -> conversionService.convertStreamChunk(chunkLine, anthropicRequest.getModel(), requestId))
                .filter(chunk -> chunk != null);

        // Subscribe to flux and send SSE events
        anthropicChunks.subscribe(
                jsonData -> {
                    try {
                        emitter.send(SseEmitter.event().data(jsonData));
                    } catch (Exception e) {
                        log.error("Error sending SSE event", e);
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("Error in streaming response", error);
                    try {
                        // Create error response in Anthropic format
                        Map<String, Object> errorResponse = new HashMap<>();
                        Map<String, Object> errorDetail = new HashMap<>();
                        errorDetail.put("type", "api_error");
                        errorDetail.put("message", "OpenAI API error: " + error.getMessage());
                        errorResponse.put("error", errorDetail);
                        String errorJson = objectMapper.writeValueAsString(errorResponse);
                        emitter.send(SseEmitter.event().data(errorJson));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Failed to send error response", e);
                        emitter.completeWithError(e);
                    }
                },
                () -> {
                    log.info("Streaming completed for request: {}", requestId);
                    emitter.complete();
                }
        );

        // Handle emitter completion and timeout
        emitter.onCompletion(() -> log.info("SSE emitter completed for request: {}", requestId));
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout for request: {}", requestId);
            emitter.complete();
        });
        emitter.onError(error -> log.error("SSE emitter error for request: {}", requestId, error));

        return emitter;
    }
}