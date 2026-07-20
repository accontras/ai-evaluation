package io.github.accontra.eval.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.domain.model.EvalObjectLog;
import io.github.accontra.eval.infrastructure.mapper.EvalObjectLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * 奥运排名服务 — ADR-016。
 *
 * 规则: 同分并列 (1,1,3,4...)，rank_total 写入总参评数。
 */
@Component
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);
    private final EvalObjectLogMapper objectLogMapper;

    public RankingService(EvalObjectLogMapper objectLogMapper) {
        this.objectLogMapper = objectLogMapper;
    }

    /**
     * 对场景下所有 SUCCESS 对象按总分降序排名（奥运排名）。
     */
    public int rank(String sceneCode) {
        var qw = new LambdaQueryWrapper<EvalObjectLog>()
                .eq(EvalObjectLog::getSceneCode, sceneCode)
                .eq(EvalObjectLog::getStatus, "SUCCESS");
        var logs = objectLogMapper.selectList(qw);
        if (logs == null || logs.isEmpty()) {
            log.warn("[Ranking] No SUCCESS records for scene={}", sceneCode);
            return 0;
        }

        logs.sort(Comparator.comparing(
                o -> o.getTotalScore() != null ? o.getTotalScore() : BigDecimal.ZERO,
                Comparator.reverseOrder()));

        int rank = 1;
        int total = logs.size();
        BigDecimal prevScore = null;

        for (int i = 0; i < logs.size(); i++) {
            var logEntry = logs.get(i);
            BigDecimal curScore = logEntry.getTotalScore() != null ? logEntry.getTotalScore() : BigDecimal.ZERO;

            if (prevScore == null || curScore.compareTo(prevScore) != 0) {
                rank = i + 1;
            }
            logEntry.setEvalRank(rank);
            logEntry.setRankTotal(total);
            objectLogMapper.updateById(logEntry);

            log.debug("[Ranking] {}: score={}, rank={}/{}", logEntry.getTargetCode(), curScore, rank, total);
            prevScore = curScore;
        }

        log.info("[Ranking] scene={}, total={}, topScore={}", sceneCode, total, logs.get(0).getTotalScore());
        return total;
    }
}
