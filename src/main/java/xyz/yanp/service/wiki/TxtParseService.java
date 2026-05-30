package xyz.yanp.service.wiki;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;
import xyz.yanp.ai.client.AiClient;
import xyz.yanp.entity.wiki.WikiProject;
import xyz.yanp.repository.wiki.WikiProjectRepository;
import xyz.yanp.util.SnowFlake;
import xyz.yanp.util.wiki.PromptUtils;
import xyz.yanp.util.wiki.TokenStatisticsAccumulator;
import xyz.yanp.vo.wiki.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PreDestroy;

import static xyz.yanp.util.wiki.Txt2worldUtils.*;

@Slf4j
@Service
public class TxtParseService {

    private static final Pattern NOVEL_TITLE_PATTERN = Pattern.compile("(?m)^\\s*(第[0-9零一二三四五六七八九十百千万两〇]+(章|[部卷])(?:\\s+\\S[^\\r\\n]{0,29})?)\\s*$");
    private static final Charset GB2312_CHARSET = Charset.forName("GB2312");
    private static final int CHUNK_SIZE = 20;
    private static final int RECENT_MEMORY_CHAR_LIMIT = 16000;
    private static final int MID_MEMORY_CHAR_LIMIT = 16000;
    private static final int RECENT_MEMORY_COMPACT_CHAR_LIMIT = 8000;
    private static final int MID_MEMORY_COMPACT_CHAR_LIMIT = 6000;
    private static final int CHUNK_DRAFT_WORKER_THREAD_NUM = 4;
    private static final int CHARACTER_RELATION_WORKER_THREAD_NUM = 12;
    private static final Set<String> LIFE_STATUSES = new HashSet<>();
    private static final String COMMON_SYSTEM_PROMPT = PromptUtils.loadPrompt("common_system_prompt.txt", Collections.emptyMap());

    static {
        LIFE_STATUSES.add("活着");
        LIFE_STATUSES.add("已死亡");
        LIFE_STATUSES.add("被封印");
        LIFE_STATUSES.add("被囚禁");
        LIFE_STATUSES.add("沉睡中");
        LIFE_STATUSES.add("昏迷中");
        LIFE_STATUSES.add("灵体状态");
    }

    @Autowired
    private AiClient aiClient;

    @Autowired
    private WikiProjectRepository wikiProjectRepository;

    private final ExecutorService parseExecutor = Executors.newSingleThreadExecutor();

    public JSONObject uploadAndParseAsync(MultipartFile file, String projectName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("txt文件不能为空");
        }
        if (ObjectUtils.isEmpty(projectName)) {
            throw new IllegalArgumentException("wiki项目名不能为空");
        }

        byte[] txtBytes = file.getBytes();
        Charset txtCharset = detectSupportedTxtCharset(txtBytes);
        String novelText = stripBom(new String(txtBytes, txtCharset));
        List<Chapter> allChapters = splitChapters(novelText);
        if (allChapters.isEmpty()) {
            throw new IllegalArgumentException("txt文本内容不能为空");
        }

        WikiProject wikiProject = buildWikiProject(projectName, allChapters.size());
        WikiProject savedWikiProject = wikiProjectRepository.save(wikiProject);

