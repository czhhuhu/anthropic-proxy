package com.example.anthropicproxy.service;

import com.example.anthropicproxy.config.OpenAIConfigProperties;
import com.example.anthropicproxy.model.openai.OpenAICompletionRequest;
import com.example.anthropicproxy.model.openai.OpenAICompletionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAIClientService {
    private final WebClient openaiWebClient;
    private final ObjectMapper objectMapper;
    private final OpenAIConfigProperties openAIConfig;

    /**
     * Create non-streaming completion
     */
    public Mono<OpenAICompletionResponse> createCompletion(OpenAICompletionRequest request) {
        String endpoint = "/" + openAIConfig.getApiVersion() + "/chat/completions";
        log.debug("Calling OpenAI API: {}", endpoint);

        return openaiWebClient.post()
                .uri(endpoint)
                .bodyValue(request)
                .retrieve()
                .onStatus(statusCode -> statusCode.isError(), response -> {
                    log.error("OpenAI API error: {}", response.statusCode());
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                try {
                                    // Try to parse error response
                                    var errorNode = objectMapper.readTree(errorBody);
                                    log.error("OpenAI error details: {}", errorNode.toPrettyString());
                                } catch (JsonProcessingException e) {
                                    log.error("OpenAI raw error: {}", errorBody);
                                }
                                return Mono.error(new RuntimeException("OpenAI API error: " + response.statusCode()));
                            });
                })
                .bodyToMono(OpenAICompletionResponse.class)
                .doOnSuccess(response -> log.debug("OpenAI response received"))
                .doOnError(error -> log.error("OpenAI API call failed", error));
    }

    /**
     * Create streaming completion (returns Flux of SSE strings)
     */
    public Mono<String> createCompletionStream(OpenAICompletionRequest request) {
        // For now, return empty Mono - will implement later
        return Mono.empty();
    }
}