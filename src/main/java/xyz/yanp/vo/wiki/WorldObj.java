package xyz.yanp.vo.wiki;

import lombok.Data;

import java.util.List;

@Data
public class WorldObj {

    private String name;
    private String wikiSectionType;
    private String importanceLevel;
    private String summary;
    private String lifeStatus;
    private List<Integer> appearanceChapterSeqs;
    private List<String> identInfos;
    private List<WorldObjChunkSummary> chunkSummaryList;
    private List<WorldObjAlias> aliases;
    private List<Evidence> evidences;
}
