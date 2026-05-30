package xyz.yanp.util.wiki;

import xyz.yanp.vo.wiki.TokenStatistics;
import xyz.yanp.vo.wiki.TokenStatisticsDtl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TokenStatisticsAccumulator {

    private final List<Integer> chunkPlotReaderIn = new CopyOnWriteArrayList<>();
    private final List<Integer> chunkPlotReaderOut = new CopyOnWriteArrayList<>();
    private final List<Integer> compactMemoryWorkerIn = new CopyOnWriteArrayList<>();
    private final List<Integer> compactMemoryWorkerOut = new CopyOnWriteArrayList<>();
    private final List<Integer> chunkWikiDraftWorkerIn = new CopyOnWriteArrayList<>();
    private final List<Integer> chunkWikiDraftWorkerOut = new CopyOnWriteArrayList<>();
    private final List<Integer> wikiEntityMergeReviewWorkerIn = new CopyOnWriteArrayList<>();
    private final List<Integer> wikiEntityMergeReviewWorkerOut = new CopyOnWriteArrayList<>();
    private final List<Integer> characterRelationExtractWorkerIn = new CopyOnWriteArrayList<>();
    private final List<Integer> characterRelationExtractWorkerOut = new CopyOnWriteArrayList<>();

    public void record(String answerName, int inputTokens, int outputTokens) {
        if ("chunk-plot".equals(answerName)) {
            chunkPlotReaderIn.add(inputTokens);
            chunkPlotReaderOut.add(outputTokens);
            return;
        }
        if ("compact-memory".equals(answerName)) {
            compactMemoryWorkerIn.add(inputTokens);
            compactMemoryWorkerOut.add(outputTokens);
            return;
        }
        if ("chunk-draft".equals(answerName)) {
            chunkWikiDraftWorkerIn.add(inputTokens);
            chunkWikiDraftWorkerOut.add(outputTokens);
            return;
        }
        if (answerName.startsWith("entity-merge-review-")) {
            wikiEntityMergeReviewWorkerIn.add(inputTokens);
            wikiEntityMergeReviewWorkerOut.add(outputTokens);
            return;
        }
        if ("character-relation-extract".equals(answerName)) {
            characterRelationExtractWorkerIn.add(inputTokens);
            characterRelationExtractWorkerOut.add(outputTokens);
        }
    }

    public TokenStatistics build() {
        TokenStatistics statistics = new TokenStatistics();
        statistics.setChunkPlotReaderInDtl(buildDtl(chunkPlotReaderIn));
        statistics.setChunkPlotReaderOutDtl(buildDtl(chunkPlotReaderOut));
        statistics.setCompactMemoryWorkerInDtl(buildDtl(compactMemoryWorkerIn));
        statistics.setCompactMemoryWorkerOutDtl(buildDtl(compactMemoryWorkerOut));
        statistics.setChunkWikiDraftWorkerInDtl(buildDtl(chunkWikiDraftWorkerIn));
        statistics.setChunkWikiDraftWorkerOutDtl(buildDtl(chunkWikiDraftWorkerOut));
        statistics.setWikiEntityMergeReviewWorkerInDtl(buildDtl(wikiEntityMergeReviewWorkerIn));
        statistics.setWikiEntityMergeReviewWorkerOutDtl(buildDtl(wikiEntityMergeReviewWorkerOut));
        statistics.setCharacterRelationExtractWorkerInDtl(buildDtl(characterRelationExtractWorkerIn));
        statistics.setCharacterRelationExtractWorkerOutDtl(buildDtl(characterRelationExtractWorkerOut));
        return statistics;
    }

    private TokenStatisticsDtl buildDtl(List<Integer> values) {
        TokenStatisticsDtl dtl = new TokenStatisticsDtl();
        if (values == null || values.isEmpty()) {
            dtl.setMax(0);
            dtl.setMin(0);
            dtl.setAvg(0);
            dtl.setSd(0);
            return dtl;
        }
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        long sum = 0L;
        for (Integer value : values) {
            int current = value == null ? 0 : value;
            max = Math.max(max, current);
            min = Math.min(min, current);
            sum += current;
        }
        double avg = sum * 1.0 / values.size();
        double variance = 0D;
        for (Integer value : values) {
            int current = value == null ? 0 : value;
            variance += (current - avg) * (current - avg);
        }
        variance = variance / values.size();
        dtl.setMax(max);
        dtl.setMin(min);
        dtl.setAvg((int) Math.round(avg));
        dtl.setSd((int) Math.round(Math.sqrt(variance)));
        return dtl;
    }
}
