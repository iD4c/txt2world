package xyz.yanp.vo.wiki;

import lombok.Data;

import java.util.List;

@Data
public class ChapterChunk {
    private final int index;
    private final int startChapterSeq;
    private final int endChapterSeq;
    private final List<Chapter> chapters;
    private final String content;

    public ChapterChunk(int index, int startChapterSeq, int endChapterSeq, List<Chapter> chapters, String content) {
        this.index = index;
        this.startChapterSeq = startChapterSeq;
        this.endChapterSeq = endChapterSeq;
        this.chapters = chapters;
        this.content = content;
    }
}
