package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.PaperPosition;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.PaperPositionMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.service.IDailyActionService;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.ISignalService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 日终清单实现
 */
@Service
public class DailyActionServiceImpl implements IDailyActionService {

    @Resource
    private ISignalService signalService;

    @Resource
    private IPaperService paperService;

    @Resource
    private DailyActionMapper dailyActionMapper;

    @Resource
    private PaperPositionMapper paperPositionMapper;

    @Resource
    private WatchlistMapper watchlistMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DailyAction> run(LocalDate date) {
        LocalDate actionDate = Objects.nonNull(date) ? date : LocalDate.now();
        List<DailyAction> publishedActions = listByDate(actionDate);
        for (DailyAction publishedAction : publishedActions) {
            if (Objects.nonNull(publishedAction.getRunId())) {
                return publishedActions;
            }
        }
        dailyActionMapper.delete(Wrappers.<DailyAction>lambdaQuery().eq(DailyAction::getActionDate, actionDate));

        SignalRunReq req = new SignalRunReq();
        req.setUseUniverse(false);
        List<StrategySignalEntity> signals = signalService.run(req);

        Long accountId = paperService.defaultAccount().getId();
        List<PaperPosition> positions = paperPositionMapper.selectList(Wrappers.<PaperPosition>lambdaQuery()
                .eq(PaperPosition::getAccountId, accountId));
        Map<String, PaperPosition> posMap = new HashMap<>();
        for (PaperPosition position : positions) {
            posMap.put(position.getCode(), position);
        }

        Map<String, String> nameMap = new HashMap<>();
        for (StockBasic basic : stockBasicMapper.selectList(Wrappers.emptyWrapper())) {
            if (StringUtils.isNotBlank(basic.getName())) {
                nameMap.put(basic.getCode(), basic.getName());
            }
        }
        for (Watchlist watchlist : watchlistMapper.selectList(Wrappers.emptyWrapper())) {
            if (StringUtils.isNotBlank(watchlist.getName())) {
                nameMap.putIfAbsent(watchlist.getCode(), watchlist.getName());
            }
        }
        for (PaperPosition position : positions) {
            if (StringUtils.isNotBlank(position.getName())) {
                nameMap.putIfAbsent(position.getCode(), position.getName());
            }
        }

        List<DailyAction> actions = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (StrategySignalEntity signal : signals) {
            String action = signal.getSide();
            String exitRule = null;
            BigDecimal weight = null;
            if ("BUY".equalsIgnoreCase(action)) {
                weight = new BigDecimal("0.10");
                exitRule = exitRuleOf(signal.getStrategyId());
            } else if ("SELL".equalsIgnoreCase(action)) {
                if (!posMap.containsKey(signal.getCode())) {
                    continue;
                }
                exitRule = "信号卖出";
            } else {
                continue;
            }
            DailyAction row = DailyAction.builder()
                    .actionDate(actionDate)
                    .code(signal.getCode())
                    .name(nameMap.get(signal.getCode()))
                    .action(action)
                    .strategyId(signal.getStrategyId())
                    .reason(signal.getReasonJson())
                    .suggestedWeight(weight)
                    .exitRule(exitRule)
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            dailyActionMapper.insert(row);
            actions.add(row);
            covered.add(signal.getCode());
        }

        for (PaperPosition position : positions) {
            if (covered.contains(position.getCode())) {
                continue;
            }
            DailyAction hold = DailyAction.builder()
                    .actionDate(actionDate)
                    .code(position.getCode())
                    .name(nameMap.get(position.getCode()))
                    .action("HOLD")
                    .strategyId(null)
                    .reason("继续持有")
                    .suggestedWeight(null)
                    .exitRule("止损 " + position.getStopLoss() + " / 止盈 " + position.getTakeProfit())
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            dailyActionMapper.insert(hold);
            actions.add(hold);
        }

        if (CollUtil.isEmpty(actions)) {
            // 至少生成空结果可查询
            return listByDate(actionDate);
        }
        return actions;
    }

    @Override
    public List<DailyAction> listByDate(LocalDate date) {
        return dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .eq(DailyAction::getActionDate, date)
                .orderByAsc(DailyAction::getAction)
                .orderByAsc(DailyAction::getCode));
    }

    private String exitRuleOf(String strategyId) {
        if ("S1".equals(strategyId)) {
            return "跌破MA20离场";
        }
        if ("S2".equals(strategyId)) {
            return "RSI>70或跌破MA60离场";
        }
        if ("S3".equals(strategyId)) {
            return "跌破突破日低点离场";
        }
        return "按策略离场";
    }
}
