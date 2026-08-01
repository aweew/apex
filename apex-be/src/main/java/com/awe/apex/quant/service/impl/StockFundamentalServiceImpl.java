package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.dto.FinReportRowResp;
import com.awe.apex.quant.domain.dto.FinReportSheetResp;
import com.awe.apex.quant.domain.dto.StockFundamentalResp;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.domain.entity.StockFinReportItem;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.mapper.StockFinReportItemMapper;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.service.IStockFundamentalService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 个股基本面查询（只读本地库）
 */
@Service
public class StockFundamentalServiceImpl implements IStockFundamentalService {

    @Resource
    private StockFinAbstractMapper stockFinAbstractMapper;

    @Resource
    private StockFinIndicatorMapper stockFinIndicatorMapper;

    @Resource
    private StockFinReportItemMapper stockFinReportItemMapper;

    /**
     * 查询本地落库的基本面（摘要 / 指标 / 三大报表）
     *
     * @param code              证券代码
     * @param periodLimit       摘要与指标期数
     * @param reportPeriodLimit 报表展示期数
     * @return 基本面
     */
    @Override
    public StockFundamentalResp query(String code, Integer periodLimit, Integer reportPeriodLimit) {
        String pureCode = StringUtils.trimToEmpty(code);
        if (StringUtils.isBlank(pureCode)) {
            return StockFundamentalResp.builder()
                    .code(pureCode)
                    .note("证券代码为空")
                    .build();
        }

        int historyLimit = Objects.isNull(periodLimit) || periodLimit <= 0 ? 40 : Math.min(periodLimit, 120);
        int sheetPeriodLimit = Objects.isNull(reportPeriodLimit) || reportPeriodLimit <= 0
                ? 12 : Math.min(reportPeriodLimit, 40);

        List<StockFinAbstract> abstracts = stockFinAbstractMapper.selectList(
                new LambdaQueryWrapper<StockFinAbstract>()
                        .eq(StockFinAbstract::getCode, pureCode)
                        .orderByDesc(StockFinAbstract::getReportDate)
                        .last("LIMIT " + historyLimit)
        );
        List<StockFinIndicator> indicators = stockFinIndicatorMapper.selectList(
                new LambdaQueryWrapper<StockFinIndicator>()
                        .eq(StockFinIndicator::getCode, pureCode)
                        .orderByDesc(StockFinIndicator::getReportDate)
                        .last("LIMIT " + historyLimit)
        );

        Long reportCount = stockFinReportItemMapper.selectCount(
                new LambdaQueryWrapper<StockFinReportItem>().eq(StockFinReportItem::getCode, pureCode)
        );

        FinReportSheetResp profitSheet = buildSheet(pureCode, "profit", "利润表", sheetPeriodLimit);
        FinReportSheetResp balanceSheet = buildSheet(pureCode, "balance", "资产负债表", sheetPeriodLimit);
        FinReportSheetResp cashflowSheet = buildSheet(pureCode, "cashflow", "现金流量表", sheetPeriodLimit);

        String note;
        if (CollUtil.isEmpty(abstracts) && CollUtil.isEmpty(indicators) && (Objects.isNull(reportCount) || reportCount == 0)) {
            note = "本地暂无基本面，请先运行 scripts/market_data/sync_fundamentals.py";
        } else {
            note = "基本面来自本地库（AKShare 导入），不含实时估值";
        }

        return StockFundamentalResp.builder()
                .code(pureCode)
                .latestAbstract(CollUtil.isNotEmpty(abstracts) ? abstracts.get(0) : null)
                .abstracts(abstracts)
                .latestIndicator(CollUtil.isNotEmpty(indicators) ? indicators.get(0) : null)
                .indicators(indicators)
                .profitSheet(profitSheet)
                .balanceSheet(balanceSheet)
                .cashflowSheet(cashflowSheet)
                .abstractCount(abstracts.size())
                .indicatorCount(indicators.size())
                .reportItemCount(Objects.isNull(reportCount) ? 0 : reportCount.intValue())
                .note(note)
                .build();
    }

    /**
     * 组装单张报表透视表
     */
    private FinReportSheetResp buildSheet(String code, String statementType, String statementName, int periodLimit) {
        List<StockFinReportItem> items = stockFinReportItemMapper.selectList(
                new LambdaQueryWrapper<StockFinReportItem>()
                        .eq(StockFinReportItem::getCode, code)
                        .eq(StockFinReportItem::getStatementType, statementType)
                        .orderByDesc(StockFinReportItem::getReportDate)
                        .orderByAsc(StockFinReportItem::getItemName)
        );
        if (CollUtil.isEmpty(items)) {
            return FinReportSheetResp.builder()
                    .statementType(statementType)
                    .statementName(statementName)
                    .periods(List.of())
                    .rows(List.of())
                    .build();
        }

        LinkedHashSet<LocalDate> periodSet = new LinkedHashSet<>();
        for (StockFinReportItem item : items) {
            if (Objects.nonNull(item.getReportDate())) {
                periodSet.add(item.getReportDate());
            }
        }
        List<LocalDate> allPeriods = new ArrayList<>(periodSet);
        List<LocalDate> periods = allPeriods.size() > periodLimit
                ? allPeriods.subList(0, periodLimit)
                : allPeriods;

        LinkedHashMap<String, FinReportRowResp> rowMap = new LinkedHashMap<>();
        for (StockFinReportItem item : items) {
            if (Objects.isNull(item.getReportDate()) || !periods.contains(item.getReportDate())) {
                continue;
            }
            FinReportRowResp row = rowMap.get(item.getItemName());
            if (Objects.isNull(row)) {
                List<BigDecimal> values = new ArrayList<>();
                List<String> texts = new ArrayList<>();
                for (int i = 0; i < periods.size(); i++) {
                    values.add(null);
                    texts.add(null);
                }
                row = FinReportRowResp.builder()
                        .itemName(item.getItemName())
                        .values(values)
                        .texts(texts)
                        .build();
                rowMap.put(item.getItemName(), row);
            }
            int idx = periods.indexOf(item.getReportDate());
            if (idx >= 0) {
                row.getValues().set(idx, item.getItemValue());
                row.getTexts().set(idx, item.getItemValueText());
            }
        }

        return FinReportSheetResp.builder()
                .statementType(statementType)
                .statementName(statementName)
                .periods(periods)
                .rows(new ArrayList<>(rowMap.values()))
                .build();
    }
}
