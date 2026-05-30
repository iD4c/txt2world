package xyz.yanp.ai.client;

public interface AiClient {

    String chat(String systemPrompt, String userPrompt);

    default String chatJson(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt);
    }

    default String chatJson(String systemPrompt, String userPrompt, int maxTokens) {
        return chatJson(systemPrompt, userPrompt);
    }
}
