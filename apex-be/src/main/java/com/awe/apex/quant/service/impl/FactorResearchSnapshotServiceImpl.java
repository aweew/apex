package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ResearchScoreComponentResp;
import com.awe.apex.quant.domain.dto.ResearchScoreResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.FactorResearchSnapshot;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.factor.FactorCalculator;
import com.awe.apex.quant.factor.ResearchScoreCalculator;
import com.awe.apex.quant.indicator.RelativeStrengthUtils;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.FactorResearchSnapshotMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.service.IFactorResearchSnapshotService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 横截面因子研究快照服务实现。
 */
@Slf4j
@Service
public class FactorResearchSnapshotServiceImpl implements IFactorResearchSnapshotService {

    private static final String MODEL_VERSION = "RESEARCH_V2";
    private static final int MINIMUM_INDUSTRY_SIZE = 5;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private StockFinAbstractMapper stockFinAbstractMapper;

    @Resource
    private StockFinIndicatorMapper stockFinIndicatorMapper;

    @Resource
    private FactorResearchSnapshotMapper factorResearchSnapshotMapper;

    @Resource
    private FactorCalculator factorCalculator;

    @Resource
    private ResearchScoreCalculator researchScoreCalculator;

    /**
     * 发布指定交易日的不可变研究快照。
     *
     * @param tradeDate 目标交易日
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(LocalDate tradeDate) {
        if (Objects.isNull(tradeDate)) {
            throw new BusinessException("因子研究快照缺少目标交易日");
        }
        Long existingCount = factorResearchSnapshotMapper.selectCount(Wrappers.<FactorResearchSnapshot>lambdaQuery()
                .eq(FactorResearchSnapshot::getTradeDate, tradeDate)
                .eq(FactorResearchSnapshot::getModelVersion, MODEL_VERSION));
        if (Objects.nonNull(existingCount) && existingCount > 0) {
            log.info("因子研究快照已发布，交易日={}，模型版本={}，样本数={}", tradeDate, MODEL_VERSION, existingCount);
            return;
        }

        List<StockBasic> securities = loadUniverse();
        List<BarDaily> researchBars = barDailyMapper.selectFactorResearchBars(tradeDate.minusDays(220), tradeDate);
        Map<String, List<BarDaily>> barsByCode = groupBars(researchBars);
        Map<String, StockFinAbstract> abstractsByCode = latestAbstracts(tradeDate);
        Map<String, StockFinIndicator> indicatorsByCode = latestIndicators(tradeDate);
        List<FactorResearchSnapshot> snapshots = buildSnapshots(tradeDate, securities, barsByCode,
                abstractsByCode, indicatorsByCode);
        BigDecimal barCoverage = coverage(snapshots.size(), securities.size());
        if (barCoverage.compareTo(new BigDecimal("80")) < 0) {
            throw new BusinessException("因子研究快照日线覆盖不足80%，当前覆盖率=" + barCoverage + "%");
        }
        calculatePercentiles(snapshots);
        LocalDateTime capturedAt = LocalDateTime.now();
        for (FactorResearchSnapshot snapshot : snapshots) {
            ResearchScoreResp researchScore = researchScoreCalculator.summarize(buildComponents(snapshot));
            snapshot.setResearchScore(researchScore.getScore());
            snapshot.setCoverage(researchScore.getCoverage());
            snapshot.setUniverseSize(snapshots.size());
            snapshot.setModelVersion(MODEL_VERSION);
            snapshot.setCapturedAt(capturedAt);
            factorResearchSnapshotMapper.insert(snapshot);
        }
        log.info("因子研究快照发布完成，交易日={}，模型版本={}，日线覆盖率={}%，样本数={}",
                tradeDate, MODEL_VERSION, barCoverage, snapshots.size());
    }

    /**
     * 查询证券最新研究快照。
     *
     * @param code 证券代码
     * @return 研究评分
     */
    @Override
    public ResearchScoreResp queryLatest(String code) {
        FactorResearchSnapshot snapshot = factorResearchSnapshotMapper.selectOne(
                Wrappers.<FactorResearchSnapshot>lambdaQuery()
                        .eq(FactorResearchSnapshot::getCode, code)
                        .eq(FactorResearchSnapshot::getModelVersion, MODEL_VERSION)
                        .orderByDesc(FactorResearchSnapshot::getTradeDate)
                        .last("limit 1"));
        if (Objects.isNull(snapshot)) {
            return ResearchScoreResp.builder()
                    .status("MISSING")
                    .model(MODEL_VERSION)
                    .confidence("LOW")
                    .coverage(BigDecimal.ZERO.setScale(2))
                    .components(List.of())
                    .reason("尚未发布该证券的横截面研究快照")
                    .build();
        }
        ResearchScoreResp researchScore = researchScoreCalculator.summarize(buildComponents(snapshot));
        researchScore.setAsOf(snapshot.getTradeDate());
        if (Objects.nonNull(snapshot.getResearchScore())) {
            researchScore.setScore(snapshot.getResearchScore());
        }
        return researchScore;
    }

