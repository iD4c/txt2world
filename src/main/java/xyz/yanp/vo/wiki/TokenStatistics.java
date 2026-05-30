package xyz.yanp.vo.wiki;

import lombok.Data;

@Data
public class TokenStatistics {

    private TokenStatisticsDtl chunkPlotReaderInDtl;
    private TokenStatisticsDtl chunkPlotReaderOutDtl;

    private TokenStatisticsDtl compactMemoryWorkerInDtl;
    private TokenStatisticsDtl compactMemoryWorkerOutDtl;

    private TokenStatisticsDtl chunkWikiDraftWorkerInDtl;
    private TokenStatisticsDtl chunkWikiDraftWorkerOutDtl;

    private TokenStatisticsDtl wikiEntityMergeReviewWorkerInDtl;
    private TokenStatisticsDtl wikiEntityMergeReviewWorkerOutDtl;

    private TokenStatisticsDtl characterRelationExtractWorkerInDtl;
    private TokenStatisticsDtl characterRelationExtractWorkerOutDtl;
}
