package com.example.anthropicproxy.model.anthropic;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AnthropicRole {
    USER("user"),
    ASSISTANT("assistant");

    private final String value;

    AnthropicRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AnthropicRole fromValue(String value) {
        for (AnthropicRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}