package xyz.yanp.util.wiki;

import com.alibaba.fastjson.JSON;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptUtils {

    private static final String PROMPT_BASE_PATH = "static/prompt/";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private PromptUtils() {
    }

    public static String loadPrompt(String promptFileName, Map<String, Object> variables) {
        if (promptFileName == null || promptFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("prompt文件名不能为空");
        }

        String resourcePath = PROMPT_BASE_PATH + promptFileName;
        String template = readClasspathText(resourcePath, promptFileName);
        StringBuilder rendered = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        int lastEnd = 0;
        while (matcher.find()) {
            rendered.append(template, lastEnd, matcher.start());
            String variableName = matcher.group(1);
            if (variables == null || !variables.containsKey(variableName)) {
                throw new IllegalArgumentException("prompt模板变量未提供: " + variableName + " in " + promptFileName);
            }
            rendered.append(asPromptText(variables.get(variableName)));
            lastEnd = matcher.end();
        }
        rendered.append(template, lastEnd, template.length());
        return rendered.toString();
    }

    private static String readClasspathText(String resourcePath, String promptFileName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader == null ? null : classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("缺失prompt文件: " + promptFileName);
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("读取prompt文件失败: " + promptFileName, e);
        }
    }

    private static String asPromptText(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return (String) value;
        }
        return JSON.toJSONString(value);
    }
}
