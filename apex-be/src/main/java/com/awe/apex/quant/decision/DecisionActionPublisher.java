package com.awe.apex.quant.decision;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
public class DecisionActionPublisher {

    @Resource
    private DailyActionMapper dailyActionMapper;

    @Resource
    private DecisionRunMapper decisionRunMapper;

    /**
     * 原子发布本次决策动作并切换正式运行
     *
     * @param run       决策运行
     * @param items     决策动作
     * @param dataLevel 数据质量等级
     * @param message   决策说明
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(DecisionRun run, List<DecisionItemResp> items,
                        String dataLevel, String message) {
        LocalDate actionDate = run.getActionDate();
        decisionRunMapper.selectList(Wrappers.<DecisionRun>lambdaQuery()
                .eq(DecisionRun::getActionDate, actionDate)
                .last("FOR UPDATE"));
        dailyActionMapper.delete(Wrappers.<DailyAction>lambdaQuery()
                .eq(DailyAction::getActionDate, actionDate));
        LocalDateTime now = LocalDateTime.now();
        int rank = 0;
        for (DecisionItemResp item : items) {
            DailyAction row = toAction(actionDate, run.getId(), ++rank, item, now);
            if (dailyActionMapper.insert(row) != 1) {
                throw new BusinessException("决策动作发布失败，code=" + item.getCode());
            }
            item.setId(row.getId());
        }
        decisionRunMapper.update(null, new UpdateWrapper<DecisionRun>()
                .eq("action_date", actionDate)
                .ne("id", run.getId())
                .eq("published", 1)
                .set("published", 0)
                .set("update_time", now));
        run.setDataLevel(dataLevel);
        run.setStatus("SUCCESS");
        run.setMessage(limit(message, 512));
        run.setFinishedAt(now);
        run.setPublished(1);
        run.setUpdateTime(now);
        if (decisionRunMapper.updateById(run) != 1) {
            throw new BusinessException("决策运行发布状态更新失败: " + run.getId());
        }
    }

    private DailyAction toAction(LocalDate actionDate, Long runId, int rank,
                                 DecisionItemResp item, LocalDateTime now) {
        return DailyAction.builder()
                .runId(runId)
                .rankNo(rank)
                .actionDate(actionDate)
                .code(item.getCode())
                .name(item.getName())
                .action(item.getAction())
                .strategyId(item.getStrategyId())
                .reason(limit(item.getReason(), 512))
                .suggestedWeight(item.getSuggestedWeight())
                .referencePrice(item.getReferencePrice())
                .stopLossPrice(item.getStopLossPrice())
                .takeProfitPrice(item.getTakeProfitPrice())
                .exitRule(item.getExitRule())
                .score(item.getScore())
                .confluenceCount(item.getConfluenceCount())
                .fundNote(item.getFundNote())
                .signalId(item.getSignalId())
                .mainlineMatch(booleanInt(item.getMainlineMatch()))
                .mainlineName(item.getMainlineName())
                .scoreExplain(limit(item.getScoreExplain(), 512))
                .strategiesCsv(csv(item.getStrategies(), 64))
                .valuationLevel(item.getValuationLevel())
                .valuationLabel(item.getValuationLabel())
                .valuationScore(item.getValuationScore())
                .valuationSummary(limit(item.getValuationSummary(), 256))
                .linkHint(limit(item.getLinkHint(), 64))
                .riskFlags(csv(item.getRiskFlags(), 256))
                .executableHint(booleanInt(item.getExecutableHint()))
                .decisionStatus("PUBLISHED")
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
    }

    private Integer booleanInt(Boolean value) {
        return Objects.isNull(value) ? null : (value ? 1 : 0);
    }

    private String csv(List<String> values, int maxLength) {
        if (CollUtil.isEmpty(values)) {
            return null;
        }
        return limit(String.join(",", values), maxLength);
    }

    private String limit(String value, int maxLength) {
        if (StringUtils.isBlank(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