    private List<StockBasic> loadUniverse() {
        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .isNotNull(StockBasic::getCode)
                .in(StockBasic::getMarket, List.of("SH", "SZ", "BJ")));
        List<StockBasic> securities = new ArrayList<>();
        for (StockBasic basic : basics) {
            if (Integer.valueOf(1).equals(basic.getStFlag())
                    || (StringUtils.isNotBlank(basic.getName()) && basic.getName().toUpperCase().contains("ST"))) {
                continue;
            }
            securities.add(basic);
        }
        if (CollUtil.isEmpty(securities)) {
            throw new BusinessException("因子研究快照无可用证券池");
        }
        return securities;
    }

    private Map<String, List<BarDaily>> groupBars(List<BarDaily> researchBars) {
        Map<String, List<BarDaily>> barsByCode = new HashMap<>();
        for (BarDaily bar : researchBars) {
            if (Objects.isNull(bar) || StringUtils.isBlank(bar.getCode())) {
                continue;
            }
            barsByCode.computeIfAbsent(bar.getCode(), key -> new ArrayList<>()).add(bar);
        }
        return barsByCode;
    }

    private Map<String, StockFinAbstract> latestAbstracts(LocalDate tradeDate) {
        Map<String, StockFinAbstract> abstractsByCode = new HashMap<>();
        for (StockFinAbstract financialAbstract : stockFinAbstractMapper.selectFactorResearchAbstracts(tradeDate)) {
            abstractsByCode.putIfAbsent(financialAbstract.getCode(), financialAbstract);
        }
        return abstractsByCode;
    }

    private Map<String, StockFinIndicator> latestIndicators(LocalDate tradeDate) {
        Map<String, StockFinIndicator> indicatorsByCode = new HashMap<>();
        for (StockFinIndicator financialIndicator : stockFinIndicatorMapper.selectFactorResearchIndicators(tradeDate)) {
            indicatorsByCode.putIfAbsent(financialIndicator.getCode(), financialIndicator);
        }
        return indicatorsByCode;
    }

    private List<FactorResearchSnapshot> buildSnapshots(LocalDate tradeDate, List<StockBasic> securities,
                                                         Map<String, List<BarDaily>> barsByCode,
                                                         Map<String, StockFinAbstract> abstractsByCode,
                                                         Map<String, StockFinIndicator> indicatorsByCode) {
        List<BarDaily> benchmarkBars = barsByCode.get("000300");
        if (CollUtil.isEmpty(benchmarkBars)) {
            throw new BusinessException("因子研究快照缺少沪深300日线");
        }
        List<FactorResearchSnapshot> snapshots = new ArrayList<>();
        for (StockBasic security : securities) {
            List<BarDaily> dailyBars = barsByCode.get(security.getCode());
            if (!hasLatestBar(dailyBars, tradeDate)) {
                continue;
            }
            StockFinIndicator indicator = indicatorsByCode.get(security.getCode());
            StockFinAbstract financialAbstract = abstractsByCode.get(security.getCode());
            BigDecimal roe = Objects.nonNull(indicator) ? indicator.getRoe()
                    : Objects.nonNull(financialAbstract) ? financialAbstract.getRoe() : null;
            BigDecimal growth = Objects.nonNull(financialAbstract) ? financialAbstract.getNetProfitYoy() : null;
            BigDecimal valuation = earningYield(security.getPeTtm());
            BigDecimal rs20 = RelativeStrengthUtils.relativeStrengthPct(dailyBars, benchmarkBars, 20);
            BigDecimal rs60 = RelativeStrengthUtils.relativeStrengthPct(dailyBars, benchmarkBars, 60);
            BigDecimal momentum = average(rs20, rs60);
            BigDecimal capital = factorCalculator.calculateAmountRatio(dailyBars, 20);
            snapshots.add(FactorResearchSnapshot.builder()
                    .tradeDate(tradeDate)
                    .code(security.getCode())
                    .industry(security.getIndustry())
                    .qualityRaw(roe)
                    .growthRaw(growth)
                    .valuationRaw(valuation)
                    .momentumRaw(momentum)
                    .capitalRaw(capital)
                    .build());
        }
        return snapshots;
    }

    private boolean hasLatestBar(List<BarDaily> dailyBars, LocalDate tradeDate) {
        return CollUtil.isNotEmpty(dailyBars)
                && Objects.nonNull(dailyBars.get(dailyBars.size() - 1).getTradeDate())
                && tradeDate.equals(dailyBars.get(dailyBars.size() - 1).getTradeDate());
    }

    private BigDecimal earningYield(BigDecimal peTtm) {
        if (Objects.isNull(peTtm) || peTtm.signum() <= 0) {
            return null;
        }
        return BigDecimal.ONE.divide(peTtm, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal average(BigDecimal leftValue, BigDecimal rightValue) {
        if (Objects.isNull(leftValue) && Objects.isNull(rightValue)) {
            return null;
        }
        if (Objects.isNull(leftValue)) {
            return rightValue;
        }
        if (Objects.isNull(rightValue)) {
            return leftValue;
        }
        return leftValue.add(rightValue).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal coverage(int availableCount, int totalCount) {
        if (totalCount <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(availableCount).multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
    }

    private void calculatePercentiles(List<FactorResearchSnapshot> snapshots) {
        Map<String, List<FactorResearchSnapshot>> industrySnapshots = new HashMap<>();
        for (FactorResearchSnapshot snapshot : snapshots) {
            if (StringUtils.isNotBlank(snapshot.getIndustry())) {
                industrySnapshots.computeIfAbsent(snapshot.getIndustry(), key -> new ArrayList<>()).add(snapshot);
            }
        }
        for (FactorResearchSnapshot snapshot : snapshots) {
            List<FactorResearchSnapshot> peers = industrySnapshots.get(snapshot.getIndustry());
            if (CollUtil.isEmpty(peers) || peers.size() < MINIMUM_INDUSTRY_SIZE) {
                peers = snapshots;
            }
            snapshot.setQualityPercentile(calculatePercentile(snapshot.getQualityRaw(), peers, "QUALITY"));
            snapshot.setGrowthPercentile(calculatePercentile(snapshot.getGrowthRaw(), peers, "GROWTH"));
            snapshot.setValuationPercentile(calculatePercentile(snapshot.getValuationRaw(), peers, "VALUATION"));
            snapshot.setMomentumPercentile(calculatePercentile(snapshot.getMomentumRaw(), peers, "MOMENTUM"));
            snapshot.setCapitalPercentile(calculatePercentile(snapshot.getCapitalRaw(), peers, "CAPITAL"));
        }
    }

    private BigDecimal calculatePercentile(BigDecimal value, List<FactorResearchSnapshot> peers, String factorKey) {
        List<BigDecimal> peerValues = new ArrayList<>();
        for (FactorResearchSnapshot peer : peers) {
            BigDecimal peerValue = rawValue(peer, factorKey);
            if (Objects.nonNull(peerValue)) {
                peerValues.add(peerValue);
            }
        }
        return researchScoreCalculator.calculatePercentile(value, peerValues);
    }

    private BigDecimal rawValue(FactorResearchSnapshot snapshot, String factorKey) {
        return switch (factorKey) {
            case "QUALITY" -> snapshot.getQualityRaw();
            case "GROWTH" -> snapshot.getGrowthRaw();
            case "VALUATION" -> snapshot.getValuationRaw();
            case "MOMENTUM" -> snapshot.getMomentumRaw();
            case "CAPITAL" -> snapshot.getCapitalRaw();
            default -> null;
        };
    }

    private List<ResearchScoreComponentResp> buildComponents(FactorResearchSnapshot snapshot) {
        List<ResearchScoreComponentResp> components = new ArrayList<>();
        components.add(component("QUALITY", "质量", 25, snapshot.getQualityRaw(), "%", snapshot.getQualityPercentile(),
                snapshot, "ROE，按行业优先、样本不足时按全市场比较"));
        components.add(component("MOMENTUM", "动量", 25, snapshot.getMomentumRaw(), "pp", snapshot.getMomentumPercentile(),
                snapshot, "RS20 与 RS60 相对沪深300的均值"));
        components.add(component("GROWTH", "成长", 20, snapshot.getGrowthRaw(), "%", snapshot.getGrowthPercentile(),
                snapshot, "最新财报净利润同比"));
        components.add(component("VALUATION", "估值", 15, snapshot.getValuationRaw(), "%", snapshot.getValuationPercentile(),
                snapshot, "盈利收益率，亏损或PE无效时缺失"));
        components.add(component("CAPITAL", "资金", 15, snapshot.getCapitalRaw(), "倍", snapshot.getCapitalPercentile(),
                snapshot, "最新成交额相对前20日均额"));
        return components;
    }

    private ResearchScoreComponentResp component(String key, String label, int weight, BigDecimal rawValue,
                                                 String unit, BigDecimal percentile, FactorResearchSnapshot snapshot,
                                                 String description) {
        return ResearchScoreComponentResp.builder()
                .key(key)
                .label(label)
                .weight(BigDecimal.valueOf(weight))
                .rawValue(rawValue)
                .unit(unit)
                .percentile(percentile)
                .available(Objects.nonNull(percentile))
                .asOf(snapshot.getTradeDate())
                .universeName(StringUtils.isNotBlank(snapshot.getIndustry()) ? snapshot.getIndustry() : "全市场")
                .universeSize(snapshot.getUniverseSize())
                .description(description)
                .build();
    }
}
