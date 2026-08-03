package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.MyHoldingSaveReq;
import com.awe.apex.quant.domain.dto.ObserveTechSignal;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.SectorBasic;
import com.awe.apex.quant.domain.entity.SectorConstituent;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockCompanyProfile;
import com.awe.apex.quant.holding.ThemeBucketMatcher;
import com.awe.apex.quant.indicator.TechSignalEvaluator;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.MyHoldingMapper;
import com.awe.apex.quant.mapper.SectorBasicMapper;
import com.awe.apex.quant.mapper.SectorConstituentMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockCompanyProfileMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.market.StockQuoteClient;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IValuationService;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 我的持仓服务实现
 */
@Slf4j
@Service
public class MyHoldingServiceImpl implements IMyHoldingService {

    private static final int TECH_LOOKBACK_DAYS = 120;

    @Resource
    private MyHoldingMapper myHoldingMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private StockCompanyProfileMapper stockCompanyProfileMapper;

    @Resource
    private SectorConstituentMapper sectorConstituentMapper;

    @Resource
    private SectorBasicMapper sectorBasicMapper;

    @Resource
    private StockQuoteClient stockQuoteClient;

    @Resource
    private IValuationService valuationService;

    @Resource
    private TechSignalEvaluator techSignalEvaluator;

