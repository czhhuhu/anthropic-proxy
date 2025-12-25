package com.example.anthropicproxy.service;

import com.example.anthropicproxy.config.OpenAIConfigProperties;
import com.example.anthropicproxy.model.openai.OpenAICompletionRequest;
import com.example.anthropicproxy.model.openai.OpenAICompletionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
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
    public Flux<String> createCompletionStream(OpenAICompletionRequest request) {
        String endpoint = "/" + openAIConfig.getApiVersion() + "/chat/completions";
        log.debug("Calling OpenAI API with streaming: {}", endpoint);

        // Ensure streaming is enabled
        request.setStream(true);

        return openaiWebClient.post()
                .uri(endpoint)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .onStatus(statusCode -> statusCode.isError(), response -> {
                    log.error("OpenAI API streaming error: {}", response.statusCode());
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                try {
                                    var errorNode = objectMapper.readTree(errorBody);
                                    log.error("OpenAI streaming error details: {}", errorNode.toPrettyString());
                                } catch (JsonProcessingException e) {
                                    log.error("OpenAI raw streaming error: {}", errorBody);
                                }
                                return Mono.error(new RuntimeException("OpenAI API streaming error: " + response.statusCode()));
                            });
                })
                .bodyToFlux(String.class)
                .doOnNext(data -> log.trace("Received streaming data: {}", data))
                .doOnComplete(() -> log.debug("OpenAI streaming completed"))
                .doOnError(error -> log.error("OpenAI streaming API call failed", error));
    }
}