package xyz.yanp.util.wiki;

import com.alibaba.fastjson.JSON;
import xyz.yanp.vo.wiki.AiOutInfoDraft;
import xyz.yanp.vo.wiki.WorldObj;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static xyz.yanp.util.wiki.Txt2worldUtils.defaultList;
import static xyz.yanp.util.wiki.Txt2worldUtils.defaultText;
import static xyz.yanp.util.wiki.Txt2worldUtils.isSupportedWikiSectionType;
import static xyz.yanp.util.wiki.Txt2worldUtils.normalizeWikiSectionType;
import static xyz.yanp.util.wiki.Txt2worldUtils.writeJson;

public class OrganizeUtils {

    private static final String DRAFT_ALIGNED_FILE = "AiOutInfoDraft_aligned_list.json";
    private static final String DRAFT_FILE = "AiOutInfoDraft_list.json";
    private static final String MERGED_CHARACTERS_FILE = "MergedWikiData_characters.json";
    private static final String MERGED_FACTIONS_FILE = "MergedWikiData_factions.json";
    private static final String MERGED_ITEMS_FILE = "MergedWikiData_items.json";

    public static void main(String[] args) throws Exception {
        Path target = args != null && args.length > 0
            ? Paths.get(args[0])
            : Paths.get(System.getProperty("user.dir"), "wiki-files");

        if (!Files.exists(target)) {
            System.out.println("target path does not exist: " + target.toAbsolutePath());
            return;
        }

        List<Path> tablesDirs = resolveTablesDirs(target);
        if (tablesDirs.isEmpty()) {
            System.out.println("no tables dir found: " + target.toAbsolutePath());
            return;
        }

        for (Path tablesDir : tablesDirs) {
            organizeTablesDir(tablesDir);
            System.out.println("organized tables dir: " + tablesDir.toAbsolutePath());
        }
    }

    private static List<Path> resolveTablesDirs(Path target) throws Exception {
        List<Path> tablesDirs = new ArrayList<>();
        if (Files.isRegularFile(target)) {
            return tablesDirs;
        }

        if ("tables".equals(target.getFileName().toString())) {
            tablesDirs.add(target);
            return tablesDirs;
        }

        Path nestedTablesDir = target.resolve("tables");
        if (Files.isDirectory(nestedTablesDir)) {
            tablesDirs.add(nestedTablesDir);
            return tablesDirs;
        }

        try (java.util.stream.Stream<Path> paths = Files.list(target)) {
            paths.filter(Files::isDirectory)
                .map(path -> path.resolve("tables"))
                .filter(Files::isDirectory)
                .forEach(tablesDirs::add);
        }
        return tablesDirs;
    }

    private static void organizeTablesDir(Path tablesDir) throws Exception {
        organizeDraftFile(tablesDir.resolve(DRAFT_ALIGNED_FILE));
        organizeDraftFile(tablesDir.resolve(DRAFT_FILE));
        organizeMergedWikiDataFiles(tablesDir);
    }

    private static void organizeDraftFile(Path file) throws Exception {
        if (!Files.isRegularFile(file)) {
            return;
        }

        List<AiOutInfoDraft> drafts = JSON.parseArray(readText(file), AiOutInfoDraft.class);
        for (AiOutInfoDraft draft : defaultList(drafts)) {
            if (draft == null) {
                continue;
            }
            Map<String, List<WorldObj>> objectsBySection = buildWorldObjSectionMap();
            addWorldObjsBySection(objectsBySection, draft.getCharacters(), "人物");
            addWorldObjsBySection(objectsBySection, draft.getFactions(), "势力");
            addWorldObjsBySection(objectsBySection, draft.getItems(), "物品");
            draft.setCharacters(objectsBySection.get("人物"));
            draft.setFactions(objectsBySection.get("势力"));
            draft.setItems(objectsBySection.get("物品"));
        }
        writeJson(file, drafts);
    }

    private static void organizeMergedWikiDataFiles(Path tablesDir) throws Exception {
        Map<String, List<WorldObj>> objectsBySection = buildWorldObjSectionMap();
        addWorldObjsBySection(objectsBySection, readWorldObjList(tablesDir.resolve(MERGED_CHARACTERS_FILE)), "人物");
        addWorldObjsBySection(objectsBySection, readWorldObjList(tablesDir.resolve(MERGED_FACTIONS_FILE)), "势力");
        addWorldObjsBySection(objectsBySection, readWorldObjList(tablesDir.resolve(MERGED_ITEMS_FILE)), "物品");
        writeJson(tablesDir.resolve(MERGED_CHARACTERS_FILE), objectsBySection.get("人物"));
        writeJson(tablesDir.resolve(MERGED_FACTIONS_FILE), objectsBySection.get("势力"));
        writeJson(tablesDir.resolve(MERGED_ITEMS_FILE), objectsBySection.get("物品"));
    }

    private static Map<String, List<WorldObj>> buildWorldObjSectionMap() {
        Map<String, List<WorldObj>> objectsBySection = new HashMap<>();
        objectsBySection.put("人物", new ArrayList<>());
        objectsBySection.put("势力", new ArrayList<>());
        objectsBySection.put("物品", new ArrayList<>());
        return objectsBySection;
    }

    private static void addWorldObjsBySection(Map<String, List<WorldObj>> objectsBySection, List<WorldObj> worldObjs, String fallbackSectionType) {
        for (WorldObj worldObj : defaultList(worldObjs)) {
            if (worldObj == null) {
                continue;
            }
            String sectionType = resolveSectionType(worldObj, fallbackSectionType);
            worldObj.setWikiSectionType(sectionType);
            objectsBySection.get(sectionType).add(worldObj);
        }
    }

    private static String resolveSectionType(WorldObj worldObj, String fallbackSectionType) {
        String rawSectionType = defaultText(worldObj.getWikiSectionType(), "").trim();
        if (isSupportedWikiSectionType(rawSectionType)) {
            return normalizeWikiSectionType(rawSectionType);
        }
        return fallbackSectionType;
    }

    private static List<WorldObj> readWorldObjList(Path file) throws Exception {
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        return JSON.parseArray(readText(file), WorldObj.class);
    }

    private static String readText(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
