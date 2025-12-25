package com.example.anthropicproxy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ModelMappingService {
    private final Map<String, String> modelMapping = new HashMap<>();

    public ModelMappingService() {
        // Initialize model mapping (Anthropic -> OpenAI)
        // Claude 3 Haiku -> GPT-3.5 level
        modelMapping.put("claude-3-haiku-20240307", "gpt-3.5-turbo");
        modelMapping.put("claude-3-haiku", "gpt-3.5-turbo");

        // Claude 3 Sonnet -> GPT-4 level
        modelMapping.put("claude-3-sonnet-20240229", "gpt-4.1");
        modelMapping.put("claude-3-sonnet", "gpt-4.1");

        // Claude 3 Opus -> GPT-4o level
        modelMapping.put("claude-3-opus-20240229", "gpt-4o");
        modelMapping.put("claude-3-opus", "gpt-4o");

        // Claude 4 Sonnet -> GPT-5 level
        modelMapping.put("claude-4.5-sonnet-20251229", "gpt-5");
        modelMapping.put("claude-4.5-sonnet", "gpt-5");

        // Claude 2 series
        modelMapping.put("claude-2.1", "gpt-4.1");
        modelMapping.put("claude-2.0", "gpt-4.1");

        // Claude Instant
        modelMapping.put("claude-instant-1.2", "gpt-3.5-turbo");

        // Default mapping
        modelMapping.put("default", "gpt-4.1");
    }

    public String mapModel(String anthropicModel) {
        // Exact match
        if (modelMapping.containsKey(anthropicModel)) {
            return modelMapping.get(anthropicModel);
        }

        // Fuzzy match
        for (Map.Entry<String, String> entry : modelMapping.entrySet()) {
            if (anthropicModel.toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        // Default mapping
        log.warn("No mapping found for model '{}', using default", anthropicModel);
        return modelMapping.get("default");
    }
}