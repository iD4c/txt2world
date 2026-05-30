package xyz.yanp.vo.wiki;

import lombok.Data;

import java.util.List;

@Data
public class AiOutInfoDraft {

    private Integer chunkIndex;
    private Integer startChapterSeq;
    private Integer endChapterSeq;
    private List<WorldObj> characters;
    private List<WorldObj> factions;
    private List<WorldObj> items;
}
