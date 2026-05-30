package xyz.yanp.vo.wiki;

import lombok.Data;

import java.util.List;

@Data
public class ChunkSummary {

    private Integer chunkIndex;
    private Integer startChapterSeq;
    private Integer endChapterSeq;
    private String chunkSummary;
    private List<AiOutInfoPlotChapter> chapters;
}
