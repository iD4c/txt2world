package xyz.yanp.ai.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import xyz.yanp.ai.config.DeepSeekAiProperties;

@Slf4j
@Component
public class DeepSeekAiClient implements AiClient {

    private static final int DEFAULT_JSON_MAX_TOKENS = 16000;
    private static final int RETRY_JSON_MAX_TOKENS = 28000;
    private static final long[] RATE_LIMIT_RETRY_DELAYS_MS = {4000L, 16000L, 64000L};

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DeepSeekAiProperties deepSeekAiProperties;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chatInternal(systemPrompt, userPrompt, false);
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        return chatJson(systemPrompt, userPrompt, DEFAULT_JSON_MAX_TOKENS);
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt, int maxTokens) {
        try {
            return chatInternal(systemPrompt, userPrompt, true, maxTokens);
        } catch (RetryableJsonResponseException e) {
            if (maxTokens >= RETRY_JSON_MAX_TOKENS) {
                if (e.getContent() != null && !e.getContent().trim().isEmpty()) {
                    return e.getContent();
                }
                throw e;
            }
            log.warn("deepseek json response retryable failure, retry with max_tokens={}, reason={}", RETRY_JSON_MAX_TOKENS, e.getMessage());
            try {
                return chatInternal(systemPrompt, userPrompt, true, RETRY_JSON_MAX_TOKENS);
            } catch (RetryableJsonResponseException retryException) {
                if (retryException.getContent() != null && !retryException.getContent().trim().isEmpty()) {
                    return retryException.getContent();
                }
                throw retryException;
            }
        }
    }

    private String chatInternal(String systemPrompt, String userPrompt, boolean jsonMode) {
        return chatInternal(systemPrompt, userPrompt, jsonMode, 0);
    }

    private String chatInternal(String systemPrompt, String userPrompt, boolean jsonMode, int maxTokens) {
        String apiKey = deepSeekAiProperties.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("DeepSeek API key is missing. Please set ai.deepseek.api-key or DEEPSEEK_API_KEY.");
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", deepSeekAiProperties.getModel());
        requestBody.put("stream", false);
        requestBody.put("thinking", new JSONObject().fluentPut("type", "disabled"));
        if (jsonMode) {
            requestBody.put("response_format", new JSONObject().fluentPut("type", "json_object"));
        }
        if (maxTokens > 0) {
            requestBody.put("max_tokens", maxTokens);
        }

        JSONArray messages = new JSONArray();
        messages.add(buildMessage("system", systemPrompt));
        messages.add(buildMessage("user", userPrompt));
        requestBody.put("messages", messages);

        log.info("deepseek request model={}, baseUrl={}, jsonMode={}, maxTokens={}",
                deepSeekAiProperties.getModel(), deepSeekAiProperties.getBaseUrl(), jsonMode, maxTokens);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());

        String baseUrl = deepSeekAiProperties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String responseBody = postForObjectWithRateLimitRetry(
            baseUrl + "/chat/completions",
            new HttpEntity<>(requestBody.toJSONString(), headers),
            String.class
        );

        if (responseBody == null || responseBody.trim().isEmpty()) {
            throw new IllegalStateException("deepseek response is empty");
        }

        JSONObject responseJson = JSONObject.parseObject(responseBody);
        JSONArray choices = responseJson.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("deepseek response missing choices: " + responseBody);
        }

        JSONObject firstChoice = choices.getJSONObject(0);
        String finishReason = firstChoice == null ? null : firstChoice.getString("finish_reason");
        if ("length".equals(finishReason)) {
            throw new RetryableJsonResponseException("deepseek response truncated, finish_reason=length");
        }
        JSONObject messageJson = firstChoice == null ? null : firstChoice.getJSONObject("message");
        String content = messageJson == null ? null : messageJson.getString("content");

        if (content == null || content.trim().isEmpty()) {
            throw new IllegalStateException("deepseek response missing choices[0].message.content: " + responseBody);
        }

        if (jsonMode) {
            validateJsonObject(content);
        }

        return content.trim();
    }

    private void validateJsonObject(String content) {
        try {
            JSONObject json = JSONObject.parseObject(content.trim());
            if (json == null) {
                throw new IllegalArgumentException("response JSON object is null");
            }
        } catch (Exception e) {
            throw new RetryableJsonResponseException("deepseek response is not valid JSON object", e, content);
        }
    }

    private JSONObject buildMessage(String role, String content) {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String postForObjectWithRateLimitRetry(String url, HttpEntity<String> requestEntity, Class<String> responseType) {
        for (int attempt = 0; ; attempt++) {
            try {
                return restTemplate.postForObject(url, requestEntity, responseType);
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= RATE_LIMIT_RETRY_DELAYS_MS.length) {
                    log.error("deepseek request failed after {} rate-limit retries, status=429", RATE_LIMIT_RETRY_DELAYS_MS.length, e);
                    throw e;
                }
                long delayMs = RATE_LIMIT_RETRY_DELAYS_MS[attempt];
                log.warn("deepseek request rate-limited, retry {}/{}, wait {} ms", attempt + 1, RATE_LIMIT_RETRY_DELAYS_MS.length, delayMs, e);
                sleepBeforeRetry(delayMs);
            }
        }
    }

    private void sleepBeforeRetry(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("deepseek rate-limit retry interrupted", e);
        }
    }

    private static class RetryableJsonResponseException extends RuntimeException {
        RetryableJsonResponseException(String message) {
            super(message);
        }

        RetryableJsonResponseException(String message, Throwable cause) {
            super(message, cause);
        }

        RetryableJsonResponseException(String message, Throwable cause, String content) {
            super(message, cause);
            this.content = content;
        }

        private String content;

        String getContent() {
            return content;
        }
    }
}