    /**
     * 持仓列表（现价/盈亏/题材 + 技术指标/估值/评价建议）
     *
     * @return 列表
     */
    @Override
    public List<MyHolding> listHoldings() {
        List<MyHolding> list = myHoldingMapper.selectList(Wrappers.<MyHolding>lambdaQuery()
                .orderByDesc(MyHolding::getUpdateTime)
                .orderByAsc(MyHolding::getCode));
        if (CollUtil.isEmpty(list)) {
            return list;
        }
        Map<String, StockBasic> basicMap = loadBasics(list);
        Map<String, StockCompanyProfile> profileMap = loadProfiles(list);
        Map<String, List<String>> sectorNamesByCode = loadConceptSectorNames(list);
        Map<String, List<BarDaily>> barsByCode = loadBarsGrouped(list);
        List<String> valCodes = new ArrayList<>();
        for (MyHolding holding : list) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isNotBlank(code) && !valCodes.contains(code)) {
                valCodes.add(code);
            }
        }
        Map<String, ValuationBriefResp> valuationMap = valuationService.briefBatch(valCodes);

        for (MyHolding holding : list) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            StockBasic basic = basicMap.get(code);
            if (Objects.isNull(basic) && StringUtils.isNotBlank(holding.getCode())) {
                basic = basicMap.get(holding.getCode());
            }
            if (Objects.nonNull(basic)) {
                if (StringUtils.isBlank(holding.getName()) && StringUtils.isNotBlank(basic.getName())) {
                    holding.setName(basic.getName());
                }
                holding.setMarketPrice(basic.getLatestPrice());
                holding.setPctChg(basic.getPctChg());
                holding.setQuoteTime(basic.getQuoteTime());
                if (StringUtils.isNotBlank(basic.getIndustry())) {
                    holding.setIndustry(basic.getIndustry());
                }
            }
            if (StringUtils.isBlank(holding.getIndustry())) {
                holding.setIndustry("未分类");
            }
            StockCompanyProfile profile = profileMap.get(code);
            List<String> matchTexts = new ArrayList<>();
            if (Objects.nonNull(profile) && StringUtils.isNotBlank(profile.getConcepts())) {
                holding.setConcepts(profile.getConcepts());
                matchTexts.addAll(ThemeBucketMatcher.splitConcepts(profile.getConcepts()));
            }
            List<String> sectorNames = sectorNamesByCode.getOrDefault(code, List.of());
            matchTexts.addAll(sectorNames);
            if (StringUtils.isNotBlank(holding.getIndustry())) {
                matchTexts.add(holding.getIndustry());
            }
            if (StringUtils.isNotBlank(holding.getName())) {
                matchTexts.add(holding.getName());
            }
            holding.setThemeTags(ThemeBucketMatcher.match(matchTexts));
            fillPnl(holding);
            fillInsight(holding, barsByCode.get(code), valuationMap.get(code));
        }
        return list;
    }

    /**
     * 新增或更新持仓（同代码合并更新）
     *
     * @param req 请求
     * @return 持仓
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MyHolding save(MyHoldingSaveReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("证券代码不能为空");
        }
        String code = MarketCodeUtils.normalizeHoldingCode(req.getCode());
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("证券代码无效");
        }
        Integer quantity = Objects.nonNull(req.getQuantity()) ? req.getQuantity() : 0;
        if (quantity < 0) {
            throw new BusinessException("持仓数量不能为负");
        }

        String name = StringUtils.trim(req.getName());
        StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("LIMIT 1"));
        if (StringUtils.isBlank(name) && Objects.nonNull(basic)) {
            name = basic.getName();
        }

        LocalDateTime now = LocalDateTime.now();
        MyHolding exist = null;
        if (Objects.nonNull(req.getId())) {
            exist = myHoldingMapper.selectById(req.getId());
        }
        if (Objects.isNull(exist)) {
            exist = myHoldingMapper.selectOne(Wrappers.<MyHolding>lambdaQuery()
                    .eq(MyHolding::getCode, code)
                    .last("LIMIT 1"));
        }

        if (Objects.nonNull(exist)) {
            exist.setCode(code);
            exist.setName(name);
            exist.setQuantity(quantity);
            exist.setCostPrice(req.getCostPrice());
            exist.setStopLoss(req.getStopLoss());
            exist.setTakeProfit(req.getTakeProfit());
            exist.setNote(StringUtils.trim(req.getNote()));
            exist.setUpdateTime(now);
            myHoldingMapper.updateById(exist);
            fillPnlFromBasic(exist, basic);
            return exist;
        }

        MyHolding created = MyHolding.builder()
                .code(code)
                .name(name)
                .quantity(quantity)
                .costPrice(req.getCostPrice())
                .stopLoss(req.getStopLoss())
                .takeProfit(req.getTakeProfit())
                .note(StringUtils.trim(req.getNote()))
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        myHoldingMapper.insert(created);
        fillPnlFromBasic(created, basic);
        return created;
    }

    /**
     * 删除持仓
     *
     * @param id 主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (Objects.isNull(id)) {
            throw new BusinessException("持仓ID不能为空");
        }
        myHoldingMapper.deleteById(id);
    }

    /**
     * 刷新持仓行情（缺报价优先），并返回最新列表
     *
     * @param onlyMissing 是否只刷本地无现价的
     * @return 结果（含 holdings）
     */
    @Override
    public Map<String, Object> refreshQuotes(Boolean onlyMissing) {
        boolean missingOnly = !Boolean.FALSE.equals(onlyMissing);
        List<MyHolding> list = myHoldingMapper.selectList(Wrappers.<MyHolding>lambdaQuery()
                .orderByAsc(MyHolding::getCode));
        int success = 0;
        int fail = 0;
        for (MyHolding holding : list) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isBlank(code)) {
                continue;
            }
            if (!code.equals(holding.getCode())) {
                holding.setCode(code);
                myHoldingMapper.updateById(holding);
            }
            StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getCode, code)
                    .last("LIMIT 1"));
            if (missingOnly && Objects.nonNull(basic) && Objects.nonNull(basic.getLatestPrice())) {
                continue;
            }
            try {
                StockBasic synced = upsertQuote(code);
                if (Objects.nonNull(synced) && Objects.nonNull(synced.getLatestPrice())) {
                    success++;
                    if (StringUtils.isBlank(holding.getName()) && StringUtils.isNotBlank(synced.getName())) {
                        holding.setName(synced.getName());
                        myHoldingMapper.updateById(holding);
                    }
                } else {
                    fail++;
                }
            } catch (Exception ex) {
                fail++;
                log.warn("持仓刷新行情失败 code={}, err={}", code, ex.getMessage());
            }
            try {
                Thread.sleep(120L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("fail", fail);
        result.put("holdings", listHoldings());
        result.put("message", "行情刷新完成：成功 " + success + " / 失败 " + fail);
        return result;
    }

    /**
     * 拉取行情并写入 stock_basic；拒绝 0 价覆盖，日线收盘兜底
     */
    private StockBasic upsertQuote(String code) {
        StockBasic fetched = stockQuoteClient.fetchBasic(code);
        fillPriceFromBarIfNeeded(fetched);
        LocalDateTime now = LocalDateTime.now();
        StockBasic existing = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("LIMIT 1"));
        if (Objects.isNull(existing)) {
            if (Objects.isNull(fetched.getLatestPrice()) || fetched.getLatestPrice().signum() <= 0) {
                throw new BusinessException("未拿到有效现价: " + code);
            }
            fetched.setCreateTime(now);
            fetched.setUpdateTime(now);
            stockBasicMapper.insert(fetched);
            return fetched;
        }
        if (StringUtils.isNotBlank(fetched.getName())) {
            existing.setName(fetched.getName());
        }
        if (StringUtils.isNotBlank(fetched.getMarket())) {
            existing.setMarket(fetched.getMarket());
        }
        existing.setStFlag(fetched.getStFlag());
        // 仅写入有效现价，避免 0/空覆盖旧值
        if (Objects.nonNull(fetched.getLatestPrice()) && fetched.getLatestPrice().signum() > 0) {
            existing.setLatestPrice(fetched.getLatestPrice());
        }
        if (Objects.nonNull(fetched.getPctChg())) {
            existing.setPctChg(fetched.getPctChg());
        }
        if (Objects.nonNull(fetched.getPeTtm())) {
            existing.setPeTtm(fetched.getPeTtm());
        }
        if (Objects.nonNull(fetched.getPb())) {
            existing.setPb(fetched.getPb());
        }
        if (Objects.nonNull(fetched.getTotalMv())) {
            existing.setTotalMv(fetched.getTotalMv());
        }
        if (Objects.nonNull(fetched.getCircMv())) {
            existing.setCircMv(fetched.getCircMv());
        }
        if (StringUtils.isNotBlank(fetched.getIndustry())) {
            existing.setIndustry(fetched.getIndustry());
        }
        if (StringUtils.isNotBlank(fetched.getSource())) {
            existing.setSource(fetched.getSource());
        }
        existing.setQuoteTime(Objects.nonNull(fetched.getQuoteTime()) ? fetched.getQuoteTime() : now);
        existing.setUpdateTime(now);
        stockBasicMapper.updateById(existing);
        return existing;
    }

    /**
     * 实时价无效时，用本地最新日线收盘价 + 涨跌幅兜底
     */
    private void fillPriceFromBarIfNeeded(StockBasic basic) {
        if (Objects.isNull(basic) || StringUtils.isBlank(basic.getCode())) {
            return;
        }
        boolean priceOk = Objects.nonNull(basic.getLatestPrice()) && basic.getLatestPrice().signum() > 0;
        if (priceOk && Objects.nonNull(basic.getPctChg())) {
            return;
        }
        BarDaily bar = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, basic.getCode())
                .orderByDesc(BarDaily::getTradeDate)
                .last("LIMIT 1"));
        if (Objects.isNull(bar)) {
            return;
        }
        if (!priceOk && Objects.nonNull(bar.getClosePrice()) && bar.getClosePrice().signum() > 0) {
            basic.setLatestPrice(bar.getClosePrice());
        }
        if (Objects.isNull(basic.getPctChg()) && Objects.nonNull(bar.getPctChg())) {
            basic.setPctChg(bar.getPctChg());
        }
        if (StringUtils.isBlank(basic.getSource())) {
            basic.setSource("bar_daily");
        } else if (!basic.getSource().contains("bar_daily")) {
            basic.setSource(basic.getSource() + "+bar_daily");
        }
    }

    private Map<String, StockBasic> loadBasics(List<MyHolding> list) {
        Map<String, StockBasic> map = new HashMap<>();
        List<String> codes = new java.util.ArrayList<>();
        for (MyHolding holding : list) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isNotBlank(code) && !codes.contains(code)) {
                codes.add(code);
            }
        }
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, codes));
        for (StockBasic basic : basics) {
            map.put(basic.getCode(), basic);
            String normalized = MarketCodeUtils.normalizeHoldingCode(basic.getCode());
            if (StringUtils.isNotBlank(normalized)) {
                map.put(normalized, basic);
            }
        }
        return map;
    }

    /**
     * 公司概况（概念串）
     */
    private Map<String, StockCompanyProfile> loadProfiles(List<MyHolding> list) {
        Map<String, StockCompanyProfile> map = new HashMap<>();
        List<String> codes = holdingCodes(list);
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        List<StockCompanyProfile> profiles = stockCompanyProfileMapper.selectList(
                Wrappers.<StockCompanyProfile>lambdaQuery().in(StockCompanyProfile::getCode, codes));
        if (CollUtil.isEmpty(profiles)) {
            return map;
        }
        for (StockCompanyProfile profile : profiles) {
            String code = MarketCodeUtils.normalizeHoldingCode(profile.getCode());
            if (StringUtils.isNotBlank(code)) {
                map.put(code, profile);
            }
        }
        return map;
    }

    /**
     * 概念/题材板块名称（来自成分股表）
     */
    private Map<String, List<String>> loadConceptSectorNames(List<MyHolding> list) {
        Map<String, List<String>> result = new HashMap<>();
        List<String> codes = holdingCodes(list);
        if (CollUtil.isEmpty(codes)) {
            return result;
        }
        List<SectorConstituent> cons = sectorConstituentMapper.selectList(
                Wrappers.<SectorConstituent>lambdaQuery()
                        .in(SectorConstituent::getStockCode, codes)
                        .in(SectorConstituent::getBoardType, List.of("CONCEPT", "THEME")));
        if (CollUtil.isEmpty(cons)) {
            return result;
        }
        Set<String> sectorCodes = new HashSet<>();
        for (SectorConstituent row : cons) {
            if (StringUtils.isNotBlank(row.getSectorCode())) {
                sectorCodes.add(row.getSectorCode());
            }
        }
        Map<String, String> sectorNameByCode = new HashMap<>();
        if (CollUtil.isNotEmpty(sectorCodes)) {
            List<SectorBasic> sectors = sectorBasicMapper.selectList(
                    Wrappers.<SectorBasic>lambdaQuery().in(SectorBasic::getCode, sectorCodes));
            for (SectorBasic sector : sectors) {
                if (StringUtils.isNotBlank(sector.getCode()) && StringUtils.isNotBlank(sector.getName())) {
                    sectorNameByCode.put(sector.getCode(), sector.getName());
                }
            }
        }
        for (SectorConstituent row : cons) {
            String stock = MarketCodeUtils.normalizeHoldingCode(row.getStockCode());
            String sectorName = sectorNameByCode.get(row.getSectorCode());
            if (StringUtils.isBlank(stock) || StringUtils.isBlank(sectorName)) {
                continue;
            }
            List<String> names = result.computeIfAbsent(stock, k -> new ArrayList<>());
            if (!names.contains(sectorName)) {
                names.add(sectorName);
            }
        }
        return result;
    }

    private List<String> holdingCodes(List<MyHolding> list) {
        List<String> codes = new ArrayList<>();
        for (MyHolding holding : list) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isNotBlank(code) && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    /**
     * 批量加载日线（技术指标用）
     */
    private Map<String, List<BarDaily>> loadBarsGrouped(List<MyHolding> list) {
        Map<String, List<BarDaily>> map = new HashMap<>();
        List<String> codes = holdingCodes(list);
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        LocalDate begin = LocalDate.now().minusDays(TECH_LOOKBACK_DAYS);
        int batchSize = 40;
        for (int i = 0; i < codes.size(); i += batchSize) {
            List<String> batch = codes.subList(i, Math.min(i + batchSize, codes.size()));
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, batch)
                    .ge(BarDaily::getTradeDate, begin)
                    .orderByAsc(BarDaily::getCode)
                    .orderByAsc(BarDaily::getTradeDate));
            for (BarDaily bar : bars) {
                map.computeIfAbsent(bar.getCode(), k -> new ArrayList<>()).add(bar);
            }
        }
        return map;
    }

    /**
     * 填充技术指标、估值、评价与建议（持仓视角，技术按多头健康度）
     */
    private void fillInsight(MyHolding holding, List<BarDaily> bars, ValuationBriefResp valuation) {
        List<ObserveTechSignal> techSignals = techSignalEvaluator.evaluate("BUY", bars);
        int hit = 0;
        for (ObserveTechSignal signal : techSignals) {
            if (Boolean.TRUE.equals(signal.getHit())) {
                hit++;
            }
        }
        holding.setTechSignals(techSignals);
        holding.setTechHitCount(hit);
        holding.setTechTotal(techSignals.size());
        if (CollUtil.isEmpty(techSignals)) {
            holding.setTechSummary("日线不足");
        } else if (hit >= 5) {
            holding.setTechSummary("技术 " + hit + "/" + techSignals.size() + " · 偏强");
        } else if (hit >= 3) {
            holding.setTechSummary("技术 " + hit + "/" + techSignals.size() + " · 中性");
        } else {
            holding.setTechSummary("技术 " + hit + "/" + techSignals.size() + " · 偏弱");
        }

        if (Objects.nonNull(valuation)) {
            holding.setValuationLevel(valuation.getLevel());
            holding.setValuationLabel(valuation.getLevelLabel());
            holding.setValuationScore(valuation.getScore());
            holding.setValuationSummary(valuation.getSummary());
        }

        BigDecimal price = holding.getMarketPrice();
        String valLevel = Objects.nonNull(valuation) ? valuation.getLevel() : null;
        boolean rich = "OVERVALUED".equals(valLevel) || "SLIGHTLY_EXPENSIVE".equals(valLevel);
        boolean cheap = "UNDERVALUED".equals(valLevel) || "SLIGHTLY_CHEAP".equals(valLevel);

        if (Objects.nonNull(price) && Objects.nonNull(holding.getStopLoss())
                && holding.getStopLoss().signum() > 0
                && price.compareTo(holding.getStopLoss()) <= 0) {
            holding.setVerdict("止损卖出");
            holding.setAdvice("现价触及止损 " + moneyText(holding.getStopLoss()) + "，优先离场复盘");
            return;
        }
        if (Objects.nonNull(price) && Objects.nonNull(holding.getTakeProfit())
                && holding.getTakeProfit().signum() > 0
                && price.compareTo(holding.getTakeProfit()) >= 0) {
            holding.setVerdict("止盈减仓");
            holding.setAdvice("现价触及止盈 " + moneyText(holding.getTakeProfit()) + "，建议减仓锁定利润");
            return;
        }

        if (CollUtil.isEmpty(techSignals)) {
            holding.setVerdict("数据不足");
            holding.setAdvice("日线不足，建议先同步行情后再评估");
            return;
        }

        if (hit >= 5 && !rich) {
            holding.setVerdict(cheap ? "持有偏多" : "继续持有");
            holding.setAdvice(cheap
                    ? "技术偏强且估值不贵，可持有；回撤再评估加仓"
                    : "技术偏强，按止损纪律持有，勿追高加仓");
            return;
        }
        if (hit >= 3) {
            if (rich) {
                holding.setVerdict("谨慎持有");
                holding.setAdvice("估值偏贵，逢高减仓，止损勿放松");
            } else {
                holding.setVerdict("继续持有");
                holding.setAdvice("技术中性，持有观望，贴近止损管理");
            }
            return;
        }
        if (rich) {
            holding.setVerdict("逢高减仓");
            holding.setAdvice("技术偏弱且估值偏贵，优先减仓降风险");
        } else {
            holding.setVerdict("谨慎持有");
            holding.setAdvice("技术偏弱，收紧止损，反弹减仓或观察离场");
        }
    }

    private String moneyText(BigDecimal price) {
        if (Objects.isNull(price)) {
            return "-";
        }
        return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void fillPnlFromBasic(MyHolding holding, StockBasic basic) {
        if (Objects.nonNull(basic)) {
            holding.setMarketPrice(basic.getLatestPrice());
            holding.setPctChg(basic.getPctChg());
            holding.setQuoteTime(basic.getQuoteTime());
            if (StringUtils.isNotBlank(basic.getIndustry())) {
                holding.setIndustry(basic.getIndustry());
            }
        }
        fillPnl(holding);
    }

    private void fillPnl(MyHolding holding) {
        BigDecimal price = holding.getMarketPrice();
        Integer qty = holding.getQuantity();
        if (Objects.isNull(price) || price.signum() <= 0 || Objects.isNull(qty) || qty <= 0) {
            holding.setMarketValue(null);
            holding.setPnl(null);
            holding.setPnlPct(null);
            holding.setTodayPnl(null);
            return;
        }
        BigDecimal marketValue = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        holding.setMarketValue(marketValue);

        // 今日盈亏 = 市值 × 涨跌幅% / (100 + 涨跌幅%) = (现价 - 昨收) × 数量
        BigDecimal pct = holding.getPctChg();
        if (Objects.isNull(pct)) {
            holding.setTodayPnl(null);
        } else {
            BigDecimal denom = BigDecimal.valueOf(100).add(pct);
            if (denom.signum() == 0) {
                holding.setTodayPnl(null);
            } else {
                holding.setTodayPnl(marketValue.multiply(pct)
                        .divide(denom, 2, RoundingMode.HALF_UP));
            }
        }

        if (Objects.isNull(holding.getCostPrice()) || holding.getCostPrice().signum() <= 0) {
            holding.setPnl(null);
            holding.setPnlPct(null);
            return;
        }
        BigDecimal cost = holding.getCostPrice().multiply(BigDecimal.valueOf(qty));
        BigDecimal pnl = marketValue.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        holding.setPnl(pnl);
        holding.setPnlPct(pnl.divide(cost, 4, RoundingMode.HALF_UP));
    }
}
