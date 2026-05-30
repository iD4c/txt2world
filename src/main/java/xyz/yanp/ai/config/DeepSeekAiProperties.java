package xyz.yanp.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.deepseek")
public class DeepSeekAiProperties {

    private String baseUrl = "https://api.deepseek.com";

    private String model = "deepseek-chat";

    private String apiKey;
}
