package xyz.yanp.vo.wiki;

import lombok.Data;

@Data
public class CharacterRelation {

    private Integer chunkIndex;
    private Integer fromChapterSeq;
    private Integer toChapterSeq;
    private CharacterRelationSide characterA;
    private CharacterRelationSide characterB;
    private String relationSummary;
}