        parseExecutor.submit(() -> runCoreFlow(savedWikiProject, novelText, allChapters, false));
        return buildRunResult(resolveProjectDir(savedWikiProject), savedWikiProject, allChapters.size());
    }

    private Charset detectSupportedTxtCharset(byte[] bytes) {
        if (hasUnsupportedBom(bytes) || containsNulByte(bytes)) {
            throw new IllegalArgumentException("不支持该文件编码，仅支持UTF-8和GB2312");
        }
        if (isDecodable(bytes, StandardCharsets.UTF_8)) {
            return StandardCharsets.UTF_8;
        }
        if (isDecodable(bytes, GB2312_CHARSET)) {
            return GB2312_CHARSET;
        }
        throw new IllegalArgumentException("不支持该文件编码，仅支持UTF-8和GB2312");
    }

    private boolean hasUnsupportedBom(byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            return false;
        }
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return (first == 0xFF && second == 0xFE) || (first == 0xFE && second == 0xFF);
    }

    private boolean containsNulByte(byte[] bytes) {
        for (byte item : bytes) {
            if (item == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isDecodable(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    public JSONObject reparseAsync(String wikiProjectId) throws IOException {
        if (ObjectUtils.isEmpty(wikiProjectId)) {
            throw new IllegalArgumentException("wiki项目id不能为空");
        }
        Optional<WikiProject> optional = wikiProjectRepository.findById(wikiProjectId);
        if (!optional.isPresent()) {
            throw new IllegalArgumentException("wiki项目不存在");
        }
        WikiProject wikiProject = optional.get();
        if ("解析中".equals(wikiProject.getParseStatus())) {
            throw new IllegalArgumentException("解析中的wiki项目不能再次解析");
        }

        Path projectDir = resolveProjectDir(wikiProject);
        Path originalPath = projectDir.resolve("original.txt");
        if (!Files.isRegularFile(originalPath) || Files.size(originalPath) <= 0) {
            throw new IllegalArgumentException("wiki项目原文文件不存在");
        }

        String novelText = stripBom(new String(Files.readAllBytes(originalPath), StandardCharsets.UTF_8));
        List<Chapter> allChapters = splitChapters(novelText);
        if (allChapters.isEmpty()) {
            throw new IllegalArgumentException("txt文本内容不能为空");
        }

        wikiProject.setChunkSize(CHUNK_SIZE);
        wikiProject.setChapterNum(allChapters.size());
        wikiProject.setParseStatus("解析中");
        WikiProject savedWikiProject = wikiProjectRepository.save(wikiProject);

        parseExecutor.submit(() -> runCoreFlow(savedWikiProject, novelText, allChapters, true));
        return buildRunResult(projectDir, savedWikiProject, allChapters.size());
    }

    private void runCoreFlow(WikiProject wikiProject, String novelText, List<Chapter> chapters, boolean reuseAiLog) {
        Path projectDir = resolveProjectDir(wikiProject);
        Path aiLogDir = projectDir.resolve("ai-log");
        Path tablesDir = projectDir.resolve("tables");
        TokenStatisticsAccumulator tokenStatisticsAccumulator = new TokenStatisticsAccumulator();
        try {
            Files.createDirectories(aiLogDir);
            Files.createDirectories(tablesDir);
            writeText(projectDir.resolve("original.txt"), novelText);

            List<ChapterChunk> chunks = buildChunks(chapters);
            List<ChunkSummary> chunkSummary_list = new ArrayList<>();
            List<ChunkReadingMemory> chunkReadingMemory_list = new ArrayList<>();
            List<AiOutInfoDraft> aiOutInfoDraft_list = new ArrayList<>();

            runChunkPlotAndDraftPipeline(aiLogDir, chunks, chunkSummary_list, chunkReadingMemory_list, aiOutInfoDraft_list, tokenStatisticsAccumulator, reuseAiLog);
            writeJson(tablesDir.resolve("chunkSummary_list.json"), chunkSummary_list);
            writeJson(tablesDir.resolve("chunkReadingMemory_list.json"), chunkReadingMemory_list);
            writeJson(tablesDir.resolve("AiOutInfoDraft_list.json"), aiOutInfoDraft_list);

            MergedWikiData mergedWiki = mergeChunkDraftsByJava(aiOutInfoDraft_list);

            AiOutInfoMergeReviewResult entityMergeReview = runWikiEntityMergeReviewWorker(aiLogDir, mergedWiki, tokenStatisticsAccumulator, reuseAiLog);
            writeJson(tablesDir.resolve("AiOutInfoMergeReviewResult.json"), entityMergeReview);
            writeJson(tablesDir.resolve("MergedWikiData_characters.json"), defaultList(mergedWiki.getCharacters()));
            writeJson(tablesDir.resolve("MergedWikiData_factions.json"), defaultList(mergedWiki.getFactions()));
            writeJson(tablesDir.resolve("MergedWikiData_items.json"), defaultList(mergedWiki.getItems()));

            List<AiOutInfoDraft> chunkWorldObjAlign_list = chunkWorldObjAlign(aiOutInfoDraft_list, mergedWiki);
            writeJson(tablesDir.resolve("AiOutInfoDraft_aligned_list.json"), chunkWorldObjAlign_list);

            List<AiOutInfoRelationExtract> aiOutInfoRelationExtract_list = runCharacterRelationExtractWorkers(
                aiLogDir,
                chunkSummary_list,
                chunkReadingMemory_list,
                chunkWorldObjAlign_list,
                tokenStatisticsAccumulator,
                reuseAiLog
            );
            writeJson(tablesDir.resolve("AiOutInfoRelationExtract_list.json"), aiOutInfoRelationExtract_list);
            writeJson(aiLogDir.resolve("token_statistics.json"), tokenStatisticsAccumulator.build());

            wikiProject.setParseStatus("已解析");
            wikiProjectRepository.save(wikiProject);
        } catch (Exception e) {
            log.error("txt2world parse failed, wikiProjectId={}, projectName={}", wikiProject.getId(), wikiProject.getName(), e);
            wikiProject.setParseStatus("解析失败");
            try {
                wikiProjectRepository.save(wikiProject);
            } catch (Exception saveException) {
                log.error("txt2world parse failed and wikiProject status update failed, wikiProjectId={}", wikiProject.getId(), saveException);
            }
        }
    }

    @PreDestroy
    public void shutdownParseExecutor() {
        parseExecutor.shutdown();
    }

    private WikiProject buildWikiProject(String projectName, int totalChapterNum) {
        WikiProject wikiProject = new WikiProject();
        wikiProject.setId(SnowFlake.getBean().nextId());
        wikiProject.setName(projectName);
        wikiProject.setChunkSize(CHUNK_SIZE);
        wikiProject.setChapterNum(totalChapterNum);
        wikiProject.setParseStatus("解析中");
        return wikiProject;
    }

    private Path resolveProjectDir(WikiProject wikiProject) {
        return Paths.get(System.getProperty("user.dir"), "wiki-files", "wiki-" + wikiProject.getId());
    }

    private void runChunkPlotAndDraftPipeline(Path aiLogDir,
                                              List<ChapterChunk> chunks,
                                              List<ChunkSummary> chunkSummary_list,
                                              List<ChunkReadingMemory> chunkReadingMemory_list,
                                              List<AiOutInfoDraft> aiOutInfoDraft_list,
                                              TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                              boolean reuseAiLog) throws IOException {
        ExecutorService draftExecutor = Executors.newFixedThreadPool(CHUNK_DRAFT_WORKER_THREAD_NUM);
        List<Future<AiOutInfoDraft>> draftFutures = new ArrayList<>();
        ChunkReadingMemory readingMemory = buildEmptyReadingMemory();
        try {
            for (int i = 0; i < chunks.size(); i++) {
                ChapterChunk chunk = chunks.get(i);
                AiContextPlot aiContextPlot = buildAiContextPlot(chunk.getIndex(), readingMemory);
                String prompt = buildChunkPlotPrompt(chunk, aiContextPlot);
                String answer = askAiWithLog(aiLogDir, chunk.getIndex(), "chunk-" + chunk.getIndex() + "-plot", "chunk-plot", prompt, tokenStatisticsAccumulator, reuseAiLog);

                AiOutInfoPlot parsed = parseAiObject(answer, AiOutInfoPlot.class);
                ChunkSummary normalizedSummary = isEmptyPlotOutput(parsed) ? fallbackChunkPlot(chunk) : normalizeChunkSummary(chunk, parsed);
                chunkSummary_list.add(normalizedSummary);
                String chunkMemorySummary = limitMemorySummary(normalizedSummary.getChunkSummary());
                compactRecentMemoryBeforeAppend(aiLogDir, chunk.getIndex(), readingMemory, chunkMemorySummary, tokenStatisticsAccumulator, reuseAiLog);
                readingMemory.getRecentMemory().add(chunkMemorySummary);
                chunkReadingMemory_list.add(buildChunkReadingMemorySnapshot(chunk, readingMemory));

                if (i > 0) {
                    submitChunkWikiDraftWorker(aiLogDir, chunks.get(i - 1), chunkSummary_list, chunkReadingMemory_list, draftFutures, draftExecutor, tokenStatisticsAccumulator, reuseAiLog);
                }
            }
            if (!chunks.isEmpty()) {
                submitChunkWikiDraftWorker(aiLogDir, chunks.get(chunks.size() - 1), chunkSummary_list, chunkReadingMemory_list, draftFutures, draftExecutor, tokenStatisticsAccumulator, reuseAiLog);
            }
            for (Future<AiOutInfoDraft> future : draftFutures) {
                aiOutInfoDraft_list.add(future.get());
            }
        } catch (Exception e) {
            throw new IOException("txt2world chunk draft worker failed", e);
        } finally {
            draftExecutor.shutdown();
        }
    }

    private void submitChunkWikiDraftWorker(Path aiLogDir,
                                            ChapterChunk chunk,
                                            List<ChunkSummary> chunkSummaries,
                                            List<ChunkReadingMemory> chunkReadingMemories,
                                            List<Future<AiOutInfoDraft>> draftFutures,
                                            ExecutorService draftExecutor,
                                            TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                            boolean reuseAiLog) {
        AiContextDraft aiContextDraft = buildAiContextDraft(chunk.getIndex(), chunkSummaries, chunkReadingMemories);
        draftFutures.add(draftExecutor.submit(() -> runSingleChunkWikiDraftWorker(aiLogDir, chunk, aiContextDraft, tokenStatisticsAccumulator, reuseAiLog)));
    }

    private AiOutInfoDraft runSingleChunkWikiDraftWorker(Path aiLogDir,
                                                         ChapterChunk chunk,
                                                         AiContextDraft aiContextDraft,
                                                         TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                                         boolean reuseAiLog) throws IOException {
        String prompt = buildChunkWikiDraftPrompt(chunk, aiContextDraft);
        String answer = askAiWithLog(aiLogDir, chunk.getIndex(), "chunk-" + chunk.getIndex() + "-draft", "chunk-draft", prompt, tokenStatisticsAccumulator, reuseAiLog);

        AiOutInfoDraft draft = parseAiObject(answer, AiOutInfoDraft.class);
        AiOutInfoDraft normalized = isEmptyDraftOutput(draft) ? fallbackChunkWikiDraft(chunk) : normalizeChunkWikiDraft(chunk, draft);
        verifyDraftEvidences(normalized, chunk);
        return normalized;
    }

    private AiOutInfoMergeReviewResult runWikiEntityMergeReviewWorker(Path aiLogDir,
                                                                      MergedWikiData mergedWiki,
                                                                      TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                                                      boolean reuseAiLog) throws IOException {
        AiOutInfoMergeReviewResult mergedReview = fallbackEntityMergeReview();
        mergeReviewBatch(aiLogDir, "characters", "人物", mergedWiki.getCharacters(), mergedReview, tokenStatisticsAccumulator, reuseAiLog);
        mergeReviewBatch(aiLogDir, "factions", "势力", mergedWiki.getFactions(), mergedReview, tokenStatisticsAccumulator, reuseAiLog);
        mergeReviewBatch(aiLogDir, "items", "物品", mergedWiki.getItems(), mergedReview, tokenStatisticsAccumulator, reuseAiLog);
        normalizeMergedWiki(mergedWiki);
        return mergedReview;
    }

    private void mergeReviewBatch(Path aiLogDir,
                                  String batchName,
                                  String wikiSectionType,
                                  List<WorldObj> worldObjs,
                                  AiOutInfoMergeReviewResult mergedReview,
                                  TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                  boolean reuseAiLog) throws IOException {
        if (worldObjs == null || worldObjs.isEmpty()) {
            return;
        }
        String prompt = buildWikiEntityMergeReviewPrompt(wikiSectionType, worldObjs);
        String logName = "entity-merge-review-" + batchName;
        String answer = askAiWithLog(aiLogDir, 0, logName, logName, prompt, tokenStatisticsAccumulator, reuseAiLog);

        AiOutInfoMergeReviewResult review = parseAiObject(answer, AiOutInfoMergeReviewResult.class);
        if (review == null || review.getMergeGroups() == null) {
            return;
        }
        applyEntityMergeReview(worldObjs, review.getMergeGroups());
        mergedReview.getMergeGroups().addAll(review.getMergeGroups());
    }

    private List<AiOutInfoDraft> chunkWorldObjAlign(List<AiOutInfoDraft> aiOutInfoDraftList, MergedWikiData mergedWiki) {
        List<AiOutInfoDraft> alignedDrafts = new ArrayList<>();
        for (AiOutInfoDraft draft : defaultList(aiOutInfoDraftList)) {
            if (draft == null) {
                continue;
            }
            AiOutInfoDraft aligned = new AiOutInfoDraft();
            aligned.setChunkIndex(draft.getChunkIndex());
            aligned.setStartChapterSeq(draft.getStartChapterSeq());
            aligned.setEndChapterSeq(draft.getEndChapterSeq());
            aligned.setCharacters(alignChunkWorldObjList(draft.getCharacters(), mergedWiki.getCharacters(), "人物"));
            aligned.setFactions(alignChunkWorldObjList(draft.getFactions(), mergedWiki.getFactions(), "势力"));
            aligned.setItems(alignChunkWorldObjList(draft.getItems(), mergedWiki.getItems(), "物品"));
            alignedDrafts.add(aligned);
        }
        return alignedDrafts;
    }

    private List<WorldObj> alignChunkWorldObjList(List<WorldObj> chunkWorldObjs, List<WorldObj> mergedWorldObjs, String sectionType) {
        Map<String, WorldObj> alignedByMergedName = new LinkedHashMap<>();
        for (WorldObj chunkWorldObj : defaultList(chunkWorldObjs)) {
            if (chunkWorldObj == null || ObjectUtils.isEmpty(chunkWorldObj.getName())) {
                continue;
            }
            WorldObj mergedTarget = findMergedWorldObjTarget(mergedWorldObjs, chunkWorldObj, sectionType);
            if (mergedTarget == null || ObjectUtils.isEmpty(mergedTarget.getName())) {
                continue;
            }
            WorldObj alignedItem = copyWorldObj(chunkWorldObj);
            applyMergedIdentity(alignedItem, mergedTarget);
            WorldObj existing = alignedByMergedName.get(mergedTarget.getName());
            if (existing == null) {
                alignedByMergedName.put(mergedTarget.getName(), alignedItem);
            } else {
                mergeWorldObjInto(existing, alignedItem);
                applyMergedIdentity(existing, mergedTarget);
            }
        }
        return normalizeMergedWorldObjList(new ArrayList<>(alignedByMergedName.values()));
    }

    private WorldObj findMergedWorldObjTarget(List<WorldObj> mergedWorldObjs, WorldObj chunkWorldObj, String sectionType) {
        for (WorldObj mergedWorldObj : defaultList(mergedWorldObjs)) {
            if (mergedWorldObj != null
                && matchesExpectedWikiSectionType(mergedWorldObj, sectionType)
                && shouldMergeWorldObj(mergedWorldObj, chunkWorldObj)) {
                return mergedWorldObj;
            }
        }
        return null;
    }

    private void applyMergedIdentity(WorldObj target, WorldObj mergedTarget) {
        target.setName(mergedTarget.getName());
        target.setWikiSectionType(normalizeWikiSectionType(mergedTarget.getWikiSectionType()));
        target.setAliases(normalizeAliases(mergedTarget.getAliases()));
        normalizeWorldObjLike(target);
    }

    private List<AiOutInfoRelationExtract> runCharacterRelationExtractWorkers(Path aiLogDir,
                                                                               List<ChunkSummary> chunkSummaries,
                                                                               List<ChunkReadingMemory> chunkReadingMemories,
                                                                               List<AiOutInfoDraft> alignedChunkDrafts,
                                                                               TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                                                               boolean reuseAiLog) throws IOException {
        ExecutorService relationExecutor = Executors.newFixedThreadPool(CHARACTER_RELATION_WORKER_THREAD_NUM);
        List<Future<AiOutInfoRelationExtract>> relationFutures = new ArrayList<>();
        try {
            for (AiOutInfoDraft alignedDraft : defaultList(alignedChunkDrafts)) {
                if (alignedDraft == null) {
                    continue;
                }
                ChunkSummary chunkSummary = getChunkSummary(chunkSummaries, defaultNumber(alignedDraft.getChunkIndex()));
                ChunkReadingMemory chunkReadingMemory = getChunkReadingMemory(chunkReadingMemories, defaultNumber(alignedDraft.getChunkIndex()));
                relationFutures.add(relationExecutor.submit(() -> runSingleCharacterRelationExtractWorker(
                    aiLogDir,
                    alignedDraft,
                    chunkSummary,
                    chunkReadingMemory,
                    tokenStatisticsAccumulator,
                    reuseAiLog
                )));
            }

            List<AiOutInfoRelationExtract> relationExtracts = new ArrayList<>();
            for (Future<AiOutInfoRelationExtract> future : relationFutures) {
                relationExtracts.add(future.get());
            }
            return relationExtracts;
        } catch (Exception e) {
            throw new IOException("txt2world character relation extract worker failed", e);
        } finally {
            relationExecutor.shutdown();
        }
    }

    private AiOutInfoRelationExtract runSingleCharacterRelationExtractWorker(Path aiLogDir,
                                                                             AiOutInfoDraft alignedDraft,
                                                                             ChunkSummary chunkSummary,
                                                                             ChunkReadingMemory chunkReadingMemory,
                                                                             TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                                                             boolean reuseAiLog) throws IOException {
        String prompt = buildCharacterRelationExtractPrompt(alignedDraft, chunkSummary, chunkReadingMemory);
        int chunkIndex = defaultNumber(alignedDraft.getChunkIndex());
        String answer = askAiWithLog(aiLogDir, chunkIndex, "chunk-" + chunkIndex + "-relation", "character-relation-extract", prompt, tokenStatisticsAccumulator, reuseAiLog);

        AiOutInfoRelationExtract relationExtract = parseAiObject(answer, AiOutInfoRelationExtract.class);
        if (relationExtract == null || relationExtract.getCharacterRelations() == null) {
            relationExtract = fallbackCharacterRelationExtract();
        }
        return normalizeCharacterRelationExtract(relationExtract, alignedDraft);
    }

    private String buildChunkPlotPrompt(ChapterChunk chunk, AiContextPlot context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("chunkIndex", chunk.getIndex());
        variables.put("startChapterSeq", chunk.getStartChapterSeq());
        variables.put("endChapterSeq", chunk.getEndChapterSeq());
        variables.put("chunkReadingMemory", context.getChunkReadingMemory());
        variables.put("chunkContent", chunk.getContent());
        return PromptUtils.loadPrompt("chunk_plot_reader_user_prompt.txt", variables);
    }

    private String buildChunkWikiDraftPrompt(ChapterChunk chunk, AiContextDraft context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("chunkIndex", chunk.getIndex());
        variables.put("startChapterSeq", chunk.getStartChapterSeq());
        variables.put("endChapterSeq", chunk.getEndChapterSeq());
        variables.put("context", context);
        variables.put("chunkContent", chunk.getContent());
        return PromptUtils.loadPrompt("chunk_wiki_draft_user_prompt.txt", variables);
    }

    private String buildWikiEntityMergeReviewPrompt(String wikiSectionType, List<WorldObj> worldObjs) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("worldObjs", buildMergeReviewWorldObjs(wikiSectionType, worldObjs));
        return PromptUtils.loadPrompt("wiki_entity_merge_review_user_prompt.txt", variables);
    }

    private String buildCharacterRelationExtractPrompt(AiOutInfoDraft alignedDraft,
                                                       ChunkSummary chunkSummary,
                                                       ChunkReadingMemory chunkReadingMemory) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("chunkIndex", alignedDraft.getChunkIndex());
        variables.put("startChapterSeq", alignedDraft.getStartChapterSeq());
        variables.put("endChapterSeq", alignedDraft.getEndChapterSeq());
        variables.put("chunkReadingMemory", simplifyChunkReadingMemory(chunkReadingMemory));
        variables.put("chunkAndChapterSummary", buildRelationChunkSummary(chunkSummary));
        variables.put("chunkCharacterWorldObjList", buildRelationCharacterWorldObjList(alignedDraft.getCharacters()));
        return PromptUtils.loadPrompt("character_relation_extract_user_prompt.txt", variables);
    }

    private void compactRecentMemoryBeforeAppend(Path aiLogDir,
                                                 int chunkIndex,
                                                 ChunkReadingMemory readingMemory,
                                                 String pendingSummary,
                                                 TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                                 boolean reuseAiLog) throws IOException {
        if (readingMemory.getRecentMemory().isEmpty()
                || memoryCharCount(readingMemory.getRecentMemory()) + memoryCharCount(pendingSummary) <= RECENT_MEMORY_CHAR_LIMIT) {
            return;
        }
        String compactedSummary = compactMemoryLevel(aiLogDir,
                chunkIndex,
                readingMemory.getRecentMemory(),
                RECENT_MEMORY_COMPACT_CHAR_LIMIT,
                "recentMemory",
                tokenStatisticsAccumulator,
                reuseAiLog);
        appendMidMemory(aiLogDir, chunkIndex, readingMemory, compactedSummary, tokenStatisticsAccumulator, reuseAiLog);
    }

    private void appendMidMemory(Path aiLogDir,
                                 int chunkIndex,
                                 ChunkReadingMemory readingMemory,
                                 String pendingSummary,
                                 TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                 boolean reuseAiLog) throws IOException {
        compactMidMemoryBeforeAppend(aiLogDir, chunkIndex, readingMemory, pendingSummary, tokenStatisticsAccumulator, reuseAiLog);
        readingMemory.getMidMemory().add(limitMemorySummary(pendingSummary));
    }

    private void compactMidMemoryBeforeAppend(Path aiLogDir,
                                              int chunkIndex,
                                              ChunkReadingMemory readingMemory,
                                              String pendingSummary,
                                              TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                              boolean reuseAiLog) throws IOException {
        if (readingMemory.getMidMemory().isEmpty()
                || memoryCharCount(readingMemory.getMidMemory()) + memoryCharCount(pendingSummary) <= MID_MEMORY_CHAR_LIMIT) {
            return;
        }
        String compactedSummary = compactMemoryLevel(aiLogDir,
                chunkIndex,
                readingMemory.getMidMemory(),
                MID_MEMORY_COMPACT_CHAR_LIMIT,
                "midMemory",
                tokenStatisticsAccumulator,
                reuseAiLog);
        readingMemory.getLongTermMemory().add(limitMemorySummary(compactedSummary));
    }

    private String compactMemoryLevel(Path aiLogDir,
                                      int chunkIndex,
                                      List<String> source,
                                      int compactCharLimit,
                                      String sourceLevel,
                                      TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                      boolean reuseAiLog) throws IOException {
        List<String> itemsToCompact = takeOldestMemoryItems(source, compactCharLimit);
        if (itemsToCompact.isEmpty()) {
            return "";
        }
        String prompt = buildCompactMemoryPrompt(itemsToCompact);
        String answer = askAiWithLog(aiLogDir, chunkIndex, "chunk-" + chunkIndex + "-compact-" + sourceLevel, "compact-memory", prompt, tokenStatisticsAccumulator, reuseAiLog);

        String compactedSummary = "";
        JSONObject parsed = parseJsonObject(answer);
        if (parsed != null) {
            compactedSummary = defaultText(parsed.getString("summary"), "");
        }
        if (ObjectUtils.isEmpty(compactedSummary)) {
            compactedSummary = fallbackCompactMemorySummary(itemsToCompact);
        }
        for (int i = 0; i < itemsToCompact.size() && !source.isEmpty(); i++) {
            source.remove(0);
        }
        return limitMemorySummary(compactedSummary);
    }

    private List<String> takeOldestMemoryItems(List<String> source, int compactCharLimit) {
        List<String> itemsToCompact = new ArrayList<>();
        int charCount = 0;
        for (String item : defaultList(source)) {
            int itemCharCount = memoryCharCount(item);
            if (!itemsToCompact.isEmpty() && charCount + itemCharCount > compactCharLimit) {
                break;
            }
            itemsToCompact.add(item);
            charCount += itemCharCount;
            if (charCount >= compactCharLimit) {
                break;
            }
        }
        return itemsToCompact;
    }

    private int memoryCharCount(List<String> memory) {
        int charCount = 0;
        for (String item : defaultList(memory)) {
            charCount += memoryCharCount(item);
        }
        return charCount;
    }

    private int memoryCharCount(String memory) {
        return defaultText(memory, "").length();
    }

    private String buildCompactMemoryPrompt(List<String> itemsToCompact) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("itemsToCompact", itemsToCompact);
        return PromptUtils.loadPrompt("compact_memory_user_prompt.txt", variables);
    }

    private AiContextPlot buildAiContextPlot(int chunkIndex, ChunkReadingMemory readingMemory) {
        AiContextPlot context = new AiContextPlot();
        context.setChunkIndex(chunkIndex);
        context.setChunkReadingMemory(copyMemorySlots(readingMemory));
        return context;
    }

    private AiContextDraft buildAiContextDraft(int chunkIndex,
                                               List<ChunkSummary> chunkSummaries,
                                               List<ChunkReadingMemory> chunkReadingMemories) {
        AiContextDraft context = new AiContextDraft();
        context.setChunkSummary(getPlotSummary(chunkSummaries, chunkIndex));
        context.setNextChunkSummary(getPlotSummary(chunkSummaries, chunkIndex + 1));
        context.setChunkReadingMemory(simplifyChunkReadingMemory(getChunkReadingMemory(chunkReadingMemories, chunkIndex)));
        return context;
    }

    private AiOutInfoPlot buildRelationChunkSummary(ChunkSummary chunkSummary) {
        AiOutInfoPlot plot = new AiOutInfoPlot();
        if (chunkSummary == null) {
            plot.setChunkSummary("");
            plot.setChapters(new ArrayList<>());
            return plot;
        }
        plot.setChunkSummary(defaultText(chunkSummary.getChunkSummary(), ""));
        plot.setChapters(defaultList(chunkSummary.getChapters()));
        return plot;
    }

    private List<WorldObj> buildRelationCharacterWorldObjList(List<WorldObj> characters) {
        List<WorldObj> compact = new ArrayList<>();
        for (WorldObj character : defaultList(characters)) {
            if (character == null || ObjectUtils.isEmpty(character.getName()) || !matchesExpectedWikiSectionType(character, "人物")) {
                continue;
            }
            WorldObj item = new WorldObj();
            item.setName(character.getName());
            item.setAliases(normalizeAliases(character.getAliases()));
            compact.add(item);
        }
        return compact;
    }

    private String getPlotSummary(List<ChunkSummary> chunkSummaries, int index) {
        ChunkSummary summary = getChunkSummary(chunkSummaries, index);
        return summary == null ? "" : defaultText(summary.getChunkSummary(), "");
    }

    private ChunkReadingMemory getChunkReadingMemory(List<ChunkReadingMemory> chunkReadingMemories, int chunkIndex) {
        for (ChunkReadingMemory item : chunkReadingMemories) {
            if (item != null && item.getChunkIndex() == chunkIndex) {
                return item;
            }
        }
        return null;
    }

    private ChunkSummary getChunkSummary(List<ChunkSummary> chunkSummaries, int chunkIndex) {
        for (ChunkSummary item : chunkSummaries) {
            if (item != null && item.getChunkIndex() != null && item.getChunkIndex() == chunkIndex) {
                return item;
            }
        }
        return null;
    }

    private ChunkReadingMemory simplifyChunkReadingMemory(ChunkReadingMemory readingMemorySnapshot) {
        if (readingMemorySnapshot == null) {
            return buildEmptyReadingMemory();
        }
        return copyMemorySlots(readingMemorySnapshot);
    }

    private ChunkSummary normalizeChunkSummary(ChapterChunk chunk, AiOutInfoPlot aiOutInfoPlot) {
        ChunkSummary normalized = new ChunkSummary();
        normalized.setChunkIndex(chunk.getIndex());
        normalized.setStartChapterSeq(chunk.getStartChapterSeq());
        normalized.setEndChapterSeq(chunk.getEndChapterSeq());
        normalized.setChunkSummary(limitMemorySummary(aiOutInfoPlot.getChunkSummary()));
        normalized.setChapters(normalizeChapterSummaries(aiOutInfoPlot.getChapters()));
        return normalized;
    }

    private List<AiOutInfoPlotChapter> normalizeChapterSummaries(List<AiOutInfoPlotChapter> chapters) {
        List<AiOutInfoPlotChapter> normalized = new ArrayList<>();
        if (chapters == null) {
            return normalized;
        }
        Set<Integer> seen = new HashSet<>();
        for (AiOutInfoPlotChapter chapter : chapters) {
            if (chapter == null || chapter.getChapterSeq() == null || chapter.getChapterSeq() <= 0 || !seen.add(chapter.getChapterSeq())) {
                continue;
            }
            AiOutInfoPlotChapter normalizedChapter = new AiOutInfoPlotChapter();
            normalizedChapter.setChapterSeq(chapter.getChapterSeq());
            normalizedChapter.setChapterSummary(limitChapterSummary(chapter.getChapterSummary()));
            normalized.add(normalizedChapter);
        }
        return normalized;
    }

    private ChunkReadingMemory buildChunkReadingMemorySnapshot(ChapterChunk chunk, ChunkReadingMemory readingMemory) {
        ChunkReadingMemory normalized = copyMemorySlots(readingMemory);
        normalized.setChunkIndex(chunk.getIndex());
        normalized.setStartChapterSeq(chunk.getStartChapterSeq());
        normalized.setEndChapterSeq(chunk.getEndChapterSeq());
        return normalized;
    }

    private AiOutInfoDraft normalizeChunkWikiDraft(ChapterChunk chunk, AiOutInfoDraft draft) {
        Map<String, List<WorldObj>> objectsBySection = regroupDraftWorldObjsBySection(draft);
        AiOutInfoDraft normalized = new AiOutInfoDraft();
        normalized.setChunkIndex(chunk.getIndex());
        normalized.setStartChapterSeq(chunk.getStartChapterSeq());
        normalized.setEndChapterSeq(chunk.getEndChapterSeq());
        normalized.setCharacters(normalizeDraftObjectList(objectsBySection.get("人物"), "人物", chunk.getIndex()));
        normalized.setFactions(normalizeDraftObjectList(objectsBySection.get("势力"), "势力", chunk.getIndex()));
        normalized.setItems(normalizeDraftObjectList(objectsBySection.get("物品"), "物品", chunk.getIndex()));
        return normalized;
    }

    private Map<String, List<WorldObj>> regroupDraftWorldObjsBySection(AiOutInfoDraft draft) {
        Map<String, List<WorldObj>> objectsBySection = new HashMap<>();
        objectsBySection.put("人物", new ArrayList<>());
        objectsBySection.put("势力", new ArrayList<>());
        objectsBySection.put("物品", new ArrayList<>());
        addDraftWorldObjsBySection(objectsBySection, draft.getCharacters(), "人物");
        addDraftWorldObjsBySection(objectsBySection, draft.getFactions(), "势力");
        addDraftWorldObjsBySection(objectsBySection, draft.getItems(), "物品");
        return objectsBySection;
    }

    private void addDraftWorldObjsBySection(Map<String, List<WorldObj>> objectsBySection, List<WorldObj> objects, String fallbackSectionType) {
        for (WorldObj item : defaultList(objects)) {
            if (item == null || ObjectUtils.isEmpty(item.getName())) {
                continue;
            }
            String targetSectionType = resolveDraftWorldObjSectionType(item, fallbackSectionType);
            objectsBySection.get(targetSectionType).add(item);
        }
    }

    private String resolveDraftWorldObjSectionType(WorldObj item, String fallbackSectionType) {
        String rawSectionType = defaultText(item.getWikiSectionType(), "").trim();
        if (isSupportedWikiSectionType(rawSectionType)) {
            return normalizeWikiSectionType(rawSectionType);
        }
        return fallbackSectionType;
    }

    private List<WorldObj> normalizeDraftObjectList(List<WorldObj> objects, String sectionType, int chunkIndex) {
        List<WorldObj> normalized = new ArrayList<>();
        if (objects == null) {
            return normalized;
        }
        for (WorldObj item : objects) {
            if (item == null || ObjectUtils.isEmpty(item.getName())) {
                continue;
            }
            WorldObj normalizedItem = new WorldObj();
            normalizedItem.setName(item.getName().trim());
            normalizedItem.setWikiSectionType(sectionType);
            normalizedItem.setImportanceLevel(item.getImportanceLevel());
            normalizedItem.setSummary(defaultText(item.getSummary(), "").trim());
            normalizedItem.setLifeStatus(item.getLifeStatus());
            normalizedItem.setAppearanceChapterSeqs(item.getAppearanceChapterSeqs());
            normalizedItem.setIdentInfos(normalizeDraftIdentInfos(item.getIdentInfos()));
            normalizedItem.setChunkSummaryList(normalizeChunkSummaryList(item.getChunkSummaryList(), chunkIndex, item.getSummary()));
            normalizedItem.setAliases(normalizeAliases(item.getAliases()));
            normalizedItem.setEvidences(normalizeEvidences(item.getEvidences(), chunkIndex));
            normalizeWorldObjLike(normalizedItem);
            normalized.add(normalizedItem);
        }
        return normalized;
    }

    private boolean matchesExpectedWikiSectionType(WorldObj item, String sectionType) {
        String rawSectionType = defaultText(item == null ? null : item.getWikiSectionType(), "").trim();
        return rawSectionType.isEmpty() || normalizeWikiSectionType(rawSectionType).equals(sectionType);
    }

    private void normalizeMergedWiki(MergedWikiData mergedWiki) {
        mergedWiki.setCharacters(normalizeMergedWorldObjList(mergedWiki.getCharacters()));
        mergedWiki.setFactions(normalizeMergedWorldObjList(mergedWiki.getFactions()));
        mergedWiki.setItems(normalizeMergedWorldObjList(mergedWiki.getItems()));
    }

    private MergedWikiData mergeChunkDraftsByJava(List<AiOutInfoDraft> chunkWikiDrafts) {
        MergedWikiData mergedWiki = new MergedWikiData();
        List<WorldObj> mergedCharacters = new ArrayList<>();
        List<WorldObj> mergedFactions = new ArrayList<>();
        List<WorldObj> mergedItems = new ArrayList<>();
        for (AiOutInfoDraft draft : defaultList(chunkWikiDrafts)) {
            mergeDraftObjects(mergedCharacters, draft == null ? null : draft.getCharacters(), draft == null ? null : draft.getChunkIndex());
            mergeDraftObjects(mergedFactions, draft == null ? null : draft.getFactions(), draft == null ? null : draft.getChunkIndex());
            mergeDraftObjects(mergedItems, draft == null ? null : draft.getItems(), draft == null ? null : draft.getChunkIndex());
        }
        mergedWiki.setCharacters(mergedCharacters);
        mergedWiki.setFactions(mergedFactions);
        mergedWiki.setItems(mergedItems);
        normalizeMergedWiki(mergedWiki);
        return mergedWiki;
    }

    private void mergeDraftObjects(List<WorldObj> mergedObjects, List<WorldObj> objects, Integer chunkIndex) {
        if (objects == null || chunkIndex == null) {
            return;
        }
        for (WorldObj object : objects) {
            if (object == null || ObjectUtils.isEmpty(object.getName())) {
                continue;
            }
            normalizeWorldObjLike(object);
            WorldObj same = findMergeTarget(mergedObjects, object);
            if (same == null) {
                mergedObjects.add(copyWorldObj(object));
            } else {
                mergeWorldObjInto(same, object);
            }
        }
    }

    private void applyEntityMergeReview(List<WorldObj> worldObjs, List<AiOutInfoMergeReview> mergeGroups) {
        if (worldObjs == null || mergeGroups == null) {
            return;
        }
        for (AiOutInfoMergeReview group : mergeGroups) {
            if (group == null || group.getNames() == null || group.getNames().size() < 2) {
                continue;
            }
            WorldObj target = selectEntityMergeTarget(worldObjs, group.getNames());
            if (target == null) {
                continue;
            }
            for (String sourceNameRaw : group.getNames()) {
                String sourceName = defaultText(sourceNameRaw, "").trim();
                if (!isMergeableAlias(sourceName) || sourceName.equals(target.getName())) {
                    continue;
                }
                WorldObj source = findWorldObjByName(worldObjs, sourceName);
                if (source == null || source == target) {
                    continue;
                }
                addAliasToWorldObj(target, sourceName, "AI合并名称");
                mergeWorldObjInto(target, source);
                worldObjs.remove(source);
            }
        }
    }

    private WorldObj selectEntityMergeTarget(List<WorldObj> worldObjs, List<String> names) {
        WorldObj target = null;
        int maxNameLen = -1;
        for (String name : defaultList(names)) {
            WorldObj candidate = findWorldObjByName(worldObjs, defaultText(name, "").trim());
            if (candidate == null) {
                continue;
            }
            int currentNameLen = candidate.getName() == null ? 0 : candidate.getName().trim().length();
            if (target == null || currentNameLen > maxNameLen) {
                target = candidate;
                maxNameLen = currentNameLen;
            }
        }
        return target;
    }

    private WorldObj findWorldObjByName(List<WorldObj> worldObjs, String name) {
        if (worldObjs == null || ObjectUtils.isEmpty(name)) {
            return null;
        }
        for (WorldObj worldObj : worldObjs) {
            if (worldObj != null && name.equals(defaultText(worldObj.getName(), "").trim())) {
                return worldObj;
            }
        }
        return null;
    }

    private void addAliasToWorldObj(WorldObj worldObj, String aliasText, String type) {
        if (worldObj == null || ObjectUtils.isEmpty(aliasText) || aliasText.equals(worldObj.getName())) {
            return;
        }
        List<WorldObjAlias> aliases = worldObj.getAliases();
        if (aliases == null) {
            aliases = new ArrayList<>();
            worldObj.setAliases(aliases);
        }
        for (WorldObjAlias alias : aliases) {
            if (alias != null && aliasText.equals(alias.getAlias())) {
                return;
            }
        }
        WorldObjAlias alias = new WorldObjAlias();
        alias.setAlias(aliasText);
        alias.setType(type);
        aliases.add(alias);
    }

    private WorldObj findMergeTarget(List<WorldObj> mergedObjects, WorldObj candidate) {
        for (WorldObj existing : mergedObjects) {
            if (shouldMergeWorldObj(existing, candidate)) {
                return existing;
            }
        }
        return null;
    }

    private boolean shouldMergeWorldObj(WorldObj a, WorldObj b) {
        if (!normalizeWikiSectionType(a.getWikiSectionType()).equals(normalizeWikiSectionType(b.getWikiSectionType()))) {
            return false;
        }
        String aName = defaultText(a.getName(), "").trim();
        String bName = defaultText(b.getName(), "").trim();
        if (!aName.isEmpty() && aName.equals(bName)) {
            return true;
        }
        Set<String> aAliases = collectMergeAliasTexts(a.getAliases());
        Set<String> bAliases = collectMergeAliasTexts(b.getAliases());
        if (aAliases.contains(bName) && isMergeableAlias(bName)) {
            return true;
        }
        if (bAliases.contains(aName) && isMergeableAlias(aName)) {
            return true;
        }
        for (String alias : aAliases) {
            if (bAliases.contains(alias) && isMergeableAlias(alias)) {
                return true;
            }
        }
        return false;
    }

    private void mergeWorldObjInto(WorldObj target, WorldObj source) {
        int targetLatestChunkIndex = latestChunkIndex(target.getChunkSummaryList());
        int sourceLatestChunkIndex = latestChunkIndex(source.getChunkSummaryList());
        target.setImportanceLevel(higherImportanceLevel(target.getImportanceLevel(), source.getImportanceLevel()));
        target.setSummary(null);
        target.setChunkSummaryList(mergeSummaryEntries(target.getChunkSummaryList(), source.getChunkSummaryList()));
        if ("人物".equals(normalizeWikiSectionType(target.getWikiSectionType()))) {
            target.setAppearanceChapterSeqs(mergeAppearanceChapterSeqs(target.getAppearanceChapterSeqs(), source.getAppearanceChapterSeqs()));
            if (sourceLatestChunkIndex >= targetLatestChunkIndex) {
                target.setLifeStatus(normalizeLifeStatus(source.getLifeStatus()));
            }
        }
        target.setIdentInfos(mergeIdentInfos(target.getIdentInfos(), source.getIdentInfos()));
        target.setAliases(mergeAliases(target.getAliases(), source.getAliases(), target.getName()));
        target.setEvidences(mergeEvidences(target.getEvidences(), source.getEvidences()));
    }

    private List<WorldObj> normalizeMergedWorldObjList(List<WorldObj> worldObjs) {
        List<WorldObj> normalized = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (WorldObj item : defaultList(worldObjs)) {
            if (item == null || ObjectUtils.isEmpty(item.getName())) {
                continue;
            }
            String sectionType = item.getWikiSectionType();
            if (!ObjectUtils.isEmpty(sectionType) && !isSupportedWikiSectionType(sectionType)) {
                continue;
            }
            String name = item.getName().trim();
            if (!seenNames.add(name)) {
                continue;
            }
            item.setName(name);
            normalizeWorldObjLike(item);
            normalized.add(item);
        }
        return normalized;
    }

    private List<WorldObj> buildMergeReviewWorldObjs(String wikiSectionType, List<WorldObj> worldObjs) {
        List<WorldObj> compact = new ArrayList<>();
        for (WorldObj worldObj : defaultList(worldObjs)) {
            if (worldObj == null || ObjectUtils.isEmpty(worldObj.getName())) {
                continue;
            }
            WorldObj item = new WorldObj();
            item.setName(worldObj.getName());
            item.setWikiSectionType(normalizeWikiSectionType(defaultText(worldObj.getWikiSectionType(), wikiSectionType)));
            item.setAliases(normalizeAliases(worldObj.getAliases()));
            item.setIdentInfos(normalizeIdentInfos(worldObj.getIdentInfos()));
            compact.add(item);
        }
        return compact;
    }

    private WorldObj copyWorldObj(WorldObj source) {
        WorldObj copy = new WorldObj();
        copy.setName(source.getName());
        copy.setWikiSectionType(normalizeWikiSectionType(source.getWikiSectionType()));
        copy.setImportanceLevel(normalizeImportanceLevel(source.getImportanceLevel()));
        copy.setSummary(source.getSummary());
        copy.setLifeStatus(source.getLifeStatus());
        copy.setAppearanceChapterSeqs(normalizeAppearanceChapterSeqs(source.getAppearanceChapterSeqs()));
        copy.setIdentInfos(normalizeIdentInfos(source.getIdentInfos()));
        copy.setChunkSummaryList(normalizeSummaryEntries(source.getChunkSummaryList()));
        copy.setAliases(normalizeAliases(source.getAliases()));
        copy.setEvidences(normalizeEvidences(source.getEvidences(), 0));
        normalizeWorldObjLike(copy);
        return copy;
    }

    private List<WorldObjAlias> mergeAliases(List<WorldObjAlias> left, List<WorldObjAlias> right, String name) {
        List<WorldObjAlias> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addAliases(merged, seen, left, name);
        addAliases(merged, seen, right, name);
        return merged;
    }

    private void addAliases(List<WorldObjAlias> merged, Set<String> seen, List<WorldObjAlias> source, String name) {
        for (WorldObjAlias alias : defaultList(source)) {
            if (alias == null || ObjectUtils.isEmpty(alias.getAlias())) {
                continue;
            }
            String aliasText = alias.getAlias().trim();
            if (aliasText.equals(name) || !seen.add(aliasText)) {
                continue;
            }
            WorldObjAlias copy = new WorldObjAlias();
            copy.setAlias(aliasText);
            copy.setType(defaultText(alias.getType(), "别名"));
            merged.add(copy);
        }
    }

    private List<Evidence> mergeEvidences(List<Evidence> left, List<Evidence> right) {
        List<Evidence> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addEvidences(merged, seen, left);
        addEvidences(merged, seen, right);
        sortEvidencesByChunkIndex(merged);
        return merged;
    }

    private void addEvidences(List<Evidence> merged, Set<String> seen, List<Evidence> source) {
        for (Evidence evidence : defaultList(source)) {
            if (evidence == null || ObjectUtils.isEmpty(evidence.getEvidenceText())) {
                continue;
            }
            String key = evidence.getChunkIndex() + "|" + evidence.getChapterSeq() + "|" + evidence.getEvidenceText();
            if (seen.add(key)) {
                merged.add(copyEvidence(evidence));
            }
        }
    }

    private List<WorldObjChunkSummary> mergeSummaryEntries(List<WorldObjChunkSummary> left, List<WorldObjChunkSummary> right) {
        Map<Integer, WorldObjChunkSummary> summaryByChunk = new LinkedHashMap<>();
        addSummaryEntries(summaryByChunk, left);
        putSummaryEntries(summaryByChunk, right);
        List<WorldObjChunkSummary> merged = new ArrayList<>(summaryByChunk.values());
        sortSummaryEntries(merged);
        return merged;
    }

    private List<String> mergeIdentInfos(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addIdentInfos(merged, seen, left);
        addIdentInfos(merged, seen, right);
        return merged;
    }

    private void addIdentInfos(List<String> merged, Set<String> seen, List<String> source) {
        for (String identInfo : defaultList(source)) {
            String normalized = normalizeIdentInfo(identInfo);
            if (normalized.isEmpty() || !seen.add(normalized)) {
                continue;
            }
            merged.add(normalized);
        }
    }

    private void addSummaryEntries(Map<Integer, WorldObjChunkSummary> summaryByChunk, List<WorldObjChunkSummary> summaries) {
        for (WorldObjChunkSummary summary : defaultList(summaries)) {
            if (summary == null) {
                continue;
            }
            addSummaryEntries(summaryByChunk, defaultNumber(summary.getChunkIndex()), summary.getSummaryText());
        }
    }

    private void putSummaryEntries(Map<Integer, WorldObjChunkSummary> summaryByChunk, List<WorldObjChunkSummary> summaries) {
        for (WorldObjChunkSummary summary : defaultList(summaries)) {
            if (summary == null) {
                continue;
            }
            putSummaryEntry(summaryByChunk, defaultNumber(summary.getChunkIndex()), summary.getSummaryText());
        }
    }

    private void addSummaryEntries(Map<Integer, WorldObjChunkSummary> summaryByChunk, int chunkIndex, String summaryText) {
        String normalizedText = defaultText(summaryText, "").trim();
        if (normalizedText.isEmpty()) {
            return;
        }
        WorldObjChunkSummary existing = summaryByChunk.get(chunkIndex);
        if (existing == null) {
            WorldObjChunkSummary summary = new WorldObjChunkSummary();
            summary.setChunkIndex(chunkIndex);
            summary.setSummaryText(normalizedText);
            summaryByChunk.put(chunkIndex, summary);
            return;
        }
        existing.setSummaryText(joinText(existing.getSummaryText(), normalizedText));
    }

    private void putSummaryEntry(Map<Integer, WorldObjChunkSummary> summaryByChunk, int chunkIndex, String summaryText) {
        String normalizedText = defaultText(summaryText, "").trim();
        if (normalizedText.isEmpty()) {
            return;
        }
        WorldObjChunkSummary summary = new WorldObjChunkSummary();
        summary.setChunkIndex(chunkIndex);
        summary.setSummaryText(normalizedText);
        summaryByChunk.put(chunkIndex, summary);
    }

    private void sortSummaryEntries(List<WorldObjChunkSummary> summaries) {
        if (summaries == null || summaries.size() < 2) {
            return;
        }
        summaries.sort(Comparator.comparingInt(item -> defaultNumber(item.getChunkIndex())));
    }

    private void sortEvidencesByChunkIndex(List<Evidence> evidences) {
        if (evidences == null || evidences.size() < 2) {
            return;
        }
        evidences.sort(Comparator.comparingInt(Evidence::getChapterSeq));
    }

    private String getLatestSummaryText(List<WorldObjChunkSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "";
        }
        WorldObjChunkSummary last = summaries.get(summaries.size() - 1);
        return last == null ? "" : defaultText(last.getSummaryText(), "");
    }

    private void normalizeWorldObjLike(WorldObj item) {
        item.setWikiSectionType(normalizeWikiSectionType(item.getWikiSectionType()));
        item.setImportanceLevel(normalizeImportanceLevel(item.getImportanceLevel()));
        item.setSummary(ObjectUtils.isEmpty(item.getSummary()) ? null : item.getSummary().trim());
        if ("人物".equals(item.getWikiSectionType())) {
            item.setLifeStatus(normalizeLifeStatus(item.getLifeStatus()));
            item.setAppearanceChapterSeqs(normalizeAppearanceChapterSeqs(item.getAppearanceChapterSeqs()));
        } else {
            item.setLifeStatus(null);
            item.setAppearanceChapterSeqs(null);
        }
        item.setIdentInfos(normalizeIdentInfos(item.getIdentInfos()));
        item.setChunkSummaryList(normalizeSummaryEntries(item.getChunkSummaryList()));
        item.setAliases(normalizeAliases(item.getAliases()));
        item.setEvidences(normalizeEvidences(item.getEvidences(), 0));
        if (item.getChunkSummaryList() != null && !item.getChunkSummaryList().isEmpty()) {
            item.setSummary(null);
        }
    }

    private String normalizeLifeStatus(String lifeStatus) {
        String normalized = defaultText(lifeStatus, "").trim();
        return LIFE_STATUSES.contains(normalized) ? normalized : "活着";
    }

    private List<Integer> normalizeAppearanceChapterSeqs(List<Integer> chapterSeqs) {
        List<Integer> normalized = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Integer chapterSeq : defaultList(chapterSeqs)) {
            if (chapterSeq == null || chapterSeq <= 0 || !seen.add(chapterSeq)) {
                continue;
            }
            normalized.add(chapterSeq);
        }
        normalized.sort(Comparator.naturalOrder());
        return normalized;
    }

    private List<Integer> mergeAppearanceChapterSeqs(List<Integer> left, List<Integer> right) {
        List<Integer> merged = new ArrayList<>(defaultList(left));
        merged.addAll(defaultList(right));
        return normalizeAppearanceChapterSeqs(merged);
    }

    private int latestChunkIndex(List<WorldObjChunkSummary> summaries) {
        int latest = 0;
        for (WorldObjChunkSummary summary : defaultList(summaries)) {
            if (summary != null && summary.getChunkIndex() > latest) {
                latest = summary.getChunkIndex();
            }
        }
        return latest;
    }

    private AiOutInfoRelationExtract normalizeCharacterRelationExtract(AiOutInfoRelationExtract relationExtract, AiOutInfoDraft alignedDraft) {
        AiOutInfoRelationExtract normalized = new AiOutInfoRelationExtract();
        List<CharacterRelation> relations = new ArrayList<>();
        Map<String, String> characterNameMap = buildRelationCharacterNameMap(alignedDraft.getCharacters());
        Set<String> seen = new HashSet<>();
        int chunkIndex = defaultNumber(alignedDraft.getChunkIndex());
        int startChapterSeq = defaultNumber(alignedDraft.getStartChapterSeq());
        int endChapterSeq = defaultNumber(alignedDraft.getEndChapterSeq());

        for (CharacterRelation relation : defaultList(relationExtract.getCharacterRelations())) {
            CharacterRelation normalizedRelation = normalizeCharacterRelation(relation, characterNameMap, chunkIndex, startChapterSeq, endChapterSeq);
            if (normalizedRelation == null) {
                continue;
            }
            String key = normalizedRelation.getChunkIndex()
                + "|"
                + normalizedRelation.getFromChapterSeq()
                + "|"
                + normalizedRelation.getToChapterSeq()
                + "|"
                + normalizedRelation.getCharacterA().getName()
                + "|"
                + normalizedRelation.getCharacterB().getName()
                + "|"
                + normalizedRelation.getRelationSummary();
            if (seen.add(key)) {
                relations.add(normalizedRelation);
            }
        }
        normalized.setCharacterRelations(relations);
        return normalized;
    }

    private CharacterRelation normalizeCharacterRelation(CharacterRelation relation,
                                                         Map<String, String> characterNameMap,
                                                         int chunkIndex,
                                                         int startChapterSeq,
                                                         int endChapterSeq) {
        if (relation == null || relation.getCharacterA() == null || relation.getCharacterB() == null) {
            return null;
        }
        String characterAName = characterNameMap.get(defaultText(relation.getCharacterA().getName(), "").trim());
        String characterBName = characterNameMap.get(defaultText(relation.getCharacterB().getName(), "").trim());
        if (ObjectUtils.isEmpty(characterAName) || ObjectUtils.isEmpty(characterBName) || characterAName.equals(characterBName)) {
            return null;
        }

        int fromChapterSeq = relation.getFromChapterSeq() == null ? startChapterSeq : relation.getFromChapterSeq();
        int toChapterSeq = relation.getToChapterSeq() == null ? endChapterSeq : relation.getToChapterSeq();
        if (fromChapterSeq < startChapterSeq || toChapterSeq > endChapterSeq || fromChapterSeq > toChapterSeq) {
            return null;
        }

        CharacterRelation normalized = new CharacterRelation();
        normalized.setChunkIndex(chunkIndex);
        normalized.setFromChapterSeq(fromChapterSeq);
        normalized.setToChapterSeq(toChapterSeq);
        normalized.setCharacterA(normalizeCharacterRelationSide(characterAName, relation.getCharacterA()));
        normalized.setCharacterB(normalizeCharacterRelationSide(characterBName, relation.getCharacterB()));
        normalized.setRelationSummary(limitRelationSummary(relation.getRelationSummary()));
        return normalized;
    }

    private Map<String, String> buildRelationCharacterNameMap(List<WorldObj> characters) {
        Map<String, String> nameMap = new HashMap<>();
        for (WorldObj character : defaultList(characters)) {
            if (character == null || ObjectUtils.isEmpty(character.getName()) || !matchesExpectedWikiSectionType(character, "人物")) {
                continue;
            }
            String name = character.getName().trim();
            nameMap.put(name, name);
            for (WorldObjAlias alias : defaultList(character.getAliases())) {
                if (alias == null || ObjectUtils.isEmpty(alias.getAlias())) {
                    continue;
                }
                nameMap.put(alias.getAlias().trim(), name);
            }
        }
        return nameMap;
    }

    private CharacterRelationSide normalizeCharacterRelationSide(String name, CharacterRelationSide source) {
        CharacterRelationSide side = new CharacterRelationSide();
        side.setName(name);
        side.setRelationDimensionsToOther(normalizeRelationDimensions(source.getRelationDimensionsToOther()));
        return side;
    }

    private RelationDimensions normalizeRelationDimensions(RelationDimensions source) {
        RelationDimensions dimensions = new RelationDimensions();
        dimensions.setAttitude(normalizeEnumValue(source == null ? null : source.getAttitude(), RELATION_ATTITUDES));
        dimensions.setHierarchy(normalizeEnumValue(source == null ? null : source.getHierarchy(), RELATION_HIERARCHIES));
        dimensions.setFavorability(normalizeEnumValue(source == null ? null : source.getFavorability(), RELATION_FAVORABILITIES));
        dimensions.setBond(normalizeEnumValue(source == null ? null : source.getBond(), RELATION_BONDS));
        dimensions.setNarrativeRole(normalizeEnumValue(source == null ? null : source.getNarrativeRole(), RELATION_NARRATIVE_ROLES));
        return dimensions;
    }

    private List<WorldObjChunkSummary> normalizeSummaryEntries(List<WorldObjChunkSummary> summaries) {
        Map<Integer, WorldObjChunkSummary> summaryByChunk = new LinkedHashMap<>();
        addSummaryEntries(summaryByChunk, summaries);
        List<WorldObjChunkSummary> normalized = new ArrayList<>(summaryByChunk.values());
        sortSummaryEntries(normalized);
        return normalized;
    }

    private List<WorldObjChunkSummary> normalizeChunkSummaryList(List<WorldObjChunkSummary> summaries, int chunkIndex, String summaryText) {
        Map<Integer, WorldObjChunkSummary> summaryByChunk = new LinkedHashMap<>();
        addSummaryEntries(summaryByChunk, summaries);
        putSummaryEntry(summaryByChunk, chunkIndex, summaryText);
        List<WorldObjChunkSummary> normalized = new ArrayList<>(summaryByChunk.values());
        sortSummaryEntries(normalized);
        return normalized;
    }

    private List<WorldObjAlias> normalizeAliases(List<WorldObjAlias> aliases) {
        List<WorldObjAlias> normalized = new ArrayList<>();
        Set<String> seenAliases = new HashSet<>();
        for (WorldObjAlias alias : defaultList(aliases)) {
            if (alias == null || ObjectUtils.isEmpty(alias.getAlias())) {
                continue;
            }
            String aliasText = alias.getAlias().trim();
            if (!seenAliases.add(aliasText)) {
                continue;
            }
            WorldObjAlias normalizedAlias = new WorldObjAlias();
            normalizedAlias.setAlias(aliasText);
            normalizedAlias.setType(defaultText(alias.getType(), "别名"));
            normalized.add(normalizedAlias);
        }
        return normalized;
    }

    private List<String> normalizeDraftIdentInfos(List<String> identInfos) {
        List<String> normalized = new ArrayList<>();
        Set<String> seenIdentInfos = new HashSet<>();
        int totalLength = 0;
        for (String identInfo : defaultList(identInfos)) {
            String normalizedItem = normalizeIdentInfo(identInfo);
            if (normalizedItem.isEmpty() || !seenIdentInfos.add(normalizedItem)) {
                continue;
            }
            if (totalLength >= 120) {
                break;
            }
            int remainLength = 120 - totalLength;
            if (normalizedItem.length() > remainLength) {
                normalizedItem = normalizedItem.substring(0, remainLength);
            }
            if (normalizedItem.isEmpty()) {
                break;
            }
            normalized.add(normalizedItem);
            totalLength += normalizedItem.length();
        }
        return normalized;
    }

    private List<String> normalizeIdentInfos(List<String> identInfos) {
        List<String> normalized = new ArrayList<>();
        Set<String> seenIdentInfos = new HashSet<>();
        for (String identInfo : defaultList(identInfos)) {
            String normalizedItem = normalizeIdentInfo(identInfo);
            if (normalizedItem.isEmpty() || !seenIdentInfos.add(normalizedItem)) {
                continue;
            }
            normalized.add(normalizedItem);
        }
        return normalized;
    }

    private String normalizeIdentInfo(String identInfo) {
        return defaultText(identInfo, "").trim();
    }

    private List<Evidence> normalizeEvidences(List<Evidence> evidences, int fallbackChunkIndex) {
        List<Evidence> normalized = new ArrayList<>();
        for (Evidence evidence : defaultList(evidences)) {
            if (evidence == null || ObjectUtils.isEmpty(evidence.getEvidenceText())) {
                continue;
            }
            Evidence normalizedEvidence = new Evidence();
            normalizedEvidence.setChunkIndex(evidence.getChunkIndex() > 0 ? evidence.getChunkIndex() : fallbackChunkIndex);
            normalizedEvidence.setChapterSeq(evidence.getChapterSeq());
            normalizedEvidence.setExtractReason(defaultText(evidence.getExtractReason(), ""));
            normalizedEvidence.setEvidenceText(limitEvidenceText(evidence.getEvidenceText()));
            normalized.add(normalizedEvidence);
        }
        sortEvidencesByChunkIndex(normalized);
        return normalized;
    }

    private void verifyDraftEvidences(AiOutInfoDraft draft, ChapterChunk chunk) {
        draft.setCharacters(filterDraftWorldObjEvidences(draft.getCharacters(), chunk));
        draft.setFactions(filterDraftWorldObjEvidences(draft.getFactions(), chunk));
        draft.setItems(filterDraftWorldObjEvidences(draft.getItems(), chunk));
    }

    private List<WorldObj> filterDraftWorldObjEvidences(List<WorldObj> objects, ChapterChunk chunk) {
        List<WorldObj> validObjects = new ArrayList<>();
        for (WorldObj object : defaultList(objects)) {
            if (object == null) {
                continue;
            }
            if (object.getEvidences() != null && !object.getEvidences().isEmpty()) {
                object.setEvidences(filterEvidencesByChunk(object.getEvidences(), chunk));
            }
            validObjects.add(object);
        }
        return validObjects;
    }

    private List<Evidence> filterEvidencesByChunk(List<Evidence> evidences, ChapterChunk chunk) {
        List<Evidence> valid = new ArrayList<>();
        for (Evidence evidence : defaultList(evidences)) {
            if (evidence == null) {
                continue;
            }
            String text = evidence.getEvidenceText();
            Chapter matchedChapter = findEvidenceChapter(text, chunk.getChapters());
            if (matchedChapter == null) {
                continue;
            }
            Evidence validEvidence = copyEvidence(evidence);
            validEvidence.setChunkIndex(chunk.getIndex());
            validEvidence.setChapterSeq(matchedChapter.getSeq());
            validEvidence.setEvidenceText(limitEvidenceText(text));
            valid.add(validEvidence);
        }
        sortEvidencesByChunkIndex(valid);
        return valid;
    }

    private Chapter findEvidenceChapter(String evidenceText, List<Chapter> chapters) {
        if (ObjectUtils.isEmpty(evidenceText)) {
            return null;
        }
        String text = stripQuote(evidenceText.trim());
        for (Chapter chapter : chapters) {
            if (chapter.getContent().contains(text)) {
                return chapter;
            }
        }
        return null;
    }

    private JSONObject buildRunResult(Path projectDir, WikiProject wikiProject, int parseChapterNum) {
        JSONObject result = new JSONObject();
        result.put("wikiProject", wikiProject);
        result.put("parseChapterNum", parseChapterNum);
        result.put("chunkSize", CHUNK_SIZE);
        result.put("projectDir", projectDir.toAbsolutePath().toString());
        return result;
    }

    private ChunkSummary fallbackChunkPlot(ChapterChunk chunk) {
        ChunkSummary result = new ChunkSummary();
        result.setChunkIndex(chunk.getIndex());
        result.setStartChapterSeq(chunk.getStartChapterSeq());
        result.setEndChapterSeq(chunk.getEndChapterSeq());
        result.setChunkSummary("AI未返回可解析JSON，暂用章节标题兜底：" + chunkTitleList(chunk));
        List<AiOutInfoPlotChapter> chapters = new ArrayList<>();
        for (Chapter chapter : chunk.getChapters()) {
            AiOutInfoPlotChapter item = new AiOutInfoPlotChapter();
            item.setChapterSeq(chapter.getSeq());
            item.setChapterSummary(limitChapterSummary(chapter.getTitle()));
            chapters.add(item);
        }
        result.setChapters(chapters);
        return result;
    }

    private AiOutInfoDraft fallbackChunkWikiDraft(ChapterChunk chunk) {
        AiOutInfoDraft draft = new AiOutInfoDraft();
        draft.setChunkIndex(chunk.getIndex());
        draft.setStartChapterSeq(chunk.getStartChapterSeq());
        draft.setEndChapterSeq(chunk.getEndChapterSeq());
        draft.setCharacters(new ArrayList<>());
        draft.setFactions(new ArrayList<>());
        draft.setItems(new ArrayList<>());
        return draft;
    }

    private AiOutInfoMergeReviewResult fallbackEntityMergeReview() {
        AiOutInfoMergeReviewResult review = new AiOutInfoMergeReviewResult();
        review.setMergeGroups(new ArrayList<>());
        return review;
    }

    private AiOutInfoRelationExtract fallbackCharacterRelationExtract() {
        AiOutInfoRelationExtract relationExtract = new AiOutInfoRelationExtract();
        relationExtract.setCharacterRelations(new ArrayList<>());
        return relationExtract;
    }

    private boolean isEmptyPlotOutput(AiOutInfoPlot plotOutput) {
        return plotOutput == null
            || (ObjectUtils.isEmpty(plotOutput.getChunkSummary())
            && (plotOutput.getChapters() == null || plotOutput.getChapters().isEmpty()));
    }

    private boolean isEmptyDraftOutput(AiOutInfoDraft draft) {
        return draft == null
            || (draft.getCharacters() == null
            && draft.getFactions() == null
            && draft.getItems() == null);
    }

    private List<Chapter> splitChapters(String novelText) {
        List<Chapter> chapters = new ArrayList<>();
        if (novelText == null || novelText.trim().isEmpty()) {
            return chapters;
        }

        Matcher matcher = NOVEL_TITLE_PATTERN.matcher(novelText);
        List<Integer> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<Boolean> chapterTitles = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            titles.add(matcher.group(1).trim());
            chapterTitles.add("章".equals(matcher.group(2)));
        }

        if (starts.isEmpty() || !chapterTitles.contains(Boolean.TRUE)) {
            chapters.add(new Chapter(1, "第1章", novelText.trim()));
            return chapters;
        }

        String prefix = novelText.substring(0, starts.get(0)).trim();
        StringBuilder pendingSectionContent = new StringBuilder();
        int sectionIndex = 0;
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : novelText.length();
            String content = novelText.substring(start, end).trim();
            if (content.isEmpty()) {
                continue;
            }

            if (!chapterTitles.get(i)) {
                sectionIndex++;
                if (pendingSectionContent.length() > 0) {
                    pendingSectionContent.append("\n\n");
                }
                pendingSectionContent.append(content);
                continue;
            }

            if (chapters.isEmpty() && !prefix.isEmpty()) {
                content = prefix + "\n\n" + content;
            }
            if (pendingSectionContent.length() > 0) {
                content = pendingSectionContent + "\n\n" + content;
                pendingSectionContent.setLength(0);
            }
            chapters.add(new Chapter(chapters.size() + 1, titles.get(i), content, sectionIndex));
        }
        return chapters;
    }

    private List<ChapterChunk> buildChunks(List<Chapter> chapters) {
        List<ChapterChunk> chunks = new ArrayList<>();
        List<Chapter> currentChunk = new ArrayList<>();
        Integer currentSectionIndex = null;
        for (Chapter chapter : chapters) {
            boolean sectionChanged = currentSectionIndex != null && currentSectionIndex != chapter.getSectionIndex();
            if (!currentChunk.isEmpty() && (currentChunk.size() >= CHUNK_SIZE || sectionChanged)) {
                addChapterChunk(chunks, currentChunk);
                currentChunk = new ArrayList<>();
            }
            currentChunk.add(chapter);
            currentSectionIndex = chapter.getSectionIndex();
        }
        if (!currentChunk.isEmpty()) {
            addChapterChunk(chunks, currentChunk);
        }
        return chunks;
    }

    private void addChapterChunk(List<ChapterChunk> chunks, List<Chapter> chunkChapters) {
        StringBuilder content = new StringBuilder();
        for (Chapter chapter : chunkChapters) {
            if (content.length() > 0) {
                content.append("\n\n");
            }
            content.append("【第").append(chapter.getSeq()).append("章 ").append(chapter.getTitle()).append("】\n");
            content.append(chapter.getContent());
        }
        int startChapterSeq = chunkChapters.get(0).getSeq();
        int endChapterSeq = chunkChapters.get(chunkChapters.size() - 1).getSeq();
        chunks.add(new ChapterChunk(chunks.size(), startChapterSeq, endChapterSeq, new ArrayList<>(chunkChapters), content.toString()));
    }

    private ChunkReadingMemory buildEmptyReadingMemory() {
        ChunkReadingMemory memory = new ChunkReadingMemory();
        memory.setLongTermMemory(new ArrayList<>());
        memory.setMidMemory(new ArrayList<>());
        memory.setRecentMemory(new ArrayList<>());
        return memory;
    }

    private ChunkReadingMemory copyMemorySlots(ChunkReadingMemory source) {
        ChunkReadingMemory copy = buildEmptyReadingMemory();
        if (source == null) {
            return copy;
        }
        copy.setChunkIndex(source.getChunkIndex());
        copy.setStartChapterSeq(source.getStartChapterSeq());
        copy.setEndChapterSeq(source.getEndChapterSeq());
        copy.setLongTermMemory(new ArrayList<>(defaultList(source.getLongTermMemory())));
        copy.setMidMemory(new ArrayList<>(defaultList(source.getMidMemory())));
        copy.setRecentMemory(new ArrayList<>(defaultList(source.getRecentMemory())));
        return copy;
    }

    private Evidence copyEvidence(Evidence source) {
        Evidence copy = new Evidence();
        copy.setChunkIndex(source.getChunkIndex());
        copy.setChapterSeq(source.getChapterSeq());
        copy.setExtractReason(defaultText(source.getExtractReason(), ""));
        copy.setEvidenceText(limitEvidenceText(source.getEvidenceText()));
        return copy;
    }

    private String askAiWithLog(Path aiLogDir,
                                int seq,
                                String logName,
                                String answerName,
                                String prompt,
                                TokenStatisticsAccumulator tokenStatisticsAccumulator,
                                boolean reuseAiLog) throws IOException {
        Path questionPath = aiLogDir.resolve(logName + "-q.txt");
        Path answerPath = aiLogDir.resolve(logName + "-a.txt");
        if (reuseAiLog && isNotEmptyFile(questionPath) && isNotEmptyFile(answerPath)) {
            String answer = readText(answerPath);
            validateAiJsonOrWriteError(answer, aiLogDir.resolve(logName + "-a-error.txt"), seq, answerName);
            log.info("txt2world reuse ai log, seq={}, answerName={}, answerPath={}", seq, answerName, answerPath);
            return answer;
        }

        writeText(questionPath, buildAiRequestLog(prompt));
        try {
            String answer = aiClient.chatJson(COMMON_SYSTEM_PROMPT, prompt, 16000);
            tokenStatisticsAccumulator.record(answerName, estimateInputTokens(COMMON_SYSTEM_PROMPT, prompt), estimateOutputTokens(answer));
            writeText(answerPath, answer);
            validateAiJsonOrWriteError(answer, aiLogDir.resolve(logName + "-a-error.txt"), seq, answerName);
            return answer;
        } catch (Exception e) {
            log.error("txt2world ai call failed, seq={}, answerName={}", seq, answerName, e);
            throw new IOException("txt2world ai call failed, seq=" + seq + ", answerName=" + answerName, e);
        }
    }

    private boolean isNotEmptyFile(Path path) throws IOException {
        return Files.isRegularFile(path) && Files.size(path) > 0;
    }

    private String readText(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private void validateAiJsonOrWriteError(String answer, Path errorPath, int seq, String answerName) throws IOException {
        try {
            JSONObject.parseObject(answer);
        } catch (Exception e) {
            writeText(errorPath, answer);
            throw new IOException("txt2world ai response is not valid JSON, seq=" + seq + ", answerName=" + answerName, e);
        }
    }

    private String buildAiRequestLog(String userPrompt) {
        return "【system prompt】\n"
            + COMMON_SYSTEM_PROMPT
            + "\n\n【user prompt】\n"
            + userPrompt;
    }
}
