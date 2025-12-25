package com.example.anthropicproxy.model.openai;

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
public class OpenAIStreamChunk {
    @JsonProperty("id")
    private String id;

    @JsonProperty("object")
    private String object;

    @JsonProperty("created")
    private Long created;

    @JsonProperty("model")
    private String model;

    @JsonProperty("choices")
    private List<OpenAIStreamChoice> choices;

    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpenAIStreamChoice {
        @JsonProperty("index")
        private Integer index;

        @JsonProperty("delta")
        private OpenAIDelta delta;

        @JsonProperty("finish_reason")
        private String finishReason;

        @JsonProperty("logprobs")
        private Map<String, Object> logprobs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpenAIDelta {
        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private String content;

        @JsonProperty("tool_calls")
        private List<Map<String, Object>> toolCalls;
    }
}