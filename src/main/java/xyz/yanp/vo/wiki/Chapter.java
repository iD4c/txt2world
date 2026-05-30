package xyz.yanp.vo.wiki;

import lombok.Data;

@Data
public class Chapter {
    private final int seq;
    private final String title;
    private final String content;
    private final int sectionIndex;

    public Chapter(int seq, String title, String content) {
        this(seq, title, content, 0);
    }

    public Chapter(int seq, String title, String content, int sectionIndex) {
        this.seq = seq;
        this.title = title;
        this.content = content;
        this.sectionIndex = sectionIndex;
    }
}
