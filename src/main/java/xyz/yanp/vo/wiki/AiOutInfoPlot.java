package xyz.yanp.vo.wiki;

import lombok.Data;

import java.util.List;

@Data
public class AiOutInfoPlot {

    private String chunkSummary;
    private List<AiOutInfoPlotChapter> chapters;
}
