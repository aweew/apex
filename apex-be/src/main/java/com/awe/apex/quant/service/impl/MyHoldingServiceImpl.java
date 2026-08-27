package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.BarSyncReq;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.HoldingTradeReq;
import com.awe.apex.quant.domain.dto.MyHoldingSaveReq;
import com.awe.apex.quant.domain.dto.TechRegimeResult;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.entity.SectorBasic;
import com.awe.apex.quant.domain.entity.SectorConstituent;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockCompanyProfile;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.holding.ThemeBucketMatcher;
import com.awe.apex.quant.indicator.BenchmarkBarLoader;
import com.awe.apex.quant.indicator.RelativeStrengthUtils;
import com.awe.apex.quant.indicator.TechRegimeEvaluator;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.MyHoldingMapper;
import com.awe.apex.quant.mapper.SectorBasicMapper;
import com.awe.apex.quant.mapper.SectorConstituentMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockCompanyProfileMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.market.StockQuoteClient;
import com.awe.apex.quant.util.StockPinyinUtils;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.ICompanyProfileService;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IValuationService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
import java.util.LinkedHashSet;
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

    @Resource
    private ApexUserContext userContext;

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
    private TechRegimeEvaluator techRegimeEvaluator;

    @Resource
    private BenchmarkBarLoader benchmarkBarLoader;

    @Resource
    private ICompanyProfileService companyProfileService;

    @Resource
    private IBarDailyService barDailyService;

    @Lazy
    @Resource
    private IPortfolioService portfolioService;

    /**
     * 持仓列表（现价/盈亏/题材 + 技术指标/估值/评价建议）
     *
     * @return 列表
     */
    @Override
    public List<MyHolding> listHoldings() {
        List<MyHolding> list = myHoldingMapper.selectList(Wrappers.<MyHolding>lambdaQuery()
                .eq(MyHolding::getUserId, currentUserId())
                .orderByDesc(MyHolding::getUpdateTime)
                .orderByAsc(MyHolding::getCode));
        return enrichHoldings(list);
    }

    /**
     * 查询持仓及最新行情，不加载题材、技术和估值信息
     *
     * @return 轻量持仓列表
     */
    @Override
    public List<MyHolding> listHoldingsLite() {
        List<MyHolding> holdings = myHoldingMapper.selectList(Wrappers.<MyHolding>lambdaQuery()
                .eq(MyHolding::getUserId, currentUserId())
                .orderByDesc(MyHolding::getUpdateTime)
                .orderByAsc(MyHolding::getCode));
        Map<String, StockBasic> basicMap = loadBasics(holdings);
        for (MyHolding holding : holdings) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            fillPnlFromBasic(holding, basicMap.get(code));
        }
        return holdings;
    }

    /**
     * 查询持仓证券代码，不加载行情、技术和估值信息
     *
     * @return 持仓证券代码
     */
    @Override
    public List<String> listHoldingCodes() {
        List<MyHolding> holdings = myHoldingMapper.selectList(Wrappers.<MyHolding>lambdaQuery()
                .select(MyHolding::getCode)
                .eq(MyHolding::getUserId, currentUserId())
                .orderByAsc(MyHolding::getCode));
        Set<String> uniqueCodes = new LinkedHashSet<>();
        for (MyHolding holding : holdings) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isNotBlank(code)) {
                uniqueCodes.add(code);
            }
        }
        return new ArrayList<>(uniqueCodes);
    }

    /**
     * 对给定持仓行做行情/题材/技术/估值 enrich（不读写库）
     *
     * @param list 持仓行
     * @return 同一列表
     */
    @Override
    public List<MyHolding> enrichHoldings(List<MyHolding> list) {
        if (CollUtil.isEmpty(list)) {
            return list;
        }
        Map<String, StockBasic> basicMap = loadBasics(list);
        Map<String, StockCompanyProfile> profileMap = loadProfiles(list);
        ensureMissingProfiles(list, profileMap);
        Map<String, List<String>> sectorNamesByCode = loadConceptSectorNames(list);
        Map<String, List<BarDaily>> barsByCode = loadBarsGrouped(list);
        List<BarDaily> hs300Bars = benchmarkBarLoader.loadHs300Asc(80);
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
                holding.setPeDynamic(basic.getPeDynamic());
                holding.setPeStatic(basic.getPeStatic());
                holding.setPeTtm(basic.getPeTtm());
                if (StringUtils.isNotBlank(basic.getIndustry())) {
                    holding.setIndustry(basic.getIndustry());
                }
            }
            if (StringUtils.isBlank(holding.getIndustry())) {
                holding.setIndustry(MarketCodeUtils.isFundOrEtf(code) ? "ETF" : "未分类");
            }
            StockCompanyProfile profile = profileMap.get(code);
            // 强证据：行业/主营/名称；弱证据：东财概念串、本地概念板块名（易误挂「存储芯片」等）
            List<String> strongTexts = new ArrayList<>();
            List<String> weakTexts = new ArrayList<>();
            if (Objects.nonNull(profile) && StringUtils.isNotBlank(profile.getConcepts())) {
                holding.setConcepts(profile.getConcepts());
                weakTexts.addAll(ThemeBucketMatcher.splitConcepts(profile.getConcepts()));
            }
            if (Objects.nonNull(profile) && StringUtils.isNotBlank(profile.getMainBusiness())) {
                strongTexts.add(profile.getMainBusiness());
            }
            if (Objects.nonNull(profile) && StringUtils.isNotBlank(profile.getBoardPath())) {
                strongTexts.add(profile.getBoardPath());
            }
            weakTexts.addAll(sectorNamesByCode.getOrDefault(code, List.of()));
            if (StringUtils.isNotBlank(holding.getIndustry())) {
                strongTexts.add(holding.getIndustry());
            }
            if (StringUtils.isNotBlank(holding.getName())) {
                strongTexts.add(holding.getName());
            }
            appendHkNameHints(holding.getName(), strongTexts);
            holding.setThemeTags(ThemeBucketMatcher.match(strongTexts, weakTexts));
            fillPnl(holding);
            List<BarDaily> bars = barsByCode.get(code);
            BigDecimal rs20 = RelativeStrengthUtils.relativeStrengthPct(bars, hs300Bars, 20);
            BigDecimal rs60 = RelativeStrengthUtils.relativeStrengthPct(bars, hs300Bars, 60);
            fillInsight(holding, bars, valuationMap.get(code), rs20, rs60);
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
            if (Objects.nonNull(exist) && !currentUserId().equals(exist.getUserId())) {
                throw new BusinessException("无权访问该持仓");
            }
        }
        if (Objects.isNull(exist)) {
            exist = myHoldingMapper.selectOne(Wrappers.<MyHolding>lambdaQuery()
                    .eq(MyHolding::getCode, code)
                    .eq(MyHolding::getUserId, currentUserId())
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
            portfolioService.mirrorMyHoldingSave(exist, req.getTradePrice(), req.getTradeTime());
            return exist;
        }

        MyHolding created = MyHolding.builder()
                .userId(currentUserId())
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
        portfolioService.mirrorMyHoldingSave(created, req.getTradePrice(), req.getTradeTime());
        return created;
    }

    /**
     * 买入或卖出真实持仓。
     *
     * @param req 成交请求
     * @return 变更后的持仓，全部卖出时返回空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MyHolding tradeHolding(HoldingTradeReq req) {
        if (Objects.isNull(req)) {
            throw new BusinessException("成交请求不能为空");
        }
        String code = MarketCodeUtils.normalizeHoldingCode(req.getCode());
        String name = StringUtils.trim(req.getName());
        if (Objects.nonNull(req.getHoldingId())) {
            MyHolding holding = myHoldingMapper.selectById(req.getHoldingId());
            if (Objects.isNull(holding) || !currentUserId().equals(holding.getUserId())) {
                throw new BusinessException("无权访问该持仓");
            }
            code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            name = holding.getName();
        }

        HoldingTradeReq portfolioTradeReq = new HoldingTradeReq();
        portfolioTradeReq.setCode(code);
        portfolioTradeReq.setName(name);
        portfolioTradeReq.setSide(req.getSide());
        portfolioTradeReq.setQuantity(req.getQuantity());
        portfolioTradeReq.setTradePrice(req.getTradePrice());
        portfolioTradeReq.setTradeTime(req.getTradeTime());
        Portfolio defaultPortfolio = portfolioService.ensureDefaultPortfolio();
        PortfolioHolding portfolioHolding = portfolioService.tradeHolding(defaultPortfolio.getId(),
                portfolioTradeReq, PortfolioTradeSourceEnum.HOLDING_WEB);
        if (Objects.isNull(portfolioHolding)) {
            return null;
        }
        return myHoldingMapper.selectOne(Wrappers.<MyHolding>lambdaQuery()
                .eq(MyHolding::getUserId, currentUserId())
                .eq(MyHolding::getCode, code)
                .last("LIMIT 1"));
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
        MyHolding exist = myHoldingMapper.selectById(id);
        if (Objects.isNull(exist) || !currentUserId().equals(exist.getUserId())) {
            throw new BusinessException("无权访问该持仓");
        }
        myHoldingMapper.deleteById(id);
        if (Objects.nonNull(exist)) {
            portfolioService.mirrorMyHoldingRemove(exist.getCode());
        }
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
                .eq(MyHolding::getUserId, currentUserId())
                .orderByAsc(MyHolding::getCode));
        List<String> codes = new ArrayList<>();
        for (MyHolding holding : list) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isBlank(code)) {
                continue;
            }
            if (!code.equals(holding.getCode())) {
                holding.setCode(code);
                myHoldingMapper.updateById(holding);
            }
            codes.add(code);
        }
        Map<String, Object> quoteResult = refreshQuotesForCodes(codes, missingOnly);
        // 回填空白名称
        for (MyHolding holding : list) {
            if (StringUtils.isNotBlank(holding.getName()) || StringUtils.isBlank(holding.getCode())) {
                continue;
            }
            StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getCode, holding.getCode())
                    .last("LIMIT 1"));
            if (Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getName())) {
                holding.setName(basic.getName());
                myHoldingMapper.updateById(holding);
            }
        }
        int barOk = 0;
        int barFail = 0;
        int barCount = 0;
        if (CollUtil.isNotEmpty(codes)) {
            try {
                BarSyncReq syncReq = new BarSyncReq();
                syncReq.setCodes(codes);
                BarSyncResp barResp = barDailyService.syncBars(syncReq);
                barOk = Objects.nonNull(barResp.getSuccessCount()) ? barResp.getSuccessCount() : 0;
                barFail = Objects.nonNull(barResp.getFailCount()) ? barResp.getFailCount() : 0;
                barCount = Objects.nonNull(barResp.getBarCount()) ? barResp.getBarCount() : 0;
            } catch (Exception ex) {
                log.warn("持仓日线同步失败，异常={}", ex.getMessage());
                barFail = codes.size();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", quoteResult.get("success"));
        result.put("fail", quoteResult.get("fail"));
        result.put("barSuccess", barOk);
        result.put("barFail", barFail);
        result.put("barCount", barCount);
        result.put("holdings", listHoldings());
        result.put("message", quoteResult.get("message")
                + "；日线成功 " + barOk + " / 失败 " + barFail
                + "（写入 " + barCount + " 根）");
        return result;
    }

    /**
     * 按代码列表刷新行情到 stock_basic
     *
     * @param codes       代码
     * @param onlyMissing 是否只刷本地无现价的
     * @return 结果
     */
    @Override
    public Map<String, Object> refreshQuotesForCodes(List<String> codes, Boolean onlyMissing) {
        return refreshQuotesForCodes(codes, onlyMissing, false);
    }

    /**
     * 按代码列表仅刷新实时价、涨跌幅和行情时间。
     *
     * @param codes       代码
     * @param onlyMissing 是否只刷本地无现价的
     * @return 结果
     */
    @Override
    public Map<String, Object> refreshRealtimeQuotesForCodes(List<String> codes, Boolean onlyMissing) {
        return refreshQuotesForCodes(codes, onlyMissing, true);
    }

    private Map<String, Object> refreshQuotesForCodes(List<String> codes, Boolean onlyMissing,
                                                      boolean realtimeOnly) {
        boolean missingOnly = !Boolean.FALSE.equals(onlyMissing);
        int success = 0;
        int fail = 0;
        if (CollUtil.isEmpty(codes)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("success", 0);
            empty.put("fail", 0);
            empty.put("message", "无待刷新代码");
            return empty;
        }
        for (String raw : codes) {
            String code = MarketCodeUtils.normalizeHoldingCode(raw);
            if (StringUtils.isBlank(code)) {
                continue;
            }
            StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getCode, code)
                    .last("LIMIT 1"));
            if (missingOnly && Objects.nonNull(basic) && Objects.nonNull(basic.getLatestPrice())) {
                continue;
            }
            try {
                StockBasic synced = upsertQuote(code, realtimeOnly);
                if (Objects.nonNull(synced) && Objects.nonNull(synced.getLatestPrice())) {
                    success++;
                } else {
                    fail++;
                }
            } catch (Exception ex) {
                fail++;
                log.warn("刷新行情失败，证券代码={}，异常={}", code, ex.getMessage());
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
        result.put("message", "行情刷新完成：成功 " + success + " / 失败 " + fail);
        return result;
    }

    /**
     * 拉取行情并写入 stock_basic；拒绝 0 价覆盖，日线收盘兜底
     */
    private StockBasic upsertQuote(String code, boolean realtimeOnly) {
        StockBasic fetched = realtimeOnly
                ? stockQuoteClient.fetchRealtime(code)
                : stockQuoteClient.fetchBasic(code);
        fillPriceFromBarIfNeeded(fetched);
        fetched.setPinyinAbbr(StockPinyinUtils.buildAbbr(fetched.getName()));
        LocalDateTime now = LocalDateTime.now();
        StockBasic existing = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("LIMIT 1"));
        if (Objects.isNull(existing)) {
            if (Objects.isNull(fetched.getLatestPrice()) || fetched.getLatestPrice().signum() <= 0) {
                throw new BusinessException("未拿到有效现价: " + code);
            }
            if (MarketCodeUtils.isFundOrEtf(code) && StringUtils.isBlank(fetched.getIndustry())) {
                fetched.setIndustry("ETF");
            }
            fetched.setCreateTime(now);
            fetched.setUpdateTime(now);
            stockBasicMapper.insert(fetched);
            return fetched;
        }
        if (StringUtils.isNotBlank(fetched.getName())) {
            existing.setName(fetched.getName());
            existing.setPinyinAbbr(fetched.getPinyinAbbr());
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
        if (Objects.nonNull(fetched.getPeDynamic())) {
            existing.setPeDynamic(fetched.getPeDynamic());
        }
        if (Objects.nonNull(fetched.getPeStatic())) {
            existing.setPeStatic(fetched.getPeStatic());
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
        } else if (MarketCodeUtils.isFundOrEtf(code) && StringUtils.isBlank(existing.getIndustry())) {
            existing.setIndustry("ETF");
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
     * A 股持仓若缺少概念串，自动拉东财 F10 补齐（失败不阻断列表）
     *
     * @param list       持仓
     * @param profileMap 已加载概况（会就地回写）
     */
    private void ensureMissingProfiles(List<MyHolding> list, Map<String, StockCompanyProfile> profileMap) {
        for (MyHolding holding : list) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isBlank(code) || MarketCodeUtils.isHkCode(code)) {
                continue;
            }
            // ETF/场内基金无上市公司 F10，跳过概况补齐
            if (MarketCodeUtils.isFundOrEtf(code)) {
                continue;
            }
            StockCompanyProfile profile = profileMap.get(code);
            if (Objects.nonNull(profile) && StringUtils.isNotBlank(profile.getConcepts())) {
                continue;
            }
            try {
                companyProfileService.query(code, true);
                StockCompanyProfile refreshed = stockCompanyProfileMapper.selectOne(
                        Wrappers.<StockCompanyProfile>lambdaQuery()
                                .eq(StockCompanyProfile::getCode, code)
                                .last("LIMIT 1"));
                if (Objects.nonNull(refreshed)) {
                    profileMap.put(code, refreshed);
                }
            } catch (Exception e) {
                log.warn("持仓补齐公司概况失败，证券代码={}，异常={}", code, e.getMessage());
            }
        }
    }

    /**
     * 港股名称启发：无 F10 概念时补充可匹配文本
     *
     * @param name       证券名称
     * @param matchTexts 匹配文本列表
     */
    private void appendHkNameHints(String name, List<String> matchTexts) {
        if (StringUtils.isBlank(name)) {
            return;
        }
        if (name.contains("小米")) {
            matchTexts.add("人工智能");
            matchTexts.add("AI手机");
            matchTexts.add("消费电子概念");
        }
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
     * 填充技术结构、估值、评价与建议（状态机 + RS，雷达仅展示）
     */
    private void fillInsight(MyHolding holding, List<BarDaily> bars, ValuationBriefResp valuation,
                             BigDecimal rs20, BigDecimal rs60) {
        TechRegimeResult regime = techRegimeEvaluator.evaluate(bars, rs20, rs60);
        holding.setTechSignals(regime.getRadarSignals());
        holding.setTechHitCount(regime.getHitCount());
        holding.setTechTotal(regime.getTotal());
        holding.setTechSummary(regime.getSummary());

        if (Objects.nonNull(valuation)) {
            holding.setValuationLevel(valuation.getLevel());
            holding.setValuationLabel(valuation.getLevelLabel());
            holding.setValuationScore(valuation.getScore());
            holding.setValuationSummary(valuation.getSummary());
        }

        BigDecimal price = holding.getMarketPrice();
        String valLevel = Objects.nonNull(valuation) ? valuation.getLevel() : null;
        boolean overvalued = "OVERVALUED".equals(valLevel);
        boolean slightlyExpensive = "SLIGHTLY_EXPENSIVE".equals(valLevel);
        boolean rich = overvalued || slightlyExpensive;
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

        if (TechRegimeEvaluator.REGIME_INSUFFICIENT.equals(regime.getRegime())) {
            holding.setVerdict("数据不足");
            holding.setAdvice("日线不足，建议先同步行情后再评估");
            return;
        }

        String state = regime.getRegime();
        String rsTone = regime.getRsTone();
        boolean rsBear = TechRegimeEvaluator.RS_BEARISH.equals(rsTone);
        boolean rsBull = TechRegimeEvaluator.RS_BULLISH.equals(rsTone);

        if (TechRegimeEvaluator.REGIME_TREND_HOLD.equals(state)) {
            if (rsBear) {
                holding.setVerdict("持有不加仓");
                holding.setAdvice(overvalued
                        ? "不加仓；反弹但未创新高时减持1/3，收盘跌破止损 " + moneyText(holding.getStopLoss()) + " 全部卖出"
                        : "不加仓；收盘跌破止损 " + moneyText(holding.getStopLoss()) + " 全部卖出");
                return;
            }
            holding.setVerdict(cheap ? "持有可加仓" : "持有不加仓");
            holding.setAdvice(cheap
                    ? "仅在回踩不破20日线时分批加仓，收盘跌破止损 " + moneyText(holding.getStopLoss()) + " 卖出"
                    : "不追高加仓；收盘跌破止损 " + moneyText(holding.getStopLoss()) + " 全部卖出");
            return;
        }
        if (TechRegimeEvaluator.REGIME_PULLBACK_WATCH.equals(state)) {
            if (overvalued) {
                holding.setVerdict("反弹减仓");
                holding.setAdvice("反弹时减持1/3，不加仓；收盘跌破止损 " + moneyText(holding.getStopLoss()) + " 全部卖出");
            } else {
                holding.setVerdict("持有不加仓");
                holding.setAdvice("不加仓；回调不破20日线可继续持有，收盘跌破止损 "
                        + moneyText(holding.getStopLoss()) + " 全部卖出");
            }
            return;
        }
        if (TechRegimeEvaluator.REGIME_REPAIR.equals(state)) {
            holding.setVerdict("等待收复");
            holding.setAdvice("不加仓；收盘重新站上20日线后再评估，跌破止损 "
                    + moneyText(holding.getStopLoss()) + " 全部卖出");
            return;
        }
        if (TechRegimeEvaluator.REGIME_BREAKDOWN_CUT.equals(state)) {
            if (rich) {
                holding.setVerdict("反弹减仓");
                holding.setAdvice("反弹时减持1/2；收盘跌破止损 " + moneyText(holding.getStopLoss()) + " 全部卖出");
            } else {
                holding.setVerdict("止损观察");
                holding.setAdvice("不加仓；反弹未收复20日线时减持1/3，收盘跌破止损 "
                        + moneyText(holding.getStopLoss()) + " 全部卖出");
            }
            return;
        }
        // 中性震荡
        if (overvalued) {
            holding.setVerdict("反弹减仓");
            holding.setAdvice("反弹时减持1/3，不加仓；收盘跌破止损 " + moneyText(holding.getStopLoss()) + " 全部卖出");
        } else {
            holding.setVerdict("持有不加仓");
            holding.setAdvice("不加仓；收盘跌破止损 " + moneyText(holding.getStopLoss()) + " 全部卖出");
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
            holding.setPeDynamic(basic.getPeDynamic());
            holding.setPeStatic(basic.getPeStatic());
            holding.setPeTtm(basic.getPeTtm());
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

    private Long currentUserId() {
        return userContext.currentUserId();
    }
}
