package xyz.yanp.vo.wiki;

import lombok.Data;

@Data
public class Evidence {

    private int chunkIndex;
    private int chapterSeq;
    private String extractReason;
    private String evidenceText;
}
