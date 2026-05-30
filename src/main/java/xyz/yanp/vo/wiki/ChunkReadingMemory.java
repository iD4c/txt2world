package xyz.yanp.vo.wiki;

import lombok.Data;

import java.util.List;

@Data
public class ChunkReadingMemory {

    private int chunkIndex;
    private int startChapterSeq;
    private int endChapterSeq;
    private List<String> longTermMemory;
    private List<String> midMemory;
    private List<String> recentMemory;
}
