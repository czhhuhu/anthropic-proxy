package com.example.anthropicproxy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAIConfigProperties {
    private String apiKey;
    private String baseUrl = "https://api.openai.com";
    private String apiVersion = "v1";
    private Long timeout = 60L;
}