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
public class OpenAIMessage {
    @JsonProperty("role")
    private OpenAIRole role;

    @JsonProperty("content")
    private Object content; // Can be String or List<Map<String, Object>>

    @JsonProperty("name")
    private String name;

    @JsonProperty("tool_calls")
    private List<Map<String, Object>> toolCalls;

    @JsonProperty("tool_call_id")
    private String toolCallId;
}