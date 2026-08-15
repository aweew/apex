package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.dto.FinancialQualityResp;
import com.awe.apex.quant.domain.dto.FinReportRowResp;
import com.awe.apex.quant.domain.dto.FinReportSheetResp;
import com.awe.apex.quant.domain.dto.StockFundamentalResp;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.domain.entity.StockFinReportItem;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.mapper.StockFinReportItemMapper;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.service.IStockFundamentalService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static final String CASHFLOW_STATEMENT = "cashflow";
    private static final String BALANCE_STATEMENT = "balance";
    private static final String OPERATING_CASH_FLOW_ITEM = "经营活动产生的现金流量净额";
    private static final String CAPITAL_EXPENDITURE_ITEM = "购建固定资产、无形资产和其他长期资产支付的现金";
    private static final String ACCOUNTS_RECEIVABLE_ITEM = "应收账款";

    @Resource
    private StockFinAbstractMapper stockFinAbstractMapper;

    @Resource
    private StockFinIndicatorMapper stockFinIndicatorMapper;

    @Resource
    private StockFinReportItemMapper stockFinReportItemMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

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
        FinancialQualityResp financialQuality = buildFinancialQuality(pureCode, abstracts);

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
                .financialQuality(financialQuality)
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
     * 按同一报告期计算现金流质量与自由现金流估值
     *
     * @param code      证券代码
     * @param abstracts 财务摘要，按报告期倒序
     * @return 财务现金质量指标，无经营现金流时返回 null
     */
    private FinancialQualityResp buildFinancialQuality(String code, List<StockFinAbstract> abstracts) {
        if (CollUtil.isEmpty(abstracts)) {
            return null;
        }
        List<StockFinReportItem> reportItems = stockFinReportItemMapper.selectList(
                new LambdaQueryWrapper<StockFinReportItem>()
                        .eq(StockFinReportItem::getCode, code)
                        .orderByDesc(StockFinReportItem::getReportDate)
        );
        if (CollUtil.isEmpty(reportItems)) {
            return null;
        }
        StockBasic stockBasic = stockBasicMapper.selectOne(
                new LambdaQueryWrapper<StockBasic>().eq(StockBasic::getCode, code).last("LIMIT 1"));
        BigDecimal totalMarketValue = Objects.nonNull(stockBasic) ? stockBasic.getTotalMv() : null;

        for (StockFinAbstract abstractItem : abstracts) {
            LocalDate reportDate = abstractItem.getReportDate();
            BigDecimal operatingCashFlow = findReportValue(reportItems, CASHFLOW_STATEMENT, reportDate,
                    OPERATING_CASH_FLOW_ITEM);
            if (Objects.isNull(operatingCashFlow)) {
                continue;
            }
            BigDecimal capitalExpenditure = findReportValue(reportItems, CASHFLOW_STATEMENT, reportDate,
                    CAPITAL_EXPENDITURE_ITEM);
            if (Objects.nonNull(capitalExpenditure)) {
                capitalExpenditure = capitalExpenditure.abs();
            }
            BigDecimal freeCashFlow = Objects.nonNull(capitalExpenditure)
                    ? operatingCashFlow.subtract(capitalExpenditure) : null;
            BigDecimal netProfit = abstractItem.getNetProfit();
            BigDecimal cashConversionRatio = null;
            if (Objects.nonNull(netProfit) && netProfit.compareTo(BigDecimal.ZERO) > 0) {
                cashConversionRatio = operatingCashFlow.divide(netProfit, 2, RoundingMode.HALF_UP);
            }
            BigDecimal priceToFreeCashFlow = null;
            if (Objects.nonNull(totalMarketValue) && totalMarketValue.compareTo(BigDecimal.ZERO) > 0
                    && Objects.nonNull(freeCashFlow) && freeCashFlow.compareTo(BigDecimal.ZERO) > 0) {
                priceToFreeCashFlow = totalMarketValue.divide(freeCashFlow, 2, RoundingMode.HALF_UP);
            }
            return FinancialQualityResp.builder()
                    .reportDate(reportDate)
                    .netProfit(netProfit)
                    .operatingCashFlow(operatingCashFlow)
                    .accountsReceivable(findReportValue(reportItems, BALANCE_STATEMENT, reportDate,
                            ACCOUNTS_RECEIVABLE_ITEM))
                    .cashConversionRatio(cashConversionRatio)
                    .capitalExpenditure(capitalExpenditure)
                    .freeCashFlow(freeCashFlow)
                    .priceToFreeCashFlow(priceToFreeCashFlow)
                    .build();
        }
        return null;
    }

    /**
     * 查找报告期内的指定报表科目，优先精确匹配以排除相近科目
     */
    private BigDecimal findReportValue(List<StockFinReportItem> reportItems, String statementType,
                                       LocalDate reportDate, String itemName) {
        for (StockFinReportItem reportItem : reportItems) {
            if (statementType.equals(reportItem.getStatementType()) && reportDate.equals(reportItem.getReportDate())
                    && itemName.equals(reportItem.getItemName()) && Objects.nonNull(reportItem.getItemValue())) {
                return reportItem.getItemValue();
            }
        }
        for (StockFinReportItem reportItem : reportItems) {
            if (statementType.equals(reportItem.getStatementType()) && reportDate.equals(reportItem.getReportDate())
                    && StringUtils.isNotBlank(reportItem.getItemName()) && reportItem.getItemName().contains(itemName)
                    && Objects.nonNull(reportItem.getItemValue())) {
                return reportItem.getItemValue();
            }
        }
        return null;
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
