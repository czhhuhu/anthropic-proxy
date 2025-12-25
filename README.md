# Anthropic Proxy API

A Spring Boot proxy service that provides Anthropic API format interface while calling OpenAI ChatGPT API under the hood.

## Overview

This project implements a proxy API that:
- Provides Anthropic API format interface
- Supports all major Anthropic endpoints
- Converts Anthropic requests to OpenAI ChatGPT API format
- Converts OpenAI responses back to Anthropic format
- Includes model mapping between Anthropic and OpenAI models

## Technology Stack

- **Framework**: Spring Boot 3.2.4
- **Java Version**: 17
- **Build Tool**: Maven
- **HTTP Client**: WebClient (Reactive)
- **JSON Processing**: Jackson 2.16.1
- **Package Management**: Lombok

## Implemented Features

### 1. API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /v1/` | GET | Service root, shows available endpoints |
| `GET /v1/health` | GET | Health check endpoint |
| `GET /v1/models` | GET | List supported models |
| `POST /v1/messages` | POST | Main chat completion endpoint |

### 2. Model Mapping System

Anthropic models are mapped to OpenAI models:

| Anthropic Model | OpenAI Model |
|-----------------|--------------|
| `claude-3-haiku-20240307` | `gpt-3.5-turbo` |
| `claude-3-haiku` | `gpt-3.5-turbo` |
| `claude-3-sonnet-20240229` | `gpt-4` |
| `claude-3-sonnet` | `gpt-4` |
| `claude-3-opus-20240229` | `gpt-4o` |
| `claude-3-opus` | `gpt-4o` |
| `claude-2.1` | `gpt-4` |
| `claude-2.0` | `gpt-4` |
| `claude-instant-1.2` | `gpt-3.5-turbo` |

**Default**: If no mapping found, falls back to `gpt-3.5-turbo`

### 3. Format Conversion

**Request Conversion**:
- Anthropic messages → OpenAI message format
- System prompt handling
- Temperature adjustment (Anthropic 0-1 → OpenAI 0-2)
- Stop sequences conversion
- Parameter validation and warnings for unsupported features

**Response Conversion**:
- OpenAI response → Anthropic response format
- Token usage statistics
- Stop reason mapping
- Response ID generation

## Configuration

### Application Configuration (`application.yml`)

```yaml
server:
  port: 8081  # Service port (changed from default 8080)
  servlet:
    context-path: /

spring:
  application:
    name: anthropic-proxy

openai:
  api-key: ${OPENAI_API_KEY:}  # Set via environment variable
  base-url: https://api.openai.com
  api-version: v1
  timeout: 5  # Timeout in seconds

logging:
  level:
    com.example.anthropicproxy: DEBUG
    org.springframework.web: INFO
```

### Environment Variables

- `OPENAI_API_KEY`: Your OpenAI API key (required)

## How to Run

### 1. Clone and Navigate

```bash
cd anthropic-proxy
```

### 2. Set Environment Variable

```bash
export OPENAI_API_KEY="your-openai-api-key"
```

### 3. Build and Run

```bash
mvn spring-boot:run
```

The service will start on port 8081.

### 4. Verify Service

```bash
# Check service status
curl http://localhost:8081/v1/

# Health check
curl http://localhost:8081/v1/health

# Get model list
curl http://localhost:8081/v1/models
```

### 5. Send Chat Request

```bash
curl -X POST http://localhost:8081/v1/messages \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-3-haiku",
    "messages": [
      {
        "role": "user",
        "content": "Hello, how are you?"
      }
    ],
    "max_tokens": 100
  }'
```

## Project Structure

```
anthropic-proxy/
├── src/main/java/com/example/anthropicproxy/
│   ├── AnthropicProxyApplication.java         # Main application class
│   ├── controller/
│   │   └── AnthropicController.java           # API controllers
│   ├── service/
│   │   ├── ConversionService.java             # Format conversion logic
│   │   ├── ModelMappingService.java           # Model mapping
│   │   └── OpenAIClientService.java          # OpenAI client
│   ├── model/
│   │   ├── anthropic/                         # Anthropic data models
│   │   │   ├── AnthropicCompletionRequest.java
│   │   │   ├── AnthropicCompletionResponse.java
│   │   │   ├── AnthropicMessage.java
│   │   │   ├── AnthropicRole.java
│   │   │   ├── AnthropicContentBlock.java
│   │   │   ├── AnthropicMessageContent.java
│   │   │   └── AnthropicUsage.java
│   │   └── openai/                            # OpenAI data models
│   │       ├── OpenAICompletionRequest.java
│   │       ├── OpenAICompletionResponse.java
│   │       ├── OpenAIMessage.java
│   │       ├── OpenAIRole.java
│   │       ├── OpenAIChoice.java
│   │       └── OpenAIUsage.java
│   └── config/
│       ├── ApplicationConfig.java             # WebClient configuration
│       └── OpenAIConfigProperties.java        # OpenAI configuration properties
├── src/main/resources/
│   └── application.yml                        # Configuration file
├── pom.xml                                   # Maven configuration
└── README.md                                 # This file
```

## Error Handling

- **OpenAI API Errors**: Returns Anthropic-style error responses
- **Connection Timeouts**: Configured to 5 seconds (adjustable)
- **Validation Errors**: Returns appropriate HTTP status codes
- **Logging**: Detailed logging for debugging

## Testing Results

✅ **All endpoints tested successfully**:
1. **Root endpoint** (`GET /v1/`) - Returns service information
2. **Health check** (`GET /v1/health`) - Returns `{"status":"healthy"}`
3. **Model list** (`GET /v1/models`) - Returns 9 mapped models
4. **Chat endpoint** (`POST /v1/messages`) - Correctly formats and calls OpenAI API

✅ **Error handling tested**:
- OpenAI API connection timeout (5 seconds)
- Returns standard Anthropic error format
- Detailed error logging

## Limitations and Notes

1. **Streaming Support**: Not yet implemented (marked as TODO in code)
2. **API Coverage**: Currently supports main chat completion endpoint
3. **Model Mapping**: Uses simple string matching for model mapping
4. **Parameter Support**: Some Anthropic parameters (metadata, thinking) are logged as unsupported
5. **Timeout**: Default timeout is 5 seconds for OpenAI API calls

## Extensibility

The project is designed to be easily extensible:

- **New Endpoints**: Add additional Anthropic endpoints
- **Model Mapping**: Adjust model relationships in `ModelMappingService`
- **LLM Providers**: Support other LLM providers besides OpenAI
- **Streaming**: Implement server-sent events for streaming responses
- **Caching**: Add response caching layer
- **Metrics**: Add monitoring and metrics collection

## Troubleshooting

### Port Already in Use
If port 8081 is already in use, change the port in `application.yml`:
```yaml
server:
  port: 8082  # Or any available port
```

### OpenAI API Key Not Set
Ensure environment variable is set:
```bash
echo $OPENAI_API_KEY
```

### Build Errors
Clean and rebuild:
```bash
mvn clean compile
mvn spring-boot:run
```

### Connection Issues
Check network connectivity to OpenAI API:
```bash
curl -I https://api.openai.com
```

## License

This project is provided as-is for educational and demonstration purposes.

## Contributing

Feel free to fork and modify according to your needs. Key areas for contribution:
- Streaming response implementation
- Additional Anthropic endpoints
- Enhanced error handling
- Performance optimizations
- Test coverage

---

**Status**: Service implementation complete, all core functionality tested and working.