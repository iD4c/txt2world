package xyz.yanp.util.wiki;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.ObjectUtils;
import xyz.yanp.vo.wiki.Chapter;
import xyz.yanp.vo.wiki.ChapterChunk;
import xyz.yanp.vo.wiki.WorldObjAlias;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class Txt2worldUtils {

    public static final Set<String> RELATION_ATTITUDES = new HashSet<>(Arrays.asList(
        "敌对", "欺骗", "利用", "对峙", "怀疑", "中立", "合作", "友好", "无"
    ));
    public static final Set<String> RELATION_HIERARCHIES = new HashSet<>(Arrays.asList(
        "上级", "下级", "同级", "主人", "奴仆", "无"
    ));
    public static final Set<String> RELATION_FAVORABILITIES = new HashSet<>(Arrays.asList(
        "厌恶", "冷淡", "无感", "信任", "喜欢", "爱慕", "无"
    ));
    public static final Set<String> RELATION_BONDS = new HashSet<>(Arrays.asList(
        "父", "母", "子", "女", "夫妻", "恋人", "前任", "兄", "弟", "姐", "妹", "师", "徒", "同门", "亲戚", "朋友",
        "同学", "队友", "搭档", "伙伴", "盟友", "无"
    ));
    public static final Set<String> RELATION_NARRATIVE_ROLES = new HashSet<>(Arrays.asList(
        "重要信息来源", "信息接收者", "重要线索来源", "线索接收者", "委托者", "受托者", "调查者", "被调查者",
        "施助人", "受助人", "控制者", "被控制者", "寄生者", "被寄生者", "附身者", "被附身者",
        "依附者", "被依附者", "保护者", "被保护者", "监视者", "被监视者", "教导者", "被教导者", "无"
    ));

    private static final Set<String> GENERIC_ALIAS_BLACKLIST = new HashSet<>(Arrays.asList(
        "先生", "女士", "小姐", "夫人", "老师", "队长", "老板", "老人", "男人", "女人",
        "少年", "少女", "神灵", "怪物", "敌人", "同伴"
    ));

    private Txt2worldUtils() {
    }

    public static String stripBom(String text) {
        if (text != null && text.startsWith("\uFEFF")) {
            return text.substring(1);
        }
        return text;
    }

    public static String stripQuote(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
            .replaceAll("^“", "")
            .replaceAll("”$", "")
            .replaceAll("^\"", "")
            .replaceAll("\"$", "");
    }

    public static String defaultText(String value, String defaultValue) {
        return ObjectUtils.isEmpty(value) ? defaultValue : value;
    }

    public static int defaultNumber(Integer value) {
        return value == null ? 0 : value;
    }

    public static <T> List<T> defaultList(Collection<T> values) {
        return values == null ? Collections.emptyList() : new ArrayList<>(values);
    }

    public static String normalizeWikiSectionType(String sectionType) {
        if ("势力".equals(sectionType) || "物品".equals(sectionType)) {
            return sectionType;
        }
        return "人物";
    }

    public static boolean isSupportedWikiSectionType(String sectionType) {
        return "人物".equals(sectionType)
            || "势力".equals(sectionType)
            || "物品".equals(sectionType);
    }

    public static String normalizeImportanceLevel(String importanceLevel) {
        if ("CORE".equals(importanceLevel)
            || "HIGH".equals(importanceLevel)
            || "MEDIUM".equals(importanceLevel)
            || "LOW".equals(importanceLevel)) {
            return importanceLevel;
        }
        return "TEMP";
    }

    public static String higherImportanceLevel(String a, String b) {
        return importanceRank(a) <= importanceRank(b) ? normalizeImportanceLevel(a) : normalizeImportanceLevel(b);
    }

    public static int importanceRank(String importanceLevel) {
        String normalized = normalizeImportanceLevel(importanceLevel);
        if ("CORE".equals(normalized)) {
            return 0;
        }
        if ("HIGH".equals(normalized)) {
            return 1;
        }
        if ("MEDIUM".equals(normalized)) {
            return 2;
        }
        if ("LOW".equals(normalized)) {
            return 3;
        }
        return 4;
    }

    public static String normalizeEnumValue(String value, Set<String> supportedValues) {
        String normalized = defaultText(value, "").trim();
        return supportedValues.contains(normalized) ? normalized : "无";
    }

    public static boolean isMergeableAlias(String alias) {
        return !ObjectUtils.isEmpty(alias)
            && alias.trim().length() >= 2
            && !GENERIC_ALIAS_BLACKLIST.contains(alias.trim());
    }

    public static Set<String> collectMergeAliasTexts(List<WorldObjAlias> aliases) {
        Set<String> aliasTexts = new HashSet<>();
        for (WorldObjAlias alias : defaultList(aliases)) {
            if (alias == null || ObjectUtils.isEmpty(alias.getAlias())) {
                continue;
            }
            String aliasText = alias.getAlias().trim();
            if (isMergeableAlias(aliasText)) {
                aliasTexts.add(aliasText);
            }
        }
        return aliasTexts;
    }

    public static String joinText(String left, String right) {
        String safeLeft = defaultText(left, "");
        String safeRight = defaultText(right, "");
        if (safeLeft.isEmpty()) {
            return safeRight;
        }
        if (safeRight.isEmpty() || safeLeft.contains(safeRight)) {
            return safeLeft;
        }
        return safeLeft + "\n" + safeRight;
    }

    public static String fallbackCompactMemorySummary(List<String> itemsToCompact) {
        StringBuilder sb = new StringBuilder();
        for (String item : defaultList(itemsToCompact)) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(item);
        }
        return limitMemorySummary(sb.toString());
    }

    public static String limitMemorySummary(String value) {
        String text = defaultText(value, "").trim();
        if (text.length() <= 2000) {
            return text;
        }
        return text.substring(0, 2000);
    }

    public static String limitChapterSummary(String value) {
        String text = defaultText(value, "").trim();
        if (text.length() <= 300) {
            return text;
        }
        return text.substring(0, 300);
    }

    public static String limitRelationSummary(String value) {
        String text = defaultText(value, "").trim();
        if (text.length() <= 300) {
            return text;
        }
        return text.substring(0, 300);
    }

    public static String limitEvidenceText(String value) {
        String text = stripQuote(value);
        if (text.length() <= 200) {
            return text;
        }
        return text.substring(0, 200);
    }

    public static String chunkTitleList(ChapterChunk chunk) {
        StringBuilder sb = new StringBuilder();
        for (Chapter chapter : chunk.getChapters()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(chapter.getTitle());
        }
        return sb.toString();
    }

    public static JSONObject parseJsonObject(String raw) {
        String text = extractJsonText(raw);
        if (text == null) {
            return null;
        }
        try {
            return JSONObject.parseObject(text);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T parseAiObject(String raw, Class<T> clazz) {
        String text = extractJsonText(raw);
        if (text == null) {
            return null;
        }
        try {
            JSONObject jsonObject = JSONObject.parseObject(text);
            if (jsonObject.containsKey("ai_error")) {
                return null;
            }
            return JSON.parseObject(text, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractJsonText(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstBrace = text.indexOf('{');
            int lastBrace = text.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                text = text.substring(firstBrace, lastBrace + 1);
            }
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1);
        }
        return text;
    }

    public static void writeJson(Path path, Object value) throws IOException {
        writeText(path, JSON.toJSONString(value));
    }

    public static void writeText(Path path, String text) throws IOException {
        Files.write(path, defaultText(text, "").getBytes(StandardCharsets.UTF_8));
    }

    public static int estimateInputTokens(String systemPrompt, String userPrompt) {
        return estimateTokens(systemPrompt) + estimateTokens(userPrompt);
    }

    public static int estimateOutputTokens(String answer) {
        return estimateTokens(answer);
    }

    public static int estimateTokens(String text) {
        String safeText = defaultText(text, "");
        if (safeText.isEmpty()) {
            return 0;
        }
        int charCount = safeText.codePointCount(0, safeText.length());
        return (int) Math.ceil(charCount * 0.6D);
    }
}
