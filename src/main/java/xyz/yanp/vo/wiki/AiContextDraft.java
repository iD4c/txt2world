package xyz.yanp.vo.wiki;

import lombok.Data;

@Data
public class AiContextDraft {

    private ChunkReadingMemory chunkReadingMemory;
    private String chunkSummary;
    private String nextChunkSummary;
}
