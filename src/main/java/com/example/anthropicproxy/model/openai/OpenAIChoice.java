package com.example.anthropicproxy.model.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAIChoice {
    @JsonProperty("index")
    private Integer index;

    @JsonProperty("message")
    private OpenAIMessage message;

    @JsonProperty("finish_reason")
    private String finishReason;

    @JsonProperty("logprobs")
    private Object logprobs;
}