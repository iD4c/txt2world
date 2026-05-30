package xyz.yanp.vo.wiki;

import lombok.Data;

@Data
public class WorldObjCompressedChunkSummary {

    private Integer startChunkIndex;
    private Integer endChunkIndex;
    private String compressedSummary;
}
