package io.github.accontra.eval;

import io.github.accontra.eval.application.service.RankingService;
import io.github.accontra.eval.domain.model.EvalObjectLog;
import io.github.accontra.eval.infrastructure.mapper.EvalObjectLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S27: 奥运排名集成测试
 */
@SpringBootTest
class RankingServiceTest {

    @Autowired private RankingService rankingService;
    @Autowired private EvalObjectLogMapper objectLogMapper;

    @Test
    void shouldRankWithOlympicStyle() {
        // H2 data-only: insert test records, rank, verify, clean up
        // Use existing scene data for ranking
        int count = rankingService.rank("LOGISTICS-2026Q2");
        assertThat(count).isGreaterThan(0);

        // Verify ranking is consistent
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalObjectLog>()
                .eq(EvalObjectLog::getSceneCode, "LOGISTICS-2026Q2")
                .eq(EvalObjectLog::getStatus, "SUCCESS")
                .orderByAsc(EvalObjectLog::getEvalRank);
        var ranked = objectLogMapper.selectList(qw);

        assertThat(ranked).isNotEmpty();
        // Verify rank order: ascending rank
        int prevRank = 0;
        BigDecimal prevScore = BigDecimal.valueOf(999);
        for (var r : ranked) {
            assertThat(r.getEvalRank()).isNotNull();
            assertThat(r.getRankTotal()).isEqualTo(count);
            // Score should be non-increasing
            var score = r.getTotalScore() != null ? r.getTotalScore() : BigDecimal.ZERO;
            assertThat(score).isLessThanOrEqualTo(prevScore);
            // Olympic: same score = same rank
            if (score.compareTo(prevScore) < 0) {
                assertThat(r.getEvalRank()).isGreaterThan(prevRank);
            }
            prevRank = r.getEvalRank();
            prevScore = score;
        }
    }
}
