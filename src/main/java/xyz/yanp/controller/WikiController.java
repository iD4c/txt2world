package xyz.yanp.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.yanp.entity.wiki.WikiProject;
import xyz.yanp.repository.base.SampleRepository;
import xyz.yanp.repository.wiki.WikiProjectRepository;
import xyz.yanp.service.wiki.TxtParseService;
import xyz.yanp.vo.R;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@RestController
@RequestMapping("/wiki")
public class WikiController {

    @Autowired
    private SampleRepository sampleRepository;

    @Autowired
    private WikiProjectRepository wikiProjectRepository;

    @Autowired
    private TxtParseService txtParseService;

    //    @Operation(value = "查询wiki项目")
    @PostMapping("/wikiProject/list")
    public R wikiProjectList(@RequestBody WikiProject wikiProject) {
        List<WikiProject> list = sampleRepository.sample(wikiProject);
        return R.okObject(list);
    }

    //    @Operation(value = "查询wiki项目")
    @PostMapping("/wikiProject/tableList")
    public R wikiProjectTableList(@RequestBody WikiProject wikiProject) {
        JSONObject res = new JSONObject();
        Long totalElements = sampleRepository.sampleCount(wikiProject);
        List<WikiProject> content = sampleRepository.sample(wikiProject);
        res.put("totalElements", totalElements);
        res.put("content", content);
        return R.okObject(res);
    }

    @PostMapping("/wikiProject/tables")
    public R wikiProjectTables(@RequestBody WikiProject wikiProject) {
        if (wikiProject == null || wikiProject.getId() == null) {
            return R.res2001("wiki项目id不能为空");
        }

        Optional<WikiProject> optional = wikiProjectRepository.findById(wikiProject.getId());
        if (!optional.isPresent()) {
            return R.res2001("wiki项目不存在");
        }

        Path tablesDir = resolveProjectDir(wikiProject.getId()).resolve("tables");
        if (!Files.isDirectory(tablesDir)) {
            return R.res2001("wiki项目结果文件不存在");
        }

        try {
            JSONObject data = new JSONObject();
            data.put("wikiProject", optional.get());
            data.put("chunkSummaryList", readJson(tablesDir.resolve("chunkSummary_list.json")));
            data.put("aiOutInfoDraftAlignedList", readJson(tablesDir.resolve("AiOutInfoDraft_aligned_list.json")));
            data.put("aiOutInfoRelationExtractList", readJson(tablesDir.resolve("AiOutInfoRelationExtract_list.json")));
            data.put("mergedWikiDataCharacters", readJson(tablesDir.resolve("MergedWikiData_characters.json")));
            data.put("mergedWikiDataItems", readJson(tablesDir.resolve("MergedWikiData_items.json")));
            data.put("mergedWikiDataFactions", readJson(tablesDir.resolve("MergedWikiData_factions.json")));
            return R.okObject(data);
        } catch (Exception e) {
            return R.res5000(e.getMessage());
        }
    }

//    @Operation(value = "保存wiki项目")
    @PostMapping("/wikiProject/save")
    @Transactional
    public R wikiProjectSave(@RequestBody WikiProject wikiProject) {
        wikiProjectRepository.save(wikiProject);
        return R.ok();
    }

//    @Operation(value = "删除wiki项目")
    @PostMapping("/wikiProject/dels")
    @Transactional
    public R wikiProjectDels(@RequestBody Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.res2001("wiki项目id不能为空");
        }
        List<WikiProject> deletingProjects = wikiProjectRepository.findByIdIn(ids);
        boolean hasParsingProject = deletingProjects.stream()
            .anyMatch(wikiProject -> "解析中".equals(wikiProject.getParseStatus()));
        if (hasParsingProject) {
            return R.res2001("解析中的wiki项目暂不能删除");
        }
        try {
            for (String id : ids) {
                deleteWikiProjectDiskFiles(id);
            }
        } catch (Exception e) {
            return R.res5000(e.getMessage());
        }
        wikiProjectRepository.deleteByIdIn(ids);
        return R.ok();
    }

//    @Operation(value = "删除wiki项目")
    @PostMapping("/wikiProject/delBySample")
    @Transactional
    public R wikiProjectDelBySample(@RequestBody WikiProject wikiProject) {
        List<WikiProject> list = sampleRepository.sample(wikiProject);
        wikiProjectRepository.deleteAll(list);
        return R.ok();
    }

//    @Operation(value = "删除全部wiki项目")
    @PostMapping("/wikiProject/delAll")
    @Transactional
    public R wikiProjectDelAll() {
        wikiProjectRepository.deleteAll();
        return R.ok();
    }

    @PostMapping("upload-and-parse")
    public R uploadAndParse(@RequestParam("file") MultipartFile file,
                            @RequestParam("projectName") String projectName) {
        try {
            JSONObject result = txtParseService.uploadAndParseAsync(file, projectName);
            return R.okObject(result);
        } catch (IllegalArgumentException e) {
            // 用户输入问题，例如未上传文件、项目名为空、txt 无有效章节。
            return R.res2001(e.getMessage());
        } catch (Exception e) {
            // AI 调用、文件读写、JSON 解析等非预期错误统一返回服务端错误。
            return R.res5000(e.getMessage());
        }
    }

    @PostMapping("/wikiProject/reparse")
    public R reparse(@RequestBody WikiProject wikiProject) {
        try {
            if (wikiProject == null || wikiProject.getId() == null) {
                return R.res2001("wiki项目id不能为空");
            }
            JSONObject result = txtParseService.reparseAsync(wikiProject.getId());
            return R.okObject(result);
        } catch (IllegalArgumentException e) {
            return R.res2001(e.getMessage());
        } catch (Exception e) {
            return R.res5000(e.getMessage());
        }
    }

    private Object readJson(Path path) throws Exception {
        if (!Files.exists(path)) {
            return null;
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return JSON.parse(text);
    }

    private void deleteWikiProjectDiskFiles(String wikiProjectId) throws Exception {
        if (wikiProjectId == null || wikiProjectId.trim().isEmpty()) {
            return;
        }
        Path projectDir = resolveProjectDir(wikiProjectId);
        if (!Files.exists(projectDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(projectDir)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        throw new IllegalStateException("删除wiki项目存盘文件失败：" + path, e);
                    }
                });
        }
    }

    private Path resolveProjectDir(String wikiProjectId) {
        return Paths.get(System.getProperty("user.dir"), "wiki-files", "wiki-" + wikiProjectId);
    }
}
