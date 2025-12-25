package com.example.anthropicproxy.controller;

import com.example.anthropicproxy.model.anthropic.AnthropicCompletionRequest;
import com.example.anthropicproxy.model.anthropic.AnthropicCompletionResponse;
import com.example.anthropicproxy.model.openai.OpenAICompletionRequest;
import com.example.anthropicproxy.model.openai.OpenAICompletionResponse;
import com.example.anthropicproxy.service.ConversionService;
import com.example.anthropicproxy.service.OpenAIClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
                {"claude-3-sonnet-20240229", "gpt-4"},
                {"claude-3-sonnet", "gpt-4"},
                {"claude-3-opus-20240229", "gpt-4o"},
                {"claude-3-opus", "gpt-4o"},
                {"claude-2.1", "gpt-4"},
                {"claude-2.0", "gpt-4"},
                {"claude-instant-1.2", "gpt-3.5-turbo"}
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
    public Mono<ResponseEntity<Object>> createMessage(@RequestBody AnthropicCompletionRequest request) {
        log.info("Received message request, model: {}, streaming: {}",
                request.getModel(), request.getStream());

        // Generate request ID
        String requestId = "msg_" + UUID.randomUUID().toString().substring(0, 8);

        // Convert request
        OpenAICompletionRequest openaiRequest = conversionService.convertRequest(request);

        // Check if streaming
        if (request.getStream() != null && request.getStream()) {
            // Streaming response (to be implemented)
            log.warn("Streaming not yet implemented, falling back to non-streaming");
            return createNonStreamingResponse(openaiRequest, request, requestId);
        } else {
            // Non-streaming response
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
}