package com.example.anthropicproxy.model.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicCompletionRequest {
    @JsonProperty("model")
    private String model;

    @JsonProperty("messages")
    private List<AnthropicMessage> messages;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("stream")
    private Boolean stream;

    @JsonProperty("stop_sequences")
    private List<String> stopSequences;

    @JsonProperty("system")
    private String system;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("thinking")
    private Map<String, Object> thinking;
}