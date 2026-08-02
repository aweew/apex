package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.AtrStopItem;
import com.awe.apex.quant.domain.dto.AtrStopResp;
import com.awe.apex.quant.domain.dto.BetaTargetResp;
import com.awe.apex.quant.domain.dto.CorrelationMatrixResp;
import com.awe.apex.quant.domain.dto.EquityPointResp;
import com.awe.apex.quant.domain.dto.EquityQualityResp;
import com.awe.apex.quant.domain.dto.FactorExposureItem;
import com.awe.apex.quant.domain.dto.FactorExposureResp;
import com.awe.apex.quant.domain.dto.FillQualityItem;
import com.awe.apex.quant.domain.dto.FillQualityResp;
import com.awe.apex.quant.domain.dto.GapRiskItem;
import com.awe.apex.quant.domain.dto.GapRiskResp;
import com.awe.apex.quant.domain.dto.HoldBucketItem;
import com.awe.apex.quant.domain.dto.HoldBucketResp;
import com.awe.apex.quant.domain.dto.IndustryPnlResp;
import com.awe.apex.quant.domain.dto.JournalCreateReq;
import com.awe.apex.quant.domain.dto.KellySuggestResp;
import com.awe.apex.quant.domain.dto.MonteCarloResp;
import com.awe.apex.quant.domain.dto.MonthlyReturnResp;
import com.awe.apex.quant.domain.dto.PaperCostResp;
import com.awe.apex.quant.domain.dto.PaperExposureResp;
import com.awe.apex.quant.domain.dto.PaperHealthResp;
import com.awe.apex.quant.domain.dto.PaperOpenReq;
import com.awe.apex.quant.domain.dto.PaperOrderReq;
import com.awe.apex.quant.domain.dto.PaperPerformanceResp;
import com.awe.apex.quant.domain.dto.PositionStopsReq;
import com.awe.apex.quant.domain.dto.PositionSuggestResp;
import com.awe.apex.quant.domain.dto.PositionWeightResp;
import com.awe.apex.quant.domain.dto.RebalanceOrderSuggest;
import com.awe.apex.quant.domain.dto.RebalanceSuggestResp;
import com.awe.apex.quant.domain.dto.ReturnHistItem;
import com.awe.apex.quant.domain.dto.ReturnHistResp;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.dto.StopCoverageResp;
import com.awe.apex.quant.domain.dto.TradeCalendarDay;
import com.awe.apex.quant.domain.dto.TradeCalendarResp;
import com.awe.apex.quant.domain.dto.VolTargetResp;
import com.awe.apex.quant.domain.dto.WeekdayPnlItem;
import com.awe.apex.quant.domain.dto.WeekdayPnlResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.PaperOrder;
import com.awe.apex.quant.domain.entity.PaperPosition;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.PaperAccountMapper;
import com.awe.apex.quant.mapper.PaperOrderMapper;
import com.awe.apex.quant.mapper.PaperPositionMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StrategySignalMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.market.StockQuoteClient;
import com.awe.apex.quant.paper.PaperEquityCalculator;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IJournalService;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.IRiskService;
import com.awe.apex.quant.service.IUniverseService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * 模拟盘实现
 */
@Service
public class PaperServiceImpl implements IPaperService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private PaperAccountMapper paperAccountMapper;

    @Resource
    private PaperPositionMapper paperPositionMapper;

    @Resource
    private PaperOrderMapper paperOrderMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private IRiskService riskService;

    @Resource
    private IConfigService configService;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private WatchlistMapper watchlistMapper;

    @Resource
    private StockQuoteClient stockQuoteClient;

    @Resource
    private StrategySignalMapper strategySignalMapper;

    @Resource
    private IJournalService journalService;

    @Resource
    private IUniverseService universeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperAccount openOrDeposit(PaperOpenReq req) {
        PaperAccount account = paperAccountMapper.selectOne(Wrappers.<PaperAccount>lambdaQuery()
                .eq(PaperAccount::getAccountName, req.getAccountName())
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(account)) {
            account = PaperAccount.builder()
                    .accountName(req.getAccountName())
                    .cash(req.getCash())
                    .initCash(req.getCash())
                    .status("ACTIVE")
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            paperAccountMapper.insert(account);
            return account;
        }
        account.setCash(account.getCash().add(req.getCash()));
        account.setUpdateTime(now);
        paperAccountMapper.updateById(account);
        return account;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperAccount defaultAccount() {
        PaperAccount account = paperAccountMapper.selectOne(Wrappers.<PaperAccount>lambdaQuery()
                .eq(PaperAccount::getAccountName, "default")
                .last("limit 1"));
        if (Objects.nonNull(account)) {
            return account;
        }
        PaperOpenReq req = new PaperOpenReq();
        req.setAccountName("default");
        req.setCash(new BigDecimal("1000000"));
        return openOrDeposit(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperOrder placeOrder(PaperOrderReq req) {
        PaperAccount account = paperAccountMapper.selectById(req.getAccountId());
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        String code = MarketCodeUtils.normalizeCode(req.getCode());
        String side = req.getSide().toUpperCase();
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            throw new BusinessException("side 仅支持 BUY/SELL");
        }

        BarDaily bar = resolveBar(code, req.getTradeDate());
        BigDecimal price = resolveFillPrice(bar, side);
        int quantity = resolveQuantity(req, account, side, code, price);
        if (quantity <= 0 || quantity % 100 != 0) {
            throw new BusinessException("数量须为 100 的整数倍");
        }
        riskService.checkBeforeOrder(account.getId(), code, side, quantity, price);

        BigDecimal commission = configService.getDecimal("commission_rate", new BigDecimal("0.0005"));
        BigDecimal stamp = configService.getDecimal("stamp_tax_rate", new BigDecimal("0.0005"));
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = amount.multiply(commission);
        if ("SELL".equals(side)) {
            fee = fee.add(amount.multiply(stamp));
        }

        LocalDateTime now = LocalDateTime.now();
        if ("BUY".equals(side)) {
            BigDecimal cost = amount.add(fee);
            if (account.getCash().compareTo(cost) < 0) {
                throw new BusinessException("资金不足");
            }
            account.setCash(account.getCash().subtract(cost));
            account.setUpdateTime(now);
            paperAccountMapper.updateById(account);
            upsertBuyPosition(account.getId(), code, quantity, price, now);
        } else {
            PaperPosition position = paperPositionMapper.selectOne(Wrappers.<PaperPosition>lambdaQuery()
                    .eq(PaperPosition::getAccountId, account.getId())
                    .eq(PaperPosition::getCode, code)
                    .last("limit 1"));
            if (Objects.isNull(position) || position.getQuantity() < quantity) {
                throw new BusinessException("持仓不足");
            }
            account.setCash(account.getCash().add(amount).subtract(fee));
            account.setUpdateTime(now);
            paperAccountMapper.updateById(account);
            int left = position.getQuantity() - quantity;
            // 清仓时保留行（qty=0），避免软删除后唯一键冲突导致无法再买入
            position.setQuantity(Math.max(left, 0));
            position.setUpdateTime(now);
            paperPositionMapper.updateById(position);
        }

        String reason = Objects.nonNull(req.getTargetWeight())
                ? "目标仓位 " + req.getTargetWeight() + " 收盘价撮合"
                : "收盘价撮合";
        PaperOrder order = PaperOrder.builder()
                .accountId(account.getId())
                .code(code)
                .side(side)
                .quantity(quantity)
                .price(price)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .fee(fee.setScale(4, RoundingMode.HALF_UP))
                .tradeDate(bar.getTradeDate())
                .status("FILLED")
                .reason(reason)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        paperOrderMapper.insert(order);
        // 同步写入交易日记，便于复盘
        try {
            JournalCreateReq journalReq = new JournalCreateReq();
            journalReq.setTradeDate(order.getTradeDate().toString());
            journalReq.setCode(order.getCode());
            journalReq.setSide(order.getSide());
            journalReq.setPrice(order.getPrice());
            journalReq.setQuantity(order.getQuantity());
            journalReq.setNote("纸面成交#" + order.getId() + " " + (Objects.nonNull(order.getReason()) ? order.getReason() : ""));
            journalService.create(journalReq);
        } catch (Exception ignored) {
            // 日记失败不影响成交
        }
        return order;
    }

    /**
     * 更新止损止盈
     *
     * @param req 请求
     * @return 持仓
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperPosition updateStops(PositionStopsReq req) {
        String code = MarketCodeUtils.normalizeCode(req.getCode());
        PaperPosition position = paperPositionMapper.selectOne(Wrappers.<PaperPosition>lambdaQuery()
                .eq(PaperPosition::getAccountId, req.getAccountId())
                .eq(PaperPosition::getCode, code)
                .gt(PaperPosition::getQuantity, 0)
                .last("limit 1"));
        if (Objects.isNull(position)) {
            throw new BusinessException("无持仓: " + code);
        }
        if (Objects.nonNull(req.getStopLoss())) {
            position.setStopLoss(req.getStopLoss());
        }
        if (Objects.nonNull(req.getTakeProfit())) {
            position.setTakeProfit(req.getTakeProfit());
        }
        position.setUpdateTime(LocalDateTime.now());
        paperPositionMapper.updateById(position);
        List<PaperPosition> marked = listPositions(req.getAccountId());
        for (PaperPosition row : marked) {
            if (code.equals(row.getCode())) {
                return row;
            }
        }
        return position;
    }

    private int resolveQuantity(PaperOrderReq req, PaperAccount account, String side, String code, BigDecimal price) {
        if (Objects.nonNull(req.getQuantity()) && req.getQuantity() > 0) {
            return req.getQuantity();
        }
        if (Objects.isNull(req.getTargetWeight()) || req.getTargetWeight().signum() <= 0) {
            throw new BusinessException("请填写 quantity 或 targetWeight");
        }
        if (!"BUY".equals(side)) {
            PaperPosition position = paperPositionMapper.selectOne(Wrappers.<PaperPosition>lambdaQuery()
                    .eq(PaperPosition::getAccountId, account.getId())
                    .eq(PaperPosition::getCode, code)
                    .last("limit 1"));
            if (Objects.isNull(position)) {
                throw new BusinessException("无持仓可卖");
            }
            BigDecimal weight = req.getTargetWeight().min(BigDecimal.ONE);
            int qty = BigDecimal.valueOf(position.getQuantity()).multiply(weight)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                    .intValue() * 100;
            return Math.max(100, Math.min(qty, position.getQuantity()));
        }
        BigDecimal weight = req.getTargetWeight().min(new BigDecimal("0.95"));
        RiskOverviewResp risk = riskService.overview(account.getId());
        BigDecimal budget = risk.getTotalAsset().multiply(weight);
        int lots = budget.divide(price.multiply(BigDecimal.valueOf(100)), 0, RoundingMode.DOWN).intValue();
        if (lots <= 0) {
            throw new BusinessException("目标仓位过小，无法买入整手");
        }
        return lots * 100;
    }

    @Override
    public List<PaperPosition> listPositions(Long accountId) {
        List<PaperPosition> positions = paperPositionMapper.selectList(Wrappers.<PaperPosition>lambdaQuery()
                .eq(PaperPosition::getAccountId, accountId)
                .gt(PaperPosition::getQuantity, 0)
                .orderByAsc(PaperPosition::getCode));
        if (positions.isEmpty()) {
            return positions;
        }
        List<String> codes = new ArrayList<>();
        for (PaperPosition position : positions) {
            codes.add(position.getCode());
            if (StringUtils.isBlank(position.getName())) {
                String name = resolveName(position.getCode());
                if (StringUtils.isNotBlank(name)) {
                    position.setName(name);
                    position.setUpdateTime(LocalDateTime.now());
                    paperPositionMapper.updateById(position);
                }
            }
        }
        Map<String, BigDecimal> closeMap = latestCloseMap(codes);
        for (PaperPosition position : positions) {
            BigDecimal price = closeMap.get(position.getCode());
            if (Objects.isNull(price) || Objects.isNull(position.getQuantity()) || Objects.isNull(position.getCostPrice())) {
                continue;
            }
            BigDecimal mv = price.multiply(BigDecimal.valueOf(position.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cost = position.getCostPrice().multiply(BigDecimal.valueOf(position.getQuantity()));
            BigDecimal pnl = mv.subtract(cost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal pnlPct = cost.signum() == 0 ? BigDecimal.ZERO
                    : pnl.divide(cost, 4, RoundingMode.HALF_UP);
            position.setMarketPrice(price);
            position.setMarketValue(mv);
            position.setPnl(pnl);
            position.setPnlPct(pnlPct);
            if (Objects.nonNull(position.getCreateTime())) {
                position.setHoldDays((int) Math.max(0, ChronoUnit.DAYS.between(position.getCreateTime().toLocalDate(), LocalDate.now())));
            }
        }
        return positions;
    }

    private Map<String, BigDecimal> latestCloseMap(List<String> codes) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (codes.isEmpty()) {
            return map;
        }
        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, codes));
        Set<String> missing = new HashSet<>(codes);
        for (StockBasic basic : basics) {
            if (Objects.nonNull(basic.getLatestPrice()) && basic.getLatestPrice().signum() > 0) {
                map.put(basic.getCode(), basic.getLatestPrice());
                missing.remove(basic.getCode());
            }
        }
        if (missing.isEmpty()) {
            return map;
        }
        // 按交易日倒序扫描，首见即最新收盘
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .in(BarDaily::getCode, missing)
                .orderByDesc(BarDaily::getTradeDate)
                .last("LIMIT " + Math.min(Math.max(missing.size() * 40, 40), 8000)));
        for (BarDaily bar : bars) {
            if (Objects.isNull(bar.getClosePrice()) || !missing.contains(bar.getCode())) {
                continue;
            }
            map.put(bar.getCode(), bar.getClosePrice());
            missing.remove(bar.getCode());
            if (missing.isEmpty()) {
                break;
            }
        }
        return map;
    }

    @Override
    public List<PaperOrder> listOrders(Long accountId) {
        return paperOrderMapper.selectList(Wrappers.<PaperOrder>lambdaQuery()
                .eq(PaperOrder::getAccountId, accountId)
                .orderByDesc(PaperOrder::getId));
    }

    private void upsertBuyPosition(Long accountId, String code, int qty, BigDecimal price, LocalDateTime now) {
        PaperPosition position = paperPositionMapper.selectOne(Wrappers.<PaperPosition>lambdaQuery()
                .eq(PaperPosition::getAccountId, accountId)
                .eq(PaperPosition::getCode, code)
                .last("limit 1"));
        BigDecimal stopMult = configService.getDecimal("atr_stop_mult", new BigDecimal("2.0"));
        BigDecimal takeMult = configService.getDecimal("atr_take_mult", new BigDecimal("3.0"));
        BigDecimal atr = calcAtr14(code);
        BigDecimal stopLoss;
        BigDecimal takeProfit;
        if (atr.signum() > 0) {
            stopLoss = price.subtract(atr.multiply(stopMult)).setScale(4, RoundingMode.HALF_UP);
            takeProfit = price.add(atr.multiply(takeMult)).setScale(4, RoundingMode.HALF_UP);
            if (stopLoss.signum() <= 0) {
                stopLoss = price.multiply(new BigDecimal("0.92")).setScale(4, RoundingMode.HALF_UP);
            }
        } else {
            stopLoss = price.multiply(new BigDecimal("0.92")).setScale(4, RoundingMode.HALF_UP);
            takeProfit = price.multiply(new BigDecimal("1.20")).setScale(4, RoundingMode.HALF_UP);
        }
        String name = resolveName(code);
        if (Objects.isNull(position)) {
            try {
                paperPositionMapper.insert(PaperPosition.builder()
                        .accountId(accountId)
                        .code(code)
                        .name(name)
                        .quantity(qty)
                        .costPrice(price)
                        .stopLoss(stopLoss)
                        .takeProfit(takeProfit)
                        .createTime(now)
                        .updateTime(now)
                        .deleted(0)
                        .build());
            } catch (Exception ex) {
                // 软删除残留导致唯一键冲突时恢复
                paperPositionMapper.restoreAndSet(accountId, code, qty, price, stopLoss, takeProfit, name, now);
            }
            return;
        }
        int oldQty = Objects.nonNull(position.getQuantity()) ? position.getQuantity() : 0;
        BigDecimal avg;
        if (oldQty <= 0) {
            avg = price;
        } else {
            BigDecimal oldAmount = position.getCostPrice().multiply(BigDecimal.valueOf(oldQty));
            BigDecimal newAmount = price.multiply(BigDecimal.valueOf(qty));
            avg = oldAmount.add(newAmount).divide(BigDecimal.valueOf(oldQty + qty), 4, RoundingMode.HALF_UP);
        }
        position.setQuantity(oldQty + qty);
        position.setCostPrice(avg);
        if (StringUtils.isBlank(position.getName())) {
            position.setName(name);
        }
        if (atr.signum() > 0) {
            BigDecimal newStop = avg.subtract(atr.multiply(stopMult)).setScale(4, RoundingMode.HALF_UP);
            position.setStopLoss(newStop.signum() > 0 ? newStop : avg.multiply(new BigDecimal("0.92")).setScale(4, RoundingMode.HALF_UP));
            position.setTakeProfit(avg.add(atr.multiply(takeMult)).setScale(4, RoundingMode.HALF_UP));
        } else {
            position.setStopLoss(avg.multiply(new BigDecimal("0.92")).setScale(4, RoundingMode.HALF_UP));
            position.setTakeProfit(avg.multiply(new BigDecimal("1.20")).setScale(4, RoundingMode.HALF_UP));
        }
        position.setUpdateTime(now);
        paperPositionMapper.updateById(position);
    }

    private String resolveName(String code) {
        StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("limit 1"));
        if (Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getName())) {
            return basic.getName();
        }
        Watchlist watchlist = watchlistMapper.selectOne(Wrappers.<Watchlist>lambdaQuery()
                .eq(Watchlist::getCode, code)
                .last("limit 1"));
        if (Objects.nonNull(watchlist) && StringUtils.isNotBlank(watchlist.getName())) {
            return watchlist.getName();
        }
        try {
            StockBasic fetched = stockQuoteClient.fetchBasic(code);
            if (Objects.nonNull(fetched) && StringUtils.isNotBlank(fetched.getName())) {
                LocalDateTime now = LocalDateTime.now();
                if (Objects.isNull(basic)) {
                    fetched.setCreateTime(now);
                    fetched.setUpdateTime(now);
                    stockBasicMapper.insert(fetched);
                } else {
                    basic.setName(fetched.getName());
                    basic.setIndustry(fetched.getIndustry());
                    basic.setLatestPrice(fetched.getLatestPrice());
                    basic.setUpdateTime(now);
                    stockBasicMapper.updateById(basic);
                }
                return fetched.getName();
            }
        } catch (Exception ignored) {
            // 名称兜底失败不影响成交
        }
        return null;
    }

    /**
     * 一键平仓全部持仓
     *
     * @param accountId 账户
     * @return 卖出订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PaperOrder> closeAll(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        List<PaperPosition> positions = paperPositionMapper.selectList(Wrappers.<PaperPosition>lambdaQuery()
                .eq(PaperPosition::getAccountId, id)
                .gt(PaperPosition::getQuantity, 0));
        List<PaperOrder> orders = new ArrayList<>();
        for (PaperPosition position : positions) {
            PaperOrderReq req = new PaperOrderReq();
            req.setAccountId(id);
            req.setCode(position.getCode());
            req.setSide("SELL");
            req.setQuantity(position.getQuantity());
            orders.add(placeOrder(req));
        }
        return orders;
    }

    /**
     * 按风控上限建议买入数量
     *
     * @param accountId    账户
     * @param code         代码
     * @param targetWeight 目标仓位
     * @return 建议
     */
    @Override
    public PositionSuggestResp suggestPosition(Long accountId, String code, BigDecimal targetWeight) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        String pure = MarketCodeUtils.normalizeCode(code);
        if (StringUtils.isBlank(pure)) {
            throw new BusinessException("code 不能为空");
        }
        RiskOverviewResp risk = riskService.overview(id);
        BigDecimal singleLimit = Objects.nonNull(risk.getSingleLimit()) ? risk.getSingleLimit() : new BigDecimal("0.15");
        String weightSource = "单票上限";
        BigDecimal weight;
        if (Objects.nonNull(targetWeight) && targetWeight.signum() > 0) {
            weight = targetWeight.min(singleLimit);
            weightSource = "指定仓位";
        } else {
            KellySuggestResp kelly = kellySuggest(id);
            if (Objects.nonNull(kelly.getSuggestedWeight()) && kelly.getSuggestedWeight().signum() > 0) {
                weight = kelly.getSuggestedWeight().min(singleLimit);
                weightSource = "半Kelly∩单票上限";
            } else {
                weight = singleLimit;
            }
        }
        String volRegime = hs300VolRegime();
        if ("HIGH".equals(volRegime)) {
            weight = weight.multiply(new BigDecimal("0.5")).setScale(4, RoundingMode.HALF_UP);
            weightSource = weightSource + "×波动防守";
        } else if ("LOW".equals(volRegime)) {
            weight = weight.multiply(new BigDecimal("1.1")).min(singleLimit).setScale(4, RoundingMode.HALF_UP);
            weightSource = weightSource + "×波动加仓";
        }
        BarDaily bar = resolveBar(pure, null);
        BigDecimal price = bar.getClosePrice();
        BigDecimal budget = risk.getTotalAsset().multiply(weight).min(risk.getCash());
        int lots = budget.divide(price.multiply(BigDecimal.valueOf(100)), 0, RoundingMode.DOWN).intValue();
        int qty = lots * 100;
        BigDecimal atr = calcAtr14(pure);
        BigDecimal stopMult = configService.getDecimal("atr_stop_mult", new BigDecimal("2.0"));
        BigDecimal riskPct = configService.getDecimal("risk_per_trade", new BigDecimal("0.01"));
        BigDecimal riskAmt = risk.getTotalAsset().multiply(riskPct).setScale(2, RoundingMode.HALF_UP);
        int riskQty = 0;
        if (atr.signum() > 0 && stopMult.signum() > 0) {
            BigDecimal stopDist = atr.multiply(stopMult);
            if (stopDist.signum() > 0) {
                riskQty = riskAmt.divide(stopDist.multiply(BigDecimal.valueOf(100)), 0, RoundingMode.DOWN)
                        .intValue() * 100;
            }
        }
        // 仓位权重与风险预算取更小
        if (riskQty > 0 && riskQty < qty) {
            qty = riskQty;
            lots = qty / 100;
            weightSource = weightSource + "∩风险预算";
        }
        BigDecimal amount = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgAmt = avgDailyAmount(pure, 20);
        BigDecimal participation = BigDecimal.ZERO;
        boolean liquidityOk = true;
        if (avgAmt.signum() > 0 && amount.signum() > 0) {
            participation = amount.divide(avgAmt, 6, RoundingMode.HALF_UP);
            // 单笔超过均额 10% 时按 5% 上限缩量
            if (participation.compareTo(new BigDecimal("0.10")) > 0) {
                BigDecimal capped = avgAmt.multiply(new BigDecimal("0.05"));
                int liqLots = capped.divide(price.multiply(BigDecimal.valueOf(100)), 0, RoundingMode.DOWN).intValue();
                if (liqLots < lots) {
                    lots = liqLots;
                    qty = lots * 100;
                    amount = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                    participation = avgAmt.signum() == 0 ? BigDecimal.ZERO
                            : amount.divide(avgAmt, 6, RoundingMode.HALF_UP);
                    weightSource = weightSource + "+流动性缩量";
                }
            }
            liquidityOk = participation.compareTo(new BigDecimal("0.05")) <= 0;
        }
        String msg;
        if (qty <= 0) {
            msg = "资金不足以买入 1 手（或风险预算为 0）";
        } else if (!liquidityOk) {
            msg = "按" + weightSource + "；参与率 " + participation.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    + "% 偏高（建议≤5%）";
        } else {
            msg = "按" + weightSource + "；风险预算股数 " + riskQty + "；参与率 "
                    + participation.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%";
        }
        return PositionSuggestResp.builder()
                .code(pure)
                .price(price)
                .totalAsset(risk.getTotalAsset())
                .cash(risk.getCash())
                .singleLimit(singleLimit)
                .targetWeight(weight)
                .suggestedQuantity(qty)
                .estimatedAmount(amount)
                .avgDailyAmount(avgAmt.setScale(2, RoundingMode.HALF_UP))
                .participationRate(participation)
                .liquidityOk(liquidityOk)
                .atr14(atr)
                .riskBudgetQuantity(riskQty)
                .riskBudgetAmount(riskAmt)
                .message(msg)
                .build();
    }

    private BigDecimal avgDailyAmount(String code, int days) {
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit " + Math.max(5, days)));
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (BarDaily b : bars) {
            if (Objects.nonNull(b.getAmount()) && b.getAmount().signum() > 0) {
                sum = sum.add(b.getAmount());
                n++;
            }
        }
        if (n == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }

    private String hs300VolRegime() {
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, "000300")
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit 260"));
        if (bars.size() < 40) {
            return "MID";
        }
        List<BarDaily> asc = new ArrayList<>(bars);
        asc.sort(Comparator.comparing(BarDaily::getTradeDate));
        List<Double> rets = new ArrayList<>();
        for (int i = 1; i < asc.size(); i++) {
            BigDecimal prev = asc.get(i - 1).getClosePrice();
            BigDecimal curr = asc.get(i).getClosePrice();
            if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                rets.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP).doubleValue());
            }
        }
        if (rets.size() < 30) {
            return "MID";
        }
        List<Double> rolling = new ArrayList<>();
        int win = 20;
        for (int i = win; i <= rets.size(); i++) {
            List<Double> slice = rets.subList(i - win, i);
            double mean = slice.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double var = 0;
            for (double r : slice) {
                var += (r - mean) * (r - mean);
            }
            var /= (slice.size() - 1);
            rolling.add(Math.sqrt(var) * Math.sqrt(252));
        }
        double current = rolling.get(rolling.size() - 1);
        int below = 0;
        for (double v : rolling) {
            if (v <= current) {
                below++;
            }
        }
        double pct = below * 1.0 / rolling.size();
        if (pct >= 0.7) {
            return "HIGH";
        }
        if (pct <= 0.3) {
            return "LOW";
        }
        return "MID";
    }

    private BarDaily resolveBar(String code, String tradeDate) {
        if (StringUtils.isNotBlank(tradeDate)) {
            LocalDate date = LocalDate.parse(tradeDate.trim(), DAY);
            BarDaily bar = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, code)
                    .eq(BarDaily::getTradeDate, date)
                    .last("limit 1"));
            if (Objects.isNull(bar)) {
                throw new BusinessException("无该日行情: " + code + " " + tradeDate);
            }
            return bar;
        }
        BarDaily bar = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit 1"));
        if (Objects.isNull(bar)) {
            throw new BusinessException("无行情，请先同步日线: " + code);
        }
        return bar;
    }

    private BigDecimal resolveFillPrice(BarDaily bar, String side) {
        String mode = configService.getString("fill_mode", "CLOSE");
        BigDecimal base = bar.getClosePrice();
        if ("NEXT_OPEN".equalsIgnoreCase(mode) && Objects.nonNull(bar.getOpenPrice())) {
            // 简化：无次日开盘缓存时仍用当日开盘近似
            base = bar.getOpenPrice();
        }
        BigDecimal slipBuy = configService.getDecimal("buy_slippage", new BigDecimal("0.001"));
        BigDecimal slipSell = configService.getDecimal("sell_slippage", new BigDecimal("0.001"));
        if ("BUY".equals(side)) {
            return base.multiply(BigDecimal.ONE.add(slipBuy)).setScale(4, RoundingMode.HALF_UP);
        }
        return base.multiply(BigDecimal.ONE.subtract(slipSell)).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 按信号下模拟单
     *
     * @param signalId     信号
     * @param accountId    账户
     * @param targetWeight 买入仓位
     * @return 订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperOrder orderFromSignal(Long signalId, Long accountId, BigDecimal targetWeight) {
        if (Objects.isNull(signalId)) {
            throw new BusinessException("signalId 不能为空");
        }
        StrategySignalEntity signal = strategySignalMapper.selectById(signalId);
        if (Objects.isNull(signal)) {
            throw new BusinessException("信号不存在");
        }
        String side = StringUtils.isNotBlank(signal.getSide()) ? signal.getSide().toUpperCase() : "";
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            throw new BusinessException("仅支持 BUY/SELL 信号下单，当前: " + signal.getSide());
        }
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        PaperOrderReq req = new PaperOrderReq();
        req.setAccountId(id);
        req.setCode(signal.getCode());
        req.setSide(side);
        if ("BUY".equals(side)) {
            PositionSuggestResp suggest = suggestPosition(id, signal.getCode(), targetWeight);
            if (Objects.isNull(suggest.getSuggestedQuantity()) || suggest.getSuggestedQuantity() <= 0) {
                throw new BusinessException("建议买入数量为 0，请检查资金/风控");
            }
            req.setQuantity(suggest.getSuggestedQuantity());
        } else {
            PaperPosition position = paperPositionMapper.selectOne(Wrappers.<PaperPosition>lambdaQuery()
                    .eq(PaperPosition::getAccountId, id)
                    .eq(PaperPosition::getCode, MarketCodeUtils.normalizeCode(signal.getCode()))
                    .gt(PaperPosition::getQuantity, 0)
                    .last("limit 1"));
            if (Objects.isNull(position)) {
                throw new BusinessException("无持仓可卖: " + signal.getCode());
            }
            req.setQuantity(position.getQuantity());
        }
        PaperOrder order = placeOrder(req);
        order.setReason("信号#" + signalId + " " + signal.getStrategyId() + " " + side
                + (StringUtils.isNotBlank(order.getReason()) ? " · " + order.getReason() : ""));
        order.setUpdateTime(LocalDateTime.now());
        paperOrderMapper.updateById(order);
        return order;
    }

    /**
     * 刷新持仓市价
     *
     * @param accountId 账户
     * @return 持仓
     */
    @Override
    public List<PaperPosition> refreshMarks(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        List<PaperPosition> positions = paperPositionMapper.selectList(Wrappers.<PaperPosition>lambdaQuery()
                .eq(PaperPosition::getAccountId, id)
                .gt(PaperPosition::getQuantity, 0));
        for (PaperPosition position : positions) {
            try {
                StockBasic basic = stockQuoteClient.fetchBasic(position.getCode());
                if (Objects.nonNull(basic) && Objects.nonNull(basic.getLatestPrice())) {
                    StockBasic stored = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                            .eq(StockBasic::getCode, position.getCode())
                            .last("limit 1"));
                    LocalDateTime now = LocalDateTime.now();
                    if (Objects.isNull(stored)) {
                        basic.setCreateTime(now);
                        basic.setUpdateTime(now);
                        stockBasicMapper.insert(basic);
                    } else {
                        stored.setLatestPrice(basic.getLatestPrice());
                        stored.setPctChg(basic.getPctChg());
                        stored.setName(basic.getName());
                        stored.setUpdateTime(now);
                        stockBasicMapper.updateById(stored);
                    }
                }
            } catch (Exception ignored) {
                // 单票刷新失败不影响整体
            }
        }
        return listPositions(id);
    }

    /**
     * 相对基准绩效
     *
     * @param accountId        账户
     * @param benchmarkCode    主基准
     * @param altBenchmarkCode 副基准
     * @return 绩效
     */
    @Override
    public PaperPerformanceResp performance(Long accountId, String benchmarkCode, String altBenchmarkCode) {
        PaperAccount account = Objects.nonNull(accountId) ? paperAccountMapper.selectById(accountId) : defaultAccount();
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        String bench = StringUtils.isNotBlank(benchmarkCode)
                ? MarketCodeUtils.normalizeCode(benchmarkCode) : "000300";
        String altBench = StringUtils.isNotBlank(altBenchmarkCode)
                ? MarketCodeUtils.normalizeCode(altBenchmarkCode) : "000905";
        List<PaperOrder> orders = listOrders(account.getId());
        RiskOverviewResp risk = riskService.overview(account.getId());
        BigDecimal initCash = Objects.nonNull(account.getInitCash()) ? account.getInitCash() : BigDecimal.valueOf(1000000);
        PaperEquityCalculator.ReplayResult replay = replayOrders(account, orders, risk.getTotalAsset());
        List<EquityPointResp> paperEq = replay.getPoints();
        LocalDate start = orders.stream()
                .map(PaperOrder::getTradeDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now().minusMonths(6));
        LocalDate end = LocalDate.now();
        // 下单过近时基准区间不足 2 根，自动扩到近半年
        LocalDate benchBegin = start.isAfter(end.minusDays(5)) ? end.minusMonths(6) : start;
        List<EquityPointResp> benchEq = buyHoldCurve(bench, benchBegin, end, initCash);
        if (benchEq.size() < 2) {
            benchEq = buyHoldCurve(bench, end.minusMonths(12), end, initCash);
        }
        List<EquityPointResp> altEq = buyHoldCurve(altBench, benchBegin, end, initCash);
        if (altEq.size() < 2) {
            altEq = buyHoldCurve(altBench, end.minusMonths(12), end, initCash);
        }
        BigDecimal paperReturn = initCash.signum() == 0 ? BigDecimal.ZERO
                : risk.getTotalAsset().subtract(initCash).divide(initCash, 6, RoundingMode.HALF_UP);
        BigDecimal twr = calcTimeWeightedReturn(paperEq);
        BigDecimal benchReturn = curveReturn(benchEq);
        BigDecimal altReturn = curveReturn(altEq);
        List<EquityPointResp> paperForBeta = padEquityToBenchmark(paperEq, benchEq, initCash);
        BigDecimal beta = calcBeta(paperForBeta, benchEq);
        BigDecimal rollingBeta = calcRollingBeta(paperForBeta, benchEq, 20);
        BigDecimal rollingAlpha = calcRollingAlpha(paperForBeta, benchEq, 20);
        BigDecimal sortino = calcSortino(paperEq);
        BigDecimal[] irTe = calcInformationRatioAndTe(paperEq, benchEq);
        return PaperPerformanceResp.builder()
                .accountId(account.getId())
                .benchmarkCode(bench)
                .startDate(start)
                .paperReturn(paperReturn)
                .timeWeightedReturn(twr)
                .benchmarkReturn(benchReturn)
                .alpha(paperReturn.subtract(benchReturn).setScale(6, RoundingMode.HALF_UP))
                .beta(beta)
                .rollingBeta20(rollingBeta)
                .rollingAlpha20(rollingAlpha)
                .maxDrawdown(replay.getMaxDrawdown())
                .sharpe(replay.getSharpe())
                .sortino(sortino)
                .informationRatio(irTe[0])
                .trackingError(irTe[1])
                .altBenchmarkCode(altBench)
                .altBenchmarkReturn(altReturn)
                .altAlpha(paperReturn.subtract(altReturn).setScale(6, RoundingMode.HALF_UP))
                .altBenchmarkEquities(altEq)
                .totalAsset(risk.getTotalAsset())
                .paperEquities(paperEq)
                .benchmarkEquities(benchEq)
                .drawdownCurve(buildDrawdownCurve(paperEq))
                .build();
    }

    private BigDecimal calcRollingAlpha(List<EquityPointResp> paperEq, List<EquityPointResp> benchEq, int window) {
        Map<LocalDate, BigDecimal> paperMap = new HashMap<>();
        for (EquityPointResp point : paperEq) {
            if (Objects.nonNull(point.getTradeDate()) && Objects.nonNull(point.getEquity())) {
                paperMap.put(point.getTradeDate(), point.getEquity());
            }
        }
        Map<LocalDate, BigDecimal> benchMap = new HashMap<>();
        for (EquityPointResp point : benchEq) {
            if (Objects.nonNull(point.getTradeDate()) && Objects.nonNull(point.getEquity())) {
                benchMap.put(point.getTradeDate(), point.getEquity());
            }
        }
        List<LocalDate> dates = new ArrayList<>(paperMap.keySet());
        dates.retainAll(benchMap.keySet());
        dates.sort(LocalDate::compareTo);
        if (dates.size() < window + 1) {
            return BigDecimal.ZERO;
        }
        List<LocalDate> slice = dates.subList(dates.size() - (window + 1), dates.size());
        BigDecimal p0 = paperMap.get(slice.get(0));
        BigDecimal p1 = paperMap.get(slice.get(slice.size() - 1));
        BigDecimal b0 = benchMap.get(slice.get(0));
        BigDecimal b1 = benchMap.get(slice.get(slice.size() - 1));
        if (Objects.isNull(p0) || p0.signum() <= 0 || Objects.isNull(p1)
                || Objects.isNull(b0) || b0.signum() <= 0 || Objects.isNull(b1)) {
            return BigDecimal.ZERO;
        }
        BigDecimal pr = p1.subtract(p0).divide(p0, 6, RoundingMode.HALF_UP);
        BigDecimal br = b1.subtract(b0).divide(b0, 6, RoundingMode.HALF_UP);
        return pr.subtract(br).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal curveReturn(List<EquityPointResp> curve) {
        if (curve == null || curve.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal first = curve.get(0).getEquity();
        BigDecimal last = curve.get(curve.size() - 1).getEquity();
        if (Objects.isNull(first) || first.signum() <= 0 || Objects.isNull(last)) {
            return BigDecimal.ZERO;
        }
        return last.subtract(first).divide(first, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal calcTimeWeightedReturn(List<EquityPointResp> paperEq) {
        List<BigDecimal> rets = dailyReturns(paperEq);
        if (rets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal growth = BigDecimal.ONE;
        for (BigDecimal r : rets) {
            growth = growth.multiply(BigDecimal.ONE.add(r));
        }
        return growth.subtract(BigDecimal.ONE).setScale(6, RoundingMode.HALF_UP);
    }

    private List<EquityPointResp> buildDrawdownCurve(List<EquityPointResp> paperEq) {
        List<EquityPointResp> curve = new ArrayList<>();
        BigDecimal peak = BigDecimal.ZERO;
        for (EquityPointResp point : paperEq) {
            if (Objects.isNull(point.getEquity())) {
                continue;
            }
            if (point.getEquity().compareTo(peak) > 0) {
                peak = point.getEquity();
            }
            BigDecimal dd = BigDecimal.ZERO;
            if (peak.signum() > 0) {
                dd = peak.subtract(point.getEquity()).divide(peak, 6, RoundingMode.HALF_UP);
            }
            curve.add(EquityPointResp.builder()
                    .tradeDate(point.getTradeDate())
                    .equity(dd)
                    .build());
        }
        return curve;
    }

    private BigDecimal calcSortino(List<EquityPointResp> paperEq) {
        List<BigDecimal> rets = dailyReturns(paperEq);
        if (rets.size() < 5) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = rets.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rets.size()), 8, RoundingMode.HALF_UP);
        BigDecimal downside = BigDecimal.ZERO;
        int n = 0;
        for (BigDecimal r : rets) {
            if (r.signum() < 0) {
                downside = downside.add(r.multiply(r));
                n++;
            }
        }
        if (n == 0) {
            return mean.signum() > 0 ? BigDecimal.valueOf(99) : BigDecimal.ZERO;
        }
        double dd = Math.sqrt(downside.divide(BigDecimal.valueOf(n), 8, RoundingMode.HALF_UP).doubleValue());
        if (dd == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(mean.doubleValue() / dd * Math.sqrt(252)).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal[] calcInformationRatioAndTe(List<EquityPointResp> paperEq, List<EquityPointResp> benchEq) {
        BigDecimal[] zero = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        Map<LocalDate, BigDecimal> paperMap = new HashMap<>();
        for (EquityPointResp point : paperEq) {
            paperMap.put(point.getTradeDate(), point.getEquity());
        }
        Map<LocalDate, BigDecimal> benchMap = new HashMap<>();
        for (EquityPointResp point : benchEq) {
            benchMap.put(point.getTradeDate(), point.getEquity());
        }
        List<LocalDate> dates = new ArrayList<>(paperMap.keySet());
        dates.retainAll(benchMap.keySet());
        dates.sort(LocalDate::compareTo);
        if (dates.size() < 6) {
            return zero;
        }
        List<BigDecimal> excess = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            BigDecimal p0 = paperMap.get(dates.get(i - 1));
            BigDecimal p1 = paperMap.get(dates.get(i));
            BigDecimal b0 = benchMap.get(dates.get(i - 1));
            BigDecimal b1 = benchMap.get(dates.get(i));
            if (Objects.nonNull(p0) && p0.signum() > 0 && Objects.nonNull(p1)
                    && Objects.nonNull(b0) && b0.signum() > 0 && Objects.nonNull(b1)) {
                BigDecimal rp = p1.subtract(p0).divide(p0, 8, RoundingMode.HALF_UP);
                BigDecimal rb = b1.subtract(b0).divide(b0, 8, RoundingMode.HALF_UP);
                excess.add(rp.subtract(rb));
            }
        }
        if (excess.size() < 5) {
            return zero;
        }
        BigDecimal mean = excess.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(excess.size()), 8, RoundingMode.HALF_UP);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal e : excess) {
            BigDecimal d = e.subtract(mean);
            variance = variance.add(d.multiply(d));
        }
        variance = variance.divide(BigDecimal.valueOf(excess.size() - 1), 8, RoundingMode.HALF_UP);
        if (variance.signum() <= 0) {
            return zero;
        }
        double teDaily = Math.sqrt(variance.doubleValue());
        if (teDaily == 0) {
            return zero;
        }
        BigDecimal te = BigDecimal.valueOf(teDaily * Math.sqrt(252)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal ir = BigDecimal.valueOf(mean.doubleValue() / teDaily * Math.sqrt(252)).setScale(4, RoundingMode.HALF_UP);
        return new BigDecimal[]{ir, te};
    }

    /**
     * 持仓隔夜缺口风险
     *
     * @param accountId 账户
     * @return 缺口
     */
    @Override
    public GapRiskResp gapRisk(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        List<PaperPosition> positions = listPositions(id);
        List<GapRiskItem> items = new ArrayList<>();
        BigDecimal sumAvg = BigDecimal.ZERO;
        BigDecimal maxAll = BigDecimal.ZERO;
        int n = 0;
        for (PaperPosition position : positions) {
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, position.getCode())
                    .orderByDesc(BarDaily::getTradeDate)
                    .last("limit 25"));
            if (bars.size() < 3) {
                continue;
            }
            List<BarDaily> asc = new ArrayList<>(bars);
            asc.sort(Comparator.comparing(BarDaily::getTradeDate));
            BigDecimal gapSum = BigDecimal.ZERO;
            BigDecimal gapMax = BigDecimal.ZERO;
            int gapN = 0;
            BigDecimal lastGap = BigDecimal.ZERO;
            for (int i = 1; i < asc.size(); i++) {
                BigDecimal prevClose = asc.get(i - 1).getClosePrice();
                BigDecimal open = asc.get(i).getOpenPrice();
                if (Objects.isNull(prevClose) || prevClose.signum() <= 0 || Objects.isNull(open)) {
                    continue;
                }
                BigDecimal gap = open.subtract(prevClose).divide(prevClose, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                gapSum = gapSum.add(gap.abs());
                if (gap.abs().compareTo(gapMax) > 0) {
                    gapMax = gap.abs();
                }
                gapN++;
                lastGap = gap;
            }
            if (gapN == 0) {
                continue;
            }
            BigDecimal avg = gapSum.divide(BigDecimal.valueOf(gapN), 2, RoundingMode.HALF_UP);
            items.add(GapRiskItem.builder()
                    .code(position.getCode())
                    .name(position.getName())
                    .avgAbsGapPct(avg)
                    .maxAbsGapPct(gapMax.setScale(2, RoundingMode.HALF_UP))
                    .lastGapPct(lastGap.setScale(2, RoundingMode.HALF_UP))
                    .build());
            sumAvg = sumAvg.add(avg);
            if (gapMax.compareTo(maxAll) > 0) {
                maxAll = gapMax;
            }
            n++;
        }
        items.sort(Comparator.comparing(GapRiskItem::getAvgAbsGapPct).reversed());
        return GapRiskResp.builder()
                .sampleCount(n)
                .avgAbsGapPct(n == 0 ? BigDecimal.ZERO : sumAvg.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP))
                .maxAbsGapPct(maxAll.setScale(2, RoundingMode.HALF_UP))
                .message(n == 0 ? "无持仓或日线不足" : "持仓近20日隔夜缺口 |开盘/昨收-1|")
                .items(items)
                .build();
    }

    /**
     * 成交质量
     *
     * @param accountId 账户
     * @param limit     明细
     * @return 质量
     */
    @Override
    public FillQualityResp fillQuality(Long accountId, Integer limit) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        int n = Objects.nonNull(limit) ? Math.max(5, Math.min(limit, 100)) : 30;
        List<PaperOrder> orders = listOrders(id);
        List<FillQualityItem> items = new ArrayList<>();
        BigDecimal buySlipSum = BigDecimal.ZERO;
        int buyN = 0;
        BigDecimal sellSlipSum = BigDecimal.ZERO;
        int sellN = 0;
        Set<String> codes = new HashSet<>();
        LocalDate minDate = null;
        LocalDate maxDate = null;
        for (PaperOrder order : orders) {
            if (Objects.isNull(order.getPrice()) || Objects.isNull(order.getTradeDate())
                    || StringUtils.isBlank(order.getCode())) {
                continue;
            }
            codes.add(order.getCode());
            if (Objects.isNull(minDate) || order.getTradeDate().isBefore(minDate)) {
                minDate = order.getTradeDate();
            }
            if (Objects.isNull(maxDate) || order.getTradeDate().isAfter(maxDate)) {
                maxDate = order.getTradeDate();
            }
        }
        Map<String, BarDaily> barMap = new HashMap<>();
        if (!codes.isEmpty() && Objects.nonNull(minDate) && Objects.nonNull(maxDate)) {
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, codes)
                    .between(BarDaily::getTradeDate, minDate, maxDate));
            for (BarDaily bar : bars) {
                barMap.put(bar.getCode() + "|" + bar.getTradeDate(), bar);
            }
        }
        for (PaperOrder order : orders) {
            if (Objects.isNull(order.getPrice()) || Objects.isNull(order.getTradeDate())
                    || StringUtils.isBlank(order.getCode())) {
                continue;
            }
            BarDaily bar = barMap.get(order.getCode() + "|" + order.getTradeDate());
            if (Objects.isNull(bar) || Objects.isNull(bar.getClosePrice()) || bar.getClosePrice().signum() <= 0) {
                continue;
            }
            BigDecimal slip = order.getPrice().subtract(bar.getClosePrice())
                    .divide(bar.getClosePrice(), 6, RoundingMode.HALF_UP);
            boolean buy = "BUY".equalsIgnoreCase(order.getSide());
            // 买入相对收盘为正=买贵；卖出相对收盘为负=卖便宜，统一为不利滑点正数
            BigDecimal adverse = buy ? slip : slip.negate();
            if (buy) {
                buySlipSum = buySlipSum.add(slip);
                buyN++;
            } else if ("SELL".equalsIgnoreCase(order.getSide())) {
                sellSlipSum = sellSlipSum.add(slip.negate());
                sellN++;
            }
            items.add(FillQualityItem.builder()
                    .orderId(order.getId())
                    .code(order.getCode())
                    .side(order.getSide())
                    .tradeDate(order.getTradeDate())
                    .fillPrice(order.getPrice())
                    .closePrice(bar.getClosePrice())
                    .slippageVsClose(adverse)
                    .build());
            if (items.size() >= n) {
                break;
            }
        }
        BigDecimal avgBuy = buyN == 0 ? BigDecimal.ZERO
                : buySlipSum.divide(BigDecimal.valueOf(buyN), 6, RoundingMode.HALF_UP);
        BigDecimal avgSell = sellN == 0 ? BigDecimal.ZERO
                : sellSlipSum.divide(BigDecimal.valueOf(sellN), 6, RoundingMode.HALF_UP);
        // 分数：不利滑点越小越好，以 50bp 为参考
        BigDecimal adverseAvg = BigDecimal.ZERO;
        int cnt = buyN + sellN;
        if (cnt > 0) {
            adverseAvg = avgBuy.max(BigDecimal.ZERO).add(avgSell.max(BigDecimal.ZERO))
                    .divide(BigDecimal.valueOf(buyN > 0 && sellN > 0 ? 2 : 1), 6, RoundingMode.HALF_UP);
        }
        BigDecimal score = BigDecimal.valueOf(100).subtract(adverseAvg.abs().multiply(BigDecimal.valueOf(10000)));
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            score = BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.valueOf(100)) > 0) {
            score = BigDecimal.valueOf(100);
        }
        return FillQualityResp.builder()
                .sampleCount(cnt)
                .avgBuySlippage(avgBuy)
                .avgSellSlippage(avgSell)
                .qualityScore(score.setScale(1, RoundingMode.HALF_UP))
                .message(cnt == 0 ? "无有效成交样本" : "相对收盘不利滑点评分，样本 " + cnt)
                .items(items)
                .build();
    }

    /**
     * Kelly 仓位建议
     *
     * @param accountId 账户
     * @return 建议
     */
    @Override
    public KellySuggestResp kellySuggest(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        RiskOverviewResp risk = riskService.overview(id);
        BigDecimal winRate = BigDecimal.ZERO;
        BigDecimal payoff = BigDecimal.ONE;
        List<PaperOrder> orders = listOrders(id);
        BigDecimal grossWin = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        int wins = 0;
        int closed = 0;
        List<PaperOrder> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator.comparing(PaperOrder::getTradeDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(PaperOrder::getId, Comparator.nullsLast(Long::compareTo)));
        Map<String, List<BigDecimal[]>> openLots = new HashMap<>();
        for (PaperOrder order : sorted) {
            int qty = Objects.nonNull(order.getQuantity()) ? order.getQuantity() : 0;
            if (qty <= 0 || Objects.isNull(order.getPrice())) {
                continue;
            }
            if ("BUY".equalsIgnoreCase(order.getSide())) {
                openLots.computeIfAbsent(order.getCode(), k -> new ArrayList<>())
                        .add(new BigDecimal[]{BigDecimal.valueOf(qty), order.getPrice()});
                continue;
            }
            if (!"SELL".equalsIgnoreCase(order.getSide())) {
                continue;
            }
            List<BigDecimal[]> queue = openLots.computeIfAbsent(order.getCode(), k -> new ArrayList<>());
            int remain = qty;
            while (remain > 0 && !queue.isEmpty()) {
                BigDecimal[] lot = queue.get(0);
                int lotQty = lot[0].intValue();
                int matched = Math.min(remain, lotQty);
                BigDecimal pnl = order.getPrice().subtract(lot[1]).multiply(BigDecimal.valueOf(matched));
                closed++;
                if (pnl.signum() >= 0) {
                    wins++;
                    grossWin = grossWin.add(pnl);
                } else {
                    grossLoss = grossLoss.add(pnl.abs());
                }
                lot[0] = BigDecimal.valueOf(lotQty - matched);
                remain -= matched;
                if (lot[0].signum() <= 0) {
                    queue.remove(0);
                }
            }
        }
        BigDecimal singleLimit = Objects.nonNull(risk.getSingleLimit()) ? risk.getSingleLimit() : new BigDecimal("0.15");
        if (closed > 0) {
            winRate = BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(closed), 4, RoundingMode.HALF_UP);
            int losses = closed - wins;
            BigDecimal avgWin = wins == 0 ? BigDecimal.ZERO
                    : grossWin.divide(BigDecimal.valueOf(wins), 6, RoundingMode.HALF_UP);
            BigDecimal avgLoss = losses == 0 ? BigDecimal.ONE
                    : grossLoss.divide(BigDecimal.valueOf(losses), 6, RoundingMode.HALF_UP);
            if (avgLoss.signum() > 0) {
                payoff = avgWin.divide(avgLoss, 4, RoundingMode.HALF_UP);
            } else if (avgWin.signum() > 0) {
                payoff = new BigDecimal("99");
            } else {
                payoff = BigDecimal.ONE;
            }
        }
        if (closed < 3) {
            return KellySuggestResp.builder()
                    .winRate(winRate)
                    .payoffRatio(payoff)
                    .fullKelly(BigDecimal.ZERO)
                    .halfKelly(BigDecimal.ZERO)
                    .suggestedWeight(BigDecimal.ZERO)
                    .singleStockLimit(singleLimit)
                    .message("闭合样本不足(" + closed + ")，Kelly 不可用，仓位请用单票上限 " + singleLimit)
                    .build();
        }
        // f* = p - (1-p)/b
        BigDecimal full = BigDecimal.ZERO;
        if (payoff.signum() > 0) {
            full = winRate.subtract(BigDecimal.ONE.subtract(winRate).divide(payoff, 6, RoundingMode.HALF_UP));
        }
        if (full.signum() < 0) {
            full = BigDecimal.ZERO;
        }
        if (full.compareTo(BigDecimal.ONE) > 0) {
            full = BigDecimal.ONE;
        }
        BigDecimal half = full.multiply(new BigDecimal("0.5")).setScale(4, RoundingMode.HALF_UP);
        BigDecimal suggested = half.min(singleLimit).setScale(4, RoundingMode.HALF_UP);
        return KellySuggestResp.builder()
                .winRate(winRate)
                .payoffRatio(payoff)
                .fullKelly(full.setScale(4, RoundingMode.HALF_UP))
                .halfKelly(half)
                .suggestedWeight(suggested)
                .singleStockLimit(singleLimit)
                .message("半Kelly " + half + " 与单票上限取小 → " + suggested)
                .build();
    }

    /**
     * 交易成本汇总
     *
     * @param accountId 账户
     * @return 费用
     */
    @Override
    public PaperCostResp costSummary(Long accountId) {
        PaperAccount account = Objects.nonNull(accountId) ? paperAccountMapper.selectById(accountId) : defaultAccount();
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        List<PaperOrder> orders = listOrders(account.getId());
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal turnover = BigDecimal.ZERO;
        int buy = 0;
        int sell = 0;
        for (PaperOrder order : orders) {
            if (Objects.nonNull(order.getFee())) {
                totalFee = totalFee.add(order.getFee());
            }
            if (Objects.nonNull(order.getAmount())) {
                turnover = turnover.add(order.getAmount());
            }
            if ("BUY".equalsIgnoreCase(order.getSide())) {
                buy++;
            } else if ("SELL".equalsIgnoreCase(order.getSide())) {
                sell++;
            }
        }
        BigDecimal initCash = Objects.nonNull(account.getInitCash()) ? account.getInitCash() : BigDecimal.valueOf(1000000);
        BigDecimal feeRate = turnover.signum() == 0 ? BigDecimal.ZERO
                : totalFee.divide(turnover, 6, RoundingMode.HALF_UP);
        BigDecimal feeToCap = initCash.signum() == 0 ? BigDecimal.ZERO
                : totalFee.divide(initCash, 6, RoundingMode.HALF_UP);
        return PaperCostResp.builder()
                .accountId(account.getId())
                .totalFee(totalFee.setScale(2, RoundingMode.HALF_UP))
                .totalTurnover(turnover.setScale(2, RoundingMode.HALF_UP))
                .feeRate(feeRate)
                .orderCount(orders.size())
                .buyCount(buy)
                .sellCount(sell)
                .feeToCapital(feeToCap)
                .message("累计费用 " + totalFee.setScale(2, RoundingMode.HALF_UP) + " · 费率 "
                        + feeRate.multiply(BigDecimal.valueOf(10000)).setScale(2, RoundingMode.HALF_UP) + " bp")
                .build();
    }

    /**
     * 持仓相关性矩阵
     *
     * @param accountId 账户
     * @param lookback  回看天数
     * @return 矩阵
     */
    @Override
    public CorrelationMatrixResp positionCorrelation(Long accountId, Integer lookback) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        int days = Objects.nonNull(lookback) ? Math.max(20, Math.min(lookback, 250)) : 60;
        List<PaperPosition> positions = listPositions(id);
        if (positions.size() < 2) {
            return CorrelationMatrixResp.builder()
                    .codes(List.of())
                    .names(List.of())
                    .matrix(List.of())
                    .sampleDays(0)
                    .message("持仓不足 2 只")
                    .build();
        }
        LocalDate end = LocalDate.now();
        LocalDate begin = end.minusDays(days * 2L);
        List<String> codes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Map<String, Map<LocalDate, BigDecimal>> closes = new LinkedHashMap<>();
        for (PaperPosition position : positions) {
            codes.add(position.getCode());
            names.add(StringUtils.isNotBlank(position.getName()) ? position.getName() : position.getCode());
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, position.getCode())
                    .ge(BarDaily::getTradeDate, begin)
                    .le(BarDaily::getTradeDate, end)
                    .orderByDesc(BarDaily::getTradeDate)
                    .last("limit " + (days + 5)));
            Map<LocalDate, BigDecimal> map = new HashMap<>();
            for (BarDaily bar : bars) {
                if (Objects.nonNull(bar.getClosePrice())) {
                    map.put(bar.getTradeDate(), bar.getClosePrice());
                }
            }
            closes.put(position.getCode(), map);
        }
        java.util.TreeSet<LocalDate> common = null;
        for (String code : codes) {
            java.util.TreeSet<LocalDate> dates = new java.util.TreeSet<>(closes.get(code).keySet());
            if (common == null) {
                common = dates;
            } else {
                common.retainAll(dates);
            }
        }
        if (common == null || common.size() < 10) {
            return CorrelationMatrixResp.builder()
                    .codes(codes)
                    .names(names)
                    .matrix(List.of())
                    .sampleDays(0)
                    .message("共同交易日不足")
                    .build();
        }
        List<LocalDate> dateList = new ArrayList<>(common);
        if (dateList.size() > days + 1) {
            dateList = dateList.subList(dateList.size() - (days + 1), dateList.size());
        }
        Map<String, List<Double>> returns = new HashMap<>();
        for (String code : codes) {
            List<Double> rets = new ArrayList<>();
            Map<LocalDate, BigDecimal> px = closes.get(code);
            for (int i = 1; i < dateList.size(); i++) {
                BigDecimal prev = px.get(dateList.get(i - 1));
                BigDecimal curr = px.get(dateList.get(i));
                if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                    rets.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP).doubleValue());
                } else {
                    rets.add(0d);
                }
            }
            returns.put(code, rets);
        }
        List<List<BigDecimal>> matrix = new ArrayList<>();
        for (String a : codes) {
            List<BigDecimal> row = new ArrayList<>();
            for (String b : codes) {
                row.add(pearsonCorr(returns.get(a), returns.get(b)));
            }
            matrix.add(row);
        }
        return CorrelationMatrixResp.builder()
                .codes(codes)
                .names(names)
                .matrix(matrix)
                .sampleDays(dateList.size() - 1)
                .message("持仓近 " + (dateList.size() - 1) + " 日收益相关")
                .build();
    }

    private BigDecimal pearsonCorr(List<Double> xs, List<Double> ys) {
        if (xs == null || ys == null || xs.size() != ys.size() || xs.size() < 3) {
            return BigDecimal.ZERO;
        }
        int n = xs.size();
        double meanX = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanY = ys.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double num = 0;
        double denX = 0;
        double denY = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs.get(i) - meanX;
            double dy = ys.get(i) - meanY;
            num += dx * dy;
            denX += dx * dx;
            denY += dy * dy;
        }
        if (denX == 0 || denY == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(num / Math.sqrt(denX * denY)).setScale(4, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> dailyReturns(List<EquityPointResp> points) {
        List<BigDecimal> rets = new ArrayList<>();
        if (Objects.isNull(points) || points.size() < 2) {
            return rets;
        }
        for (int i = 1; i < points.size(); i++) {
            BigDecimal prev = points.get(i - 1).getEquity();
            BigDecimal curr = points.get(i).getEquity();
            if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                rets.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP));
            }
        }
        return rets;
    }

    private List<EquityPointResp> padEquityToBenchmark(List<EquityPointResp> paperEq,
                                                       List<EquityPointResp> benchEq,
                                                       BigDecimal initCash) {
        if (benchEq == null || benchEq.isEmpty()) {
            return paperEq;
        }
        Map<LocalDate, BigDecimal> paperMap = new HashMap<>();
        for (EquityPointResp point : paperEq) {
            if (Objects.nonNull(point.getTradeDate()) && Objects.nonNull(point.getEquity())) {
                paperMap.put(point.getTradeDate(), point.getEquity());
            }
        }
        BigDecimal cash = Objects.nonNull(initCash) ? initCash : BigDecimal.valueOf(1000000);
        BigDecimal last = cash;
        List<EquityPointResp> aligned = new ArrayList<>();
        for (EquityPointResp b : benchEq) {
            LocalDate d = b.getTradeDate();
            if (paperMap.containsKey(d)) {
                last = paperMap.get(d);
            }
            aligned.add(EquityPointResp.builder().tradeDate(d).equity(last).build());
        }
        return aligned;
    }

    private BigDecimal calcRollingBeta(List<EquityPointResp> paperEq, List<EquityPointResp> benchEq, int window) {
        Map<LocalDate, BigDecimal> paperMap = new HashMap<>();
        for (EquityPointResp point : paperEq) {
            paperMap.put(point.getTradeDate(), point.getEquity());
        }
        Map<LocalDate, BigDecimal> benchMap = new HashMap<>();
        for (EquityPointResp point : benchEq) {
            benchMap.put(point.getTradeDate(), point.getEquity());
        }
        List<LocalDate> dates = new ArrayList<>(paperMap.keySet());
        dates.retainAll(benchMap.keySet());
        dates.sort(LocalDate::compareTo);
        if (dates.size() < window + 1) {
            return calcBeta(paperEq, benchEq);
        }
        List<LocalDate> slice = dates.subList(dates.size() - (window + 1), dates.size());
        List<EquityPointResp> p = new ArrayList<>();
        List<EquityPointResp> b = new ArrayList<>();
        for (LocalDate d : slice) {
            p.add(EquityPointResp.builder().tradeDate(d).equity(paperMap.get(d)).build());
            b.add(EquityPointResp.builder().tradeDate(d).equity(benchMap.get(d)).build());
        }
        return calcBeta(p, b);
    }

    private BigDecimal calcBeta(List<EquityPointResp> paperEq, List<EquityPointResp> benchEq) {
        Map<LocalDate, BigDecimal> paperMap = new HashMap<>();
        for (EquityPointResp point : paperEq) {
            paperMap.put(point.getTradeDate(), point.getEquity());
        }
        Map<LocalDate, BigDecimal> benchMap = new HashMap<>();
        for (EquityPointResp point : benchEq) {
            benchMap.put(point.getTradeDate(), point.getEquity());
        }
        List<LocalDate> dates = new ArrayList<>(paperMap.keySet());
        dates.retainAll(benchMap.keySet());
        dates.sort(LocalDate::compareTo);
        if (dates.size() < 5) {
            return BigDecimal.ZERO;
        }
        List<Double> rp = new ArrayList<>();
        List<Double> rb = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            BigDecimal p0 = paperMap.get(dates.get(i - 1));
            BigDecimal p1 = paperMap.get(dates.get(i));
            BigDecimal b0 = benchMap.get(dates.get(i - 1));
            BigDecimal b1 = benchMap.get(dates.get(i));
            if (Objects.nonNull(p0) && p0.signum() > 0 && Objects.nonNull(p1)
                    && Objects.nonNull(b0) && b0.signum() > 0 && Objects.nonNull(b1)) {
                rp.add(p1.subtract(p0).divide(p0, 8, RoundingMode.HALF_UP).doubleValue());
                rb.add(b1.subtract(b0).divide(b0, 8, RoundingMode.HALF_UP).doubleValue());
            }
        }
        if (rp.size() < 3) {
            return BigDecimal.ZERO;
        }
        double meanP = rp.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanB = rb.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double cov = 0;
        double varB = 0;
        for (int i = 0; i < rp.size(); i++) {
            cov += (rp.get(i) - meanP) * (rb.get(i) - meanB);
            varB += (rb.get(i) - meanB) * (rb.get(i) - meanB);
        }
        if (varB == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(cov / varB).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 持仓暴露
     *
     * @param accountId 账户
     * @return 暴露
     */
    @Override
    public PaperExposureResp exposure(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        RiskOverviewResp risk = riskService.overview(id);
        List<PaperPosition> positions = listPositions(id);
        BigDecimal total = Objects.nonNull(risk.getTotalAsset()) ? risk.getTotalAsset() : BigDecimal.ZERO;
        BigDecimal cash = Objects.nonNull(risk.getCash()) ? risk.getCash() : BigDecimal.ZERO;
        List<PositionWeightResp> weights = new ArrayList<>();
        Map<String, BigDecimal> industryMv = new HashMap<>();
        Map<String, BigDecimal> industryPnl = new HashMap<>();
        BigDecimal hhi = BigDecimal.ZERO;
        for (PaperPosition position : positions) {
            BigDecimal mv = Objects.nonNull(position.getMarketValue()) ? position.getMarketValue() : BigDecimal.ZERO;
            BigDecimal w = total.signum() == 0 ? BigDecimal.ZERO : mv.divide(total, 6, RoundingMode.HALF_UP);
            hhi = hhi.add(w.multiply(w));
            String industry = "未分类";
            StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getCode, position.getCode())
                    .last("limit 1"));
            if (Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getIndustry())) {
                industry = basic.getIndustry();
            }
            industryMv.merge(industry, mv, BigDecimal::add);
            industryPnl.merge(industry, Objects.nonNull(position.getPnl()) ? position.getPnl() : BigDecimal.ZERO, BigDecimal::add);
            weights.add(PositionWeightResp.builder()
                    .code(position.getCode())
                    .name(position.getName())
                    .marketValue(mv)
                    .weight(w)
                    .pnl(position.getPnl())
                    .industry(industry)
                    .build());
        }
        weights.sort(Comparator.comparing(PositionWeightResp::getWeight).reversed());
        BigDecimal top1 = weights.isEmpty() ? BigDecimal.ZERO : weights.get(0).getWeight();
        BigDecimal top5 = BigDecimal.ZERO;
        for (int i = 0; i < Math.min(5, weights.size()); i++) {
            top5 = top5.add(weights.get(i).getWeight());
        }
        BigDecimal absPnlSum = BigDecimal.ZERO;
        for (BigDecimal pnl : industryPnl.values()) {
            absPnlSum = absPnlSum.add(Objects.nonNull(pnl) ? pnl.abs() : BigDecimal.ZERO);
        }
        BigDecimal absPosPnl = BigDecimal.ZERO;
        for (PositionWeightResp w : weights) {
            if (Objects.nonNull(w.getPnl())) {
                absPosPnl = absPosPnl.add(w.getPnl().abs());
            }
        }
        for (PositionWeightResp w : weights) {
            BigDecimal contrib = absPosPnl.signum() == 0 || Objects.isNull(w.getPnl()) ? BigDecimal.ZERO
                    : w.getPnl().abs().divide(absPosPnl, 4, RoundingMode.HALF_UP);
            w.setPnlContribution(contrib);
        }
        List<IndustryPnlResp> industries = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : industryMv.entrySet()) {
            BigDecimal w = total.signum() == 0 ? BigDecimal.ZERO
                    : entry.getValue().divide(total, 4, RoundingMode.HALF_UP);
            BigDecimal pnl = industryPnl.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal contrib = absPnlSum.signum() == 0 ? BigDecimal.ZERO
                    : pnl.abs().divide(absPnlSum, 4, RoundingMode.HALF_UP);
            industries.add(IndustryPnlResp.builder()
                    .industry(entry.getKey())
                    .marketValue(entry.getValue())
                    .pnl(pnl)
                    .weight(w)
                    .pnlContribution(contrib)
                    .build());
        }
        industries.sort(Comparator.comparing(IndustryPnlResp::getMarketValue).reversed());
        BigDecimal equityWeight = total.signum() == 0 ? BigDecimal.ZERO
                : total.subtract(cash).divide(total, 4, RoundingMode.HALF_UP);
        return PaperExposureResp.builder()
                .totalAsset(total)
                .cashWeight(total.signum() == 0 ? BigDecimal.ONE : cash.divide(total, 4, RoundingMode.HALF_UP))
                .equityWeight(equityWeight)
                .top1Weight(top1)
                .top5Weight(top5.setScale(4, RoundingMode.HALF_UP))
                .herfindahl(hhi.setScale(6, RoundingMode.HALF_UP))
                .industries(industries)
                .positions(weights)
                .build();
    }

    private PaperEquityCalculator.ReplayResult replayOrders(PaperAccount account,
                                                            List<PaperOrder> orders,
                                                            BigDecimal terminalEquity) {
        BigDecimal initCash = Objects.nonNull(account.getInitCash()) ? account.getInitCash() : BigDecimal.valueOf(1000000);
        if (orders.isEmpty()) {
            return PaperEquityCalculator.replay(initCash, orders, Map.of(), terminalEquity);
        }
        Set<String> codes = new HashSet<>();
        LocalDate minDate = null;
        for (PaperOrder order : orders) {
            if (StringUtils.isNotBlank(order.getCode())) {
                codes.add(order.getCode());
            }
            if (Objects.nonNull(order.getTradeDate())
                    && (Objects.isNull(minDate) || order.getTradeDate().isBefore(minDate))) {
                minDate = order.getTradeDate();
            }
        }
        Map<String, Map<LocalDate, BigDecimal>> closesByCode = new HashMap<>();
        if (!codes.isEmpty() && Objects.nonNull(minDate)) {
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, codes)
                    .ge(BarDaily::getTradeDate, minDate)
                    .orderByAsc(BarDaily::getTradeDate));
            for (BarDaily bar : bars) {
                if (Objects.isNull(bar.getClosePrice())) {
                    continue;
                }
                closesByCode.computeIfAbsent(bar.getCode(), k -> new HashMap<>())
                        .put(bar.getTradeDate(), bar.getClosePrice());
            }
        }
        return PaperEquityCalculator.replay(initCash, orders, closesByCode, terminalEquity);
    }

    /**
     * 平仓已触发止损/止盈
     *
     * @param accountId 账户
     * @param type      STOP/TAKE/BOTH
     * @return 订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PaperOrder> closeTriggered(Long accountId, String type) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        String mode = StringUtils.isNotBlank(type) ? type.toUpperCase() : "BOTH";
        List<PaperPosition> positions = listPositions(id);
        List<PaperOrder> orders = new ArrayList<>();
        for (PaperPosition position : positions) {
            BigDecimal price = position.getMarketPrice();
            if (Objects.isNull(price)) {
                continue;
            }
            boolean hitStop = Objects.nonNull(position.getStopLoss()) && price.compareTo(position.getStopLoss()) <= 0;
            boolean hitTake = Objects.nonNull(position.getTakeProfit()) && price.compareTo(position.getTakeProfit()) >= 0;
            boolean shouldClose = ("STOP".equals(mode) && hitStop)
                    || ("TAKE".equals(mode) && hitTake)
                    || ("BOTH".equals(mode) && (hitStop || hitTake));
            if (!shouldClose) {
                continue;
            }
            PaperOrderReq req = new PaperOrderReq();
            req.setAccountId(id);
            req.setCode(position.getCode());
            req.setSide("SELL");
            req.setQuantity(position.getQuantity());
            PaperOrder order = placeOrder(req);
            String tag = hitStop ? "止损触发" : "止盈触发";
            order.setReason(tag + " · " + (Objects.nonNull(order.getReason()) ? order.getReason() : ""));
            order.setUpdateTime(LocalDateTime.now());
            paperOrderMapper.updateById(order);
            orders.add(order);
        }
        return orders;
    }

    /**
     * 高分 BUY 信号批量买入建议
     *
     * @param accountId 账户
     * @param limit     条数
     * @param minScore  最低分
     * @return 建议
     */
    @Override
    public RebalanceSuggestResp signalBuySuggest(Long accountId, Integer limit, BigDecimal minScore) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        int n = Objects.nonNull(limit) ? Math.max(1, Math.min(limit, 20)) : 5;
        BigDecimal scoreFloor = Objects.nonNull(minScore) ? minScore : new BigDecimal("70");
        List<StrategySignalEntity> signals = strategySignalMapper.selectList(Wrappers.<StrategySignalEntity>lambdaQuery()
                .eq(StrategySignalEntity::getSide, "BUY")
                .ge(StrategySignalEntity::getScore, scoreFloor)
                .ge(StrategySignalEntity::getSignalDate, LocalDate.now().minusDays(5))
                .orderByDesc(StrategySignalEntity::getScore)
                .orderByDesc(StrategySignalEntity::getId)
                .last("limit 80"));
        Map<String, StrategySignalEntity> unique = new HashMap<>();
        for (StrategySignalEntity signal : signals) {
            unique.putIfAbsent(signal.getCode(), signal);
            if (unique.size() >= n) {
                break;
            }
        }
        List<String> codes = new ArrayList<>(unique.keySet());
        List<RebalanceOrderSuggest> orders = new ArrayList<>();
        for (String code : codes) {
            PositionSuggestResp suggest = suggestPosition(id, code, null);
            if (Objects.isNull(suggest.getSuggestedQuantity()) || suggest.getSuggestedQuantity() <= 0) {
                continue;
            }
            StrategySignalEntity signal = unique.get(code);
            orders.add(RebalanceOrderSuggest.builder()
                    .code(code)
                    .side("BUY")
                    .quantity(suggest.getSuggestedQuantity())
                    .price(suggest.getPrice())
                    .currentWeight(BigDecimal.ZERO)
                    .targetWeight(suggest.getTargetWeight())
                    .reason("信号#" + signal.getId() + " " + signal.getStrategyId() + " 分=" + signal.getScore())
                    .build());
        }
        return RebalanceSuggestResp.builder()
                .targetCodes(codes)
                .targetWeight(orders.isEmpty() ? BigDecimal.ZERO : orders.get(0).getTargetWeight())
                .orders(orders)
                .message("近5日高分BUY建议 " + orders.size() + " 笔（评分≥" + scoreFloor + "），需人工确认")
                .build();
    }

    /**
     * 纸面月度收益
     *
     * @param accountId 账户
     * @return 月度收益
     */
    @Override
    public List<MonthlyReturnResp> monthlyReturns(Long accountId) {
        PaperAccount account = Objects.nonNull(accountId) ? paperAccountMapper.selectById(accountId) : defaultAccount();
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        List<PaperOrder> orders = listOrders(account.getId());
        RiskOverviewResp risk = riskService.overview(account.getId());
        PaperEquityCalculator.ReplayResult replay = replayOrders(account, orders, risk.getTotalAsset());
        List<EquityPointResp> points = replay.getPoints();
        List<MonthlyReturnResp> list = new ArrayList<>();
        if (points.size() < 2) {
            return list;
        }
        Map<YearMonth, EquityPointResp> monthEnd = new LinkedHashMap<>();
        for (EquityPointResp point : points) {
            if (Objects.isNull(point.getTradeDate()) || Objects.isNull(point.getEquity())) {
                continue;
            }
            monthEnd.put(YearMonth.from(point.getTradeDate()), point);
        }
        BigDecimal prev = null;
        for (Map.Entry<YearMonth, EquityPointResp> entry : monthEnd.entrySet()) {
            BigDecimal endEq = entry.getValue().getEquity();
            BigDecimal ret = BigDecimal.ZERO;
            if (Objects.nonNull(prev) && prev.signum() > 0) {
                ret = endEq.subtract(prev).divide(prev, 6, RoundingMode.HALF_UP);
            }
            list.add(MonthlyReturnResp.builder()
                    .month(entry.getKey().toString())
                    .monthReturn(ret)
                    .endEquity(endEq)
                    .build());
            prev = endEq;
        }
        return list;
    }

    /**
     * 股票池等权再平衡建议
     *
     * @param accountId 账户
     * @param limit     成分数
     * @return 建议
     */
    @Override
    public RebalanceSuggestResp rebalanceSuggest(Long accountId, Integer limit) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        int n = Objects.nonNull(limit) ? Math.max(2, Math.min(limit, 20)) : 8;
        List<UniverseSnapshot> universe = universeService.latest();
        List<String> targets = new ArrayList<>();
        for (UniverseSnapshot item : universe) {
            targets.add(item.getCode());
            if (targets.size() >= n) {
                break;
            }
        }
        if (targets.size() < 2) {
            throw new BusinessException("股票池不足，请先刷新股票池");
        }
        BigDecimal targetW = BigDecimal.ONE.divide(BigDecimal.valueOf(targets.size()), 6, RoundingMode.HALF_UP);
        RiskOverviewResp risk = riskService.overview(id);
        BigDecimal total = risk.getTotalAsset();
        List<PaperPosition> positions = listPositions(id);
        Map<String, PaperPosition> posMap = new HashMap<>();
        for (PaperPosition position : positions) {
            posMap.put(position.getCode(), position);
        }
        List<RebalanceOrderSuggest> orders = new ArrayList<>();
        // 不在目标内的持仓建议卖出
        for (PaperPosition position : positions) {
            if (!targets.contains(position.getCode())) {
                BigDecimal mv = Objects.nonNull(position.getMarketValue()) ? position.getMarketValue() : BigDecimal.ZERO;
                orders.add(RebalanceOrderSuggest.builder()
                        .code(position.getCode())
                        .side("SELL")
                        .quantity(position.getQuantity())
                        .price(position.getMarketPrice())
                        .currentWeight(total.signum() == 0 ? BigDecimal.ZERO : mv.divide(total, 4, RoundingMode.HALF_UP))
                        .targetWeight(BigDecimal.ZERO)
                        .reason("不在目标成分，建议清仓")
                        .build());
            }
        }
        for (String code : targets) {
            BarDaily bar = resolveBar(code, null);
            BigDecimal price = bar.getClosePrice();
            BigDecimal targetMv = total.multiply(targetW);
            int targetQty = targetMv.divide(price.multiply(BigDecimal.valueOf(100)), 0, RoundingMode.DOWN).intValue() * 100;
            PaperPosition cur = posMap.get(code);
            int curQty = Objects.nonNull(cur) && Objects.nonNull(cur.getQuantity()) ? cur.getQuantity() : 0;
            BigDecimal curW = Objects.nonNull(cur) && Objects.nonNull(cur.getMarketValue()) && total.signum() > 0
                    ? cur.getMarketValue().divide(total, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            int diff = targetQty - curQty;
            if (Math.abs(diff) < 100) {
                continue;
            }
            orders.add(RebalanceOrderSuggest.builder()
                    .code(code)
                    .side(diff > 0 ? "BUY" : "SELL")
                    .quantity(Math.abs(diff))
                    .price(price)
                    .currentWeight(curW)
                    .targetWeight(targetW)
                    .reason(diff > 0 ? "低配补齐至等权" : "超配减至等权")
                    .build());
        }
        return RebalanceSuggestResp.builder()
                .targetCodes(targets)
                .targetWeight(targetW)
                .orders(orders)
                .message("等权再平衡建议 n=" + targets.size() + "，需人工确认后下单")
                .build();
    }

    /**
     * 持仓周期分桶
     *
     * @param accountId 账户
     * @return 分桶
     */
    @Override
    public HoldBucketResp holdBuckets(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        List<ClosedTradeSnap> trades = matchClosedTrades(listOrders(id));
        String[] labels = {"1-5日", "6-20日", "21-60日", "60日+"};
        int[] counts = new int[4];
        int[] wins = new int[4];
        BigDecimal[] sumRet = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (ClosedTradeSnap trade : trades) {
            int idx;
            if (trade.holdDays <= 5) {
                idx = 0;
            } else if (trade.holdDays <= 20) {
                idx = 1;
            } else if (trade.holdDays <= 60) {
                idx = 2;
            } else {
                idx = 3;
            }
            counts[idx]++;
            if (trade.ret.signum() >= 0) {
                wins[idx]++;
            }
            sumRet[idx] = sumRet[idx].add(trade.ret);
        }
        List<HoldBucketItem> buckets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BigDecimal wr = counts[i] == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(wins[i]).divide(BigDecimal.valueOf(counts[i]), 4, RoundingMode.HALF_UP);
            BigDecimal avg = counts[i] == 0 ? BigDecimal.ZERO
                    : sumRet[i].divide(BigDecimal.valueOf(counts[i]), 6, RoundingMode.HALF_UP);
            buckets.add(HoldBucketItem.builder()
                    .bucket(labels[i])
                    .tradeCount(counts[i])
                    .winRate(wr)
                    .avgReturn(avg)
                    .totalReturn(sumRet[i].setScale(6, RoundingMode.HALF_UP))
                    .build());
        }
        return HoldBucketResp.builder()
                .sampleCount(trades.size())
                .message(trades.isEmpty() ? "无闭合交易样本" : "按持仓日历日分桶的闭合盈亏")
                .buckets(buckets)
                .build();
    }

    /**
     * 周几盈亏
     *
     * @param accountId 账户
     * @return 分布
     */
    @Override
    public WeekdayPnlResp weekdayPnl(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        List<ClosedTradeSnap> trades = matchClosedTrades(listOrders(id));
        String[] labels = {"周一", "周二", "周三", "周四", "周五"};
        int[] counts = new int[5];
        int[] wins = new int[5];
        BigDecimal[] sumRet = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (ClosedTradeSnap trade : trades) {
            int wd = trade.sellWeekday;
            if (wd < 1 || wd > 5) {
                continue;
            }
            int idx = wd - 1;
            counts[idx]++;
            if (trade.ret.signum() >= 0) {
                wins[idx]++;
            }
            sumRet[idx] = sumRet[idx].add(trade.ret);
        }
        List<WeekdayPnlItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BigDecimal wr = counts[i] == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(wins[i]).divide(BigDecimal.valueOf(counts[i]), 4, RoundingMode.HALF_UP);
            BigDecimal avg = counts[i] == 0 ? BigDecimal.ZERO
                    : sumRet[i].divide(BigDecimal.valueOf(counts[i]), 6, RoundingMode.HALF_UP);
            items.add(WeekdayPnlItem.builder()
                    .weekday(i + 1)
                    .label(labels[i])
                    .tradeCount(counts[i])
                    .winRate(wr)
                    .avgReturn(avg)
                    .sumReturn(sumRet[i].setScale(6, RoundingMode.HALF_UP))
                    .build());
        }
        return WeekdayPnlResp.builder()
                .sampleCount(trades.size())
                .message(trades.isEmpty() ? "无闭合交易样本" : "按卖出日星期几统计闭合收益")
                .items(items)
                .build();
    }

    /**
     * 蒙特卡洛
     *
     * @param accountId   账户
     * @param paths       路径
     * @param horizonDays 长度
     * @return 分布
     */
    @Override
    public MonteCarloResp monteCarlo(Long accountId, Integer paths, Integer horizonDays) {
        PaperAccount account = Objects.nonNull(accountId) ? paperAccountMapper.selectById(accountId) : defaultAccount();
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        RiskOverviewResp risk = riskService.overview(account.getId());
        PaperEquityCalculator.ReplayResult replay = replayOrders(account, listOrders(account.getId()), risk.getTotalAsset());
        List<BigDecimal> hist = dailyReturns(replay.getPoints());
        int pathN = Objects.nonNull(paths) ? Math.max(50, Math.min(paths, 2000)) : 500;
        int horizon = Objects.nonNull(horizonDays) ? Math.max(5, Math.min(horizonDays, 120)) : 20;
        if (hist.size() < 5) {
            return MonteCarloResp.builder()
                    .sampleDays(hist.size())
                    .paths(pathN)
                    .horizonDays(horizon)
                    .terminalReturnP5(BigDecimal.ZERO)
                    .terminalReturnP50(BigDecimal.ZERO)
                    .terminalReturnP95(BigDecimal.ZERO)
                    .avgMaxDrawdown(BigDecimal.ZERO)
                    .maxDrawdownP95(BigDecimal.ZERO)
                    .message("权益日收益样本不足，无法蒙特卡洛")
                    .build();
        }
        Random random = new Random(42L);
        List<BigDecimal> terminals = new ArrayList<>();
        List<BigDecimal> maxDds = new ArrayList<>();
        for (int p = 0; p < pathN; p++) {
            BigDecimal equity = BigDecimal.ONE;
            BigDecimal peak = BigDecimal.ONE;
            BigDecimal maxDd = BigDecimal.ZERO;
            for (int d = 0; d < horizon; d++) {
                BigDecimal r = hist.get(random.nextInt(hist.size()));
                equity = equity.multiply(BigDecimal.ONE.add(r));
                if (equity.compareTo(peak) > 0) {
                    peak = equity;
                }
                if (peak.signum() > 0) {
                    BigDecimal dd = peak.subtract(equity).divide(peak, 8, RoundingMode.HALF_UP);
                    if (dd.compareTo(maxDd) > 0) {
                        maxDd = dd;
                    }
                }
            }
            terminals.add(equity.subtract(BigDecimal.ONE));
            maxDds.add(maxDd);
        }
        terminals.sort(BigDecimal::compareTo);
        maxDds.sort(BigDecimal::compareTo);
        BigDecimal avgDd = maxDds.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(maxDds.size()), 6, RoundingMode.HALF_UP);
        return MonteCarloResp.builder()
                .sampleDays(hist.size())
                .paths(pathN)
                .horizonDays(horizon)
                .terminalReturnP5(percentile(terminals, 0.05))
                .terminalReturnP50(percentile(terminals, 0.50))
                .terminalReturnP95(percentile(terminals, 0.95))
                .avgMaxDrawdown(avgDd)
                .maxDrawdownP95(percentile(maxDds, 0.95))
                .message("Bootstrap 历史日收益 " + pathN + " 路径 × " + horizon + " 日")
                .build();
    }

    /**
     * 因子暴露
     *
     * @param accountId 账户
     * @return 暴露
     */
    @Override
    public FactorExposureResp factorExposure(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        RiskOverviewResp risk = riskService.overview(id);
        BigDecimal total = Objects.nonNull(risk.getTotalAsset()) ? risk.getTotalAsset() : BigDecimal.ZERO;
        BigDecimal cash = Objects.nonNull(risk.getCash()) ? risk.getCash() : BigDecimal.ZERO;
        BigDecimal cashW = total.signum() == 0 ? BigDecimal.ONE : cash.divide(total, 6, RoundingMode.HALF_UP);
        BigDecimal stockW = BigDecimal.ONE.subtract(cashW).max(BigDecimal.ZERO);
        List<BarDaily> hsBars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, "000300")
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit 25"));
        BigDecimal hsRet20 = calcRetN(hsBars, 20);
        List<FactorExposureItem> items = new ArrayList<>();
        BigDecimal wMom = BigDecimal.ZERO;
        BigDecimal wVol = BigDecimal.ZERO;
        BigDecimal wRs = BigDecimal.ZERO;
        BigDecimal wSum = BigDecimal.ZERO;
        for (PaperPosition position : listPositions(id)) {
            BigDecimal mv = Objects.nonNull(position.getMarketValue()) ? position.getMarketValue() : BigDecimal.ZERO;
            BigDecimal w = total.signum() == 0 ? BigDecimal.ZERO : mv.divide(total, 6, RoundingMode.HALF_UP);
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, position.getCode())
                    .orderByDesc(BarDaily::getTradeDate)
                    .last("limit 25"));
            BigDecimal mom = calcRetN(bars, 20);
            BigDecimal vol = calcVolN(bars, 20);
            BigDecimal rs = mom.subtract(hsRet20);
            items.add(FactorExposureItem.builder()
                    .code(position.getCode())
                    .weight(w)
                    .momentum20(mom)
                    .volatility20(vol)
                    .rs20VsHs300(rs)
                    .build());
            wMom = wMom.add(mom.multiply(w));
            wVol = wVol.add(vol.multiply(w));
            wRs = wRs.add(rs.multiply(w));
            wSum = wSum.add(w);
        }
        if (wSum.signum() > 0) {
            // 股票权重内已加权；组合层再按股票仓位缩放动量等
            wMom = wMom.setScale(4, RoundingMode.HALF_UP);
            wVol = wVol.setScale(4, RoundingMode.HALF_UP);
            wRs = wRs.setScale(4, RoundingMode.HALF_UP);
        }
        items.sort(Comparator.comparing(FactorExposureItem::getWeight).reversed());
        return FactorExposureResp.builder()
                .momentum20(wMom)
                .volatility20(wVol)
                .rs20VsHs300(wRs)
                .cashWeight(cashW.setScale(4, RoundingMode.HALF_UP))
                .stockWeight(stockW.setScale(4, RoundingMode.HALF_UP))
                .message(items.isEmpty() ? "无持仓" : "市值加权动量/波动/相对强度（%）")
                .items(items)
                .build();
    }

    /**
     * ATR 止损建议
     *
     * @param accountId 账户
     * @return 建议
     */
    @Override
    public AtrStopResp atrStopSuggest(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        BigDecimal stopMult = configService.getDecimal("atr_stop_mult", new BigDecimal("2.0"));
        BigDecimal takeMult = configService.getDecimal("atr_take_mult", new BigDecimal("3.0"));
        List<AtrStopItem> items = new ArrayList<>();
        for (PaperPosition position : listPositions(id)) {
            BigDecimal atr = calcAtr14(position.getCode());
            BigDecimal base = Objects.nonNull(position.getCostPrice()) && position.getCostPrice().signum() > 0
                    ? position.getCostPrice()
                    : (Objects.nonNull(position.getMarketPrice()) ? position.getMarketPrice() : BigDecimal.ZERO);
            BigDecimal stop = null;
            BigDecimal take = null;
            if (atr.signum() > 0 && base.signum() > 0) {
                stop = base.subtract(atr.multiply(stopMult)).setScale(4, RoundingMode.HALF_UP);
                take = base.add(atr.multiply(takeMult)).setScale(4, RoundingMode.HALF_UP);
                if (stop.signum() <= 0) {
                    stop = base.multiply(new BigDecimal("0.92")).setScale(4, RoundingMode.HALF_UP);
                }
            }
            items.add(AtrStopItem.builder()
                    .code(position.getCode())
                    .name(position.getName())
                    .costPrice(position.getCostPrice())
                    .marketPrice(position.getMarketPrice())
                    .atr14(atr)
                    .suggestedStopLoss(stop)
                    .suggestedTakeProfit(take)
                    .currentStopLoss(position.getStopLoss())
                    .currentTakeProfit(position.getTakeProfit())
                    .build());
        }
        return AtrStopResp.builder()
                .stopMult(stopMult)
                .takeMult(takeMult)
                .message(items.isEmpty() ? "无持仓" : "ATR14 × 止损/止盈倍数（配置 atr_stop_mult / atr_take_mult）")
                .items(items)
                .build();
    }

    /**
     * 权益曲线质量
     *
     * @param accountId 账户
     * @return 质量
     */
    @Override
    public EquityQualityResp equityQuality(Long accountId) {
        PaperAccount account = Objects.nonNull(accountId) ? paperAccountMapper.selectById(accountId) : defaultAccount();
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        RiskOverviewResp risk = riskService.overview(account.getId());
        List<EquityPointResp> points = replayOrders(account, listOrders(account.getId()), risk.getTotalAsset()).getPoints();
        List<BigDecimal> rets = dailyReturns(points);
        if (rets.size() < 5 || points.size() < 2) {
            return EquityQualityResp.builder()
                    .sampleDays(rets.size())
                    .pathEfficiency(BigDecimal.ZERO)
                    .returnAutocorr1(BigDecimal.ZERO)
                    .upDayRatio(BigDecimal.ZERO)
                    .message("权益样本不足")
                    .build();
        }
        BigDecimal first = points.get(0).getEquity();
        BigDecimal last = points.get(points.size() - 1).getEquity();
        BigDecimal net = last.subtract(first).abs();
        BigDecimal path = BigDecimal.ZERO;
        for (int i = 1; i < points.size(); i++) {
            BigDecimal a = points.get(i - 1).getEquity();
            BigDecimal b = points.get(i).getEquity();
            if (Objects.nonNull(a) && Objects.nonNull(b)) {
                path = path.add(a.subtract(b).abs());
            }
        }
        BigDecimal eff = path.signum() == 0 ? BigDecimal.ZERO : net.divide(path, 4, RoundingMode.HALF_UP);
        if (eff.compareTo(BigDecimal.ONE) > 0) {
            eff = BigDecimal.ONE;
        }
        int up = 0;
        for (BigDecimal r : rets) {
            if (r.signum() > 0) {
                up++;
            }
        }
        BigDecimal upRatio = BigDecimal.valueOf(up).divide(BigDecimal.valueOf(rets.size()), 4, RoundingMode.HALF_UP);
        BigDecimal ac1 = BigDecimal.ZERO;
        if (rets.size() >= 6) {
            List<Double> xs = new ArrayList<>();
            List<Double> ys = new ArrayList<>();
            for (int i = 1; i < rets.size(); i++) {
                xs.add(rets.get(i - 1).doubleValue());
                ys.add(rets.get(i).doubleValue());
            }
            ac1 = pearsonCorr(xs, ys);
        }
        return EquityQualityResp.builder()
                .sampleDays(rets.size())
                .pathEfficiency(eff)
                .returnAutocorr1(ac1)
                .upDayRatio(upRatio)
                .message("路径效率 " + eff + " · 涨日占比 " + upRatio + " · 自相关 " + ac1)
                .build();
    }

    /**
     * 模拟盘健康分
     *
     * @param accountId 账户
     * @return 健康分
     */
    @Override
    public PaperHealthResp healthScore(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        List<String> factors = new ArrayList<>();
        int score = 60;
        StopCoverageResp stop = stopCoverage(id);
        if (Objects.nonNull(stop.getStopCoverage())) {
            int add = stop.getStopCoverage().multiply(BigDecimal.valueOf(20)).intValue();
            score += add;
            factors.add("止损覆盖 +" + add);
        }
        KellySuggestResp kelly = kellySuggest(id);
        if (Objects.nonNull(kelly.getHalfKelly()) && kelly.getHalfKelly().signum() > 0) {
            score += 5;
            factors.add("Kelly 可用 +5");
        } else {
            score -= 5;
            factors.add("Kelly 样本不足 -5");
        }
        FillQualityResp fill = fillQuality(id, 20);
        if (Objects.nonNull(fill.getQualityScore())) {
            if (fill.getQualityScore().compareTo(new BigDecimal("80")) >= 0) {
                score += 8;
                factors.add("成交质量优 +8");
            } else if (fill.getQualityScore().compareTo(new BigDecimal("60")) < 0) {
                score -= 8;
                factors.add("成交质量弱 -8");
            }
        }
        RiskOverviewResp risk = riskService.overview(id);
        if (Objects.nonNull(risk.getCriticalCount()) && risk.getCriticalCount() > 0) {
            score -= 15;
            factors.add("CRITICAL 风控 -15");
        } else if (Objects.nonNull(risk.getWarnCount()) && risk.getWarnCount() > 0) {
            score -= 5;
            factors.add("WARN 风控 -5");
        } else {
            score += 5;
            factors.add("风控清洁 +5");
        }
        GapRiskResp gap = gapRisk(id);
        if (Objects.nonNull(gap.getMaxAbsGapPct()) && gap.getMaxAbsGapPct().compareTo(new BigDecimal("5")) >= 0) {
            score -= 5;
            factors.add("缺口风险高 -5");
        }
        if (score > 100) {
            score = 100;
        }
        if (score < 0) {
            score = 0;
        }
        String grade;
        if (score >= 85) {
            grade = "A";
        } else if (score >= 70) {
            grade = "B";
        } else if (score >= 55) {
            grade = "C";
        } else {
            grade = "D";
        }
        return PaperHealthResp.builder()
                .score(BigDecimal.valueOf(score))
                .grade(grade)
                .factors(factors)
                .message("模拟盘健康分 " + score + "（" + grade + "）")
                .build();
    }

    /**
     * 止损覆盖率
     *
     * @param accountId 账户
     * @return 覆盖
     */
    @Override
    public StopCoverageResp stopCoverage(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        List<PaperPosition> positions = listPositions(id);
        int withStop = 0;
        int withTake = 0;
        BigDecimal distSum = BigDecimal.ZERO;
        int distN = 0;
        for (PaperPosition position : positions) {
            if (Objects.nonNull(position.getStopLoss()) && position.getStopLoss().signum() > 0) {
                withStop++;
                BigDecimal px = Objects.nonNull(position.getMarketPrice()) ? position.getMarketPrice() : position.getCostPrice();
                if (Objects.nonNull(px) && px.signum() > 0) {
                    BigDecimal dist = px.subtract(position.getStopLoss()).divide(px, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    distSum = distSum.add(dist);
                    distN++;
                }
            }
            if (Objects.nonNull(position.getTakeProfit()) && position.getTakeProfit().signum() > 0) {
                withTake++;
            }
        }
        int n = positions.size();
        BigDecimal stopCov = n == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(withStop).divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        BigDecimal takeCov = n == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(withTake).divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        BigDecimal avgDist = distN == 0 ? BigDecimal.ZERO
                : distSum.divide(BigDecimal.valueOf(distN), 2, RoundingMode.HALF_UP);
        return StopCoverageResp.builder()
                .positionCount(n)
                .withStopLoss(withStop)
                .withTakeProfit(withTake)
                .stopCoverage(stopCov)
                .takeCoverage(takeCov)
                .avgStopDistancePct(avgDist)
                .message(n == 0 ? "无持仓" : "止损覆盖 " + withStop + "/" + n + " · 均距止损 " + avgDist + "%")
                .build();
    }

    /**
     * Beta 目标
     *
     * @param accountId 账户
     * @return 建议
     */
    @Override
    public BetaTargetResp betaTarget(Long accountId) {
        PaperAccount account = Objects.nonNull(accountId) ? paperAccountMapper.selectById(accountId) : defaultAccount();
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        BigDecimal target = configService.getDecimal("target_beta", new BigDecimal("1.0"));
        RiskOverviewResp risk = riskService.overview(account.getId());
        PaperPerformanceResp perf = performance(account.getId(), "000300", "000905");
        BigDecimal current = Objects.nonNull(perf.getBeta()) ? perf.getBeta() : BigDecimal.ZERO;
        BigDecimal scale = BigDecimal.ONE;
        boolean betaUsable = current.abs().compareTo(new BigDecimal("0.05")) >= 0;
        if (betaUsable && target.signum() > 0) {
            scale = target.divide(current.abs(), 4, RoundingMode.HALF_UP);
            if (scale.compareTo(new BigDecimal("1.5")) > 0) {
                scale = new BigDecimal("1.5");
            }
            if (scale.signum() < 0) {
                scale = BigDecimal.ZERO;
            }
        }
        BigDecimal curPos = Objects.nonNull(risk.getPositionRatio()) ? risk.getPositionRatio() : BigDecimal.ZERO;
        BigDecimal limit = Objects.nonNull(risk.getTotalLimit()) ? risk.getTotalLimit() : new BigDecimal("0.80");
        BigDecimal suggested = curPos.multiply(scale).min(limit).setScale(4, RoundingMode.HALF_UP);
        String msg;
        if (!betaUsable) {
            msg = "当前 Beta 近似 0（样本短或现金拖累高），暂不缩放";
            scale = BigDecimal.ONE;
            suggested = curPos;
        } else if (scale.compareTo(BigDecimal.ONE) < 0) {
            msg = "Beta 高于目标，建议降仓至 " + suggested;
        } else if (scale.compareTo(BigDecimal.ONE) > 0) {
            msg = "Beta 低于目标，可升至 " + suggested;
        } else {
            msg = "Beta 接近目标";
        }
        return BetaTargetResp.builder()
                .targetBeta(target)
                .currentBeta(current)
                .scale(scale)
                .currentPositionRatio(curPos.setScale(4, RoundingMode.HALF_UP))
                .suggestedPositionRatio(suggested)
                .message(msg)
                .build();
    }

    /**
     * 成交日历
     *
     * @param accountId 账户
     * @param days      天数
     * @return 日历
     */
    @Override
    public TradeCalendarResp tradeCalendar(Long accountId, Integer days) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        int lookback = Objects.nonNull(days) ? Math.max(7, Math.min(days, 180)) : 60;
        LocalDate begin = LocalDate.now().minusDays(lookback);
        Map<LocalDate, TradeCalendarDay> map = new LinkedHashMap<>();
        for (PaperOrder order : listOrders(id)) {
            if (Objects.isNull(order.getTradeDate()) || order.getTradeDate().isBefore(begin)) {
                continue;
            }
            TradeCalendarDay day = map.computeIfAbsent(order.getTradeDate(), d -> TradeCalendarDay.builder()
                    .tradeDate(d)
                    .buyCount(0)
                    .sellCount(0)
                    .turnover(BigDecimal.ZERO)
                    .fee(BigDecimal.ZERO)
                    .netBuyAmount(BigDecimal.ZERO)
                    .build());
            BigDecimal amt = Objects.nonNull(order.getAmount()) ? order.getAmount() : BigDecimal.ZERO;
            BigDecimal fee = Objects.nonNull(order.getFee()) ? order.getFee() : BigDecimal.ZERO;
            day.setTurnover(day.getTurnover().add(amt));
            day.setFee(day.getFee().add(fee));
            if ("BUY".equalsIgnoreCase(order.getSide())) {
                day.setBuyCount(day.getBuyCount() + 1);
                day.setNetBuyAmount(day.getNetBuyAmount().add(amt));
            } else if ("SELL".equalsIgnoreCase(order.getSide())) {
                day.setSellCount(day.getSellCount() + 1);
                day.setNetBuyAmount(day.getNetBuyAmount().subtract(amt));
            }
        }
        List<TradeCalendarDay> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparing(TradeCalendarDay::getTradeDate).reversed());
        return TradeCalendarResp.builder()
                .days(lookback)
                .message(list.isEmpty() ? "近" + lookback + "日无成交" : "近" + lookback + "日成交日历，共 " + list.size() + " 个交易日")
                .daysList(list)
                .build();
    }

    /**
     * 波动目标缩放
     *
     * @param accountId 账户
     * @return 建议
     */
    @Override
    public VolTargetResp volTarget(Long accountId) {
        PaperAccount account = Objects.nonNull(accountId) ? paperAccountMapper.selectById(accountId) : defaultAccount();
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        RiskOverviewResp risk = riskService.overview(account.getId());
        BigDecimal target = configService.getDecimal("target_ann_vol", new BigDecimal("0.15"));
        PaperEquityCalculator.ReplayResult replay = replayOrders(account, listOrders(account.getId()), risk.getTotalAsset());
        List<BigDecimal> rets = dailyReturns(replay.getPoints());
        BigDecimal realized = BigDecimal.ZERO;
        if (rets.size() >= 5) {
            BigDecimal mean = rets.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(rets.size()), 8, RoundingMode.HALF_UP);
            BigDecimal var = BigDecimal.ZERO;
            for (BigDecimal r : rets) {
                BigDecimal d = r.subtract(mean);
                var = var.add(d.multiply(d));
            }
            var = var.divide(BigDecimal.valueOf(rets.size() - 1), 8, RoundingMode.HALF_UP);
            realized = BigDecimal.valueOf(Math.sqrt(var.doubleValue()) * Math.sqrt(252)).setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal scale = BigDecimal.ONE;
        if (realized.signum() > 0 && target.signum() > 0) {
            scale = target.divide(realized, 4, RoundingMode.HALF_UP);
            if (scale.compareTo(new BigDecimal("1.5")) > 0) {
                scale = new BigDecimal("1.5");
            }
            if (scale.signum() < 0) {
                scale = BigDecimal.ZERO;
            }
        }
        BigDecimal cur = Objects.nonNull(risk.getPositionRatio()) ? risk.getPositionRatio() : BigDecimal.ZERO;
        BigDecimal limit = Objects.nonNull(risk.getTotalLimit()) ? risk.getTotalLimit() : new BigDecimal("0.80");
        BigDecimal suggested = cur.multiply(scale).min(limit).setScale(4, RoundingMode.HALF_UP);
        String msg;
        if (rets.size() < 5) {
            msg = "权益样本不足，暂不缩放";
            scale = BigDecimal.ONE;
            suggested = cur;
        } else if (scale.compareTo(BigDecimal.ONE) < 0) {
            msg = "实现波动高于目标，建议降仓至 " + suggested;
        } else if (scale.compareTo(BigDecimal.ONE) > 0) {
            msg = "实现波动低于目标，可升至 " + suggested + "（不超过总仓上限）";
        } else {
            msg = "波动接近目标，维持仓位";
        }
        return VolTargetResp.builder()
                .targetAnnVol(target)
                .realizedAnnVol(realized)
                .scale(scale)
                .currentPositionRatio(cur.setScale(4, RoundingMode.HALF_UP))
                .suggestedPositionRatio(suggested)
                .message(msg)
                .build();
    }

    /**
     * 闭合收益分布
     *
     * @param accountId 账户
     * @return 直方
     */
    @Override
    public ReturnHistResp returnHistogram(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        List<ClosedTradeSnap> trades = matchClosedTrades(listOrders(id));
        String[] labels = {"<-10%", "-10~-5%", "-5~0%", "0~5%", "5~10%", ">10%"};
        int[] counts = new int[6];
        for (ClosedTradeSnap trade : trades) {
            double pct = trade.ret.doubleValue() * 100;
            int idx;
            if (pct < -10) {
                idx = 0;
            } else if (pct < -5) {
                idx = 1;
            } else if (pct < 0) {
                idx = 2;
            } else if (pct < 5) {
                idx = 3;
            } else if (pct < 10) {
                idx = 4;
            } else {
                idx = 5;
            }
            counts[idx]++;
        }
        List<ReturnHistItem> buckets = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            buckets.add(ReturnHistItem.builder().bucket(labels[i]).count(counts[i]).build());
        }
        return ReturnHistResp.builder()
                .sampleCount(trades.size())
                .message(trades.isEmpty() ? "无闭合交易" : "闭合交易单笔收益率分布")
                .buckets(buckets)
                .build();
    }

    /**
     * 应用 ATR 止损
     *
     * @param accountId 账户
     * @return 更新数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer applyAtrStops(Long accountId) {
        Long id = Objects.nonNull(accountId) ? accountId : defaultAccount().getId();
        AtrStopResp suggest = atrStopSuggest(id);
        int updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (AtrStopItem item : suggest.getItems()) {
            if (Objects.isNull(item.getSuggestedStopLoss()) || Objects.isNull(item.getSuggestedTakeProfit())) {
                continue;
            }
            PaperPosition position = paperPositionMapper.selectOne(Wrappers.<PaperPosition>lambdaQuery()
                    .eq(PaperPosition::getAccountId, id)
                    .eq(PaperPosition::getCode, item.getCode())
                    .last("limit 1"));
            if (Objects.isNull(position)) {
                continue;
            }
            position.setStopLoss(item.getSuggestedStopLoss());
            position.setTakeProfit(item.getSuggestedTakeProfit());
            position.setUpdateTime(now);
            paperPositionMapper.updateById(position);
            updated++;
        }
        return updated;
    }

    private BigDecimal calcAtr14(String code) {
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit 20"));
        if (bars.size() < 15) {
            return BigDecimal.ZERO;
        }
        List<BarDaily> asc = new ArrayList<>(bars);
        asc.sort(Comparator.comparing(BarDaily::getTradeDate));
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (int i = 1; i < asc.size() && n < 14; i++) {
            BarDaily cur = asc.get(i);
            BarDaily prev = asc.get(i - 1);
            if (Objects.isNull(cur.getHighPrice()) || Objects.isNull(cur.getLowPrice())
                    || Objects.isNull(prev.getClosePrice())) {
                continue;
            }
            BigDecimal tr1 = cur.getHighPrice().subtract(cur.getLowPrice());
            BigDecimal tr2 = cur.getHighPrice().subtract(prev.getClosePrice()).abs();
            BigDecimal tr3 = cur.getLowPrice().subtract(prev.getClosePrice()).abs();
            BigDecimal tr = tr1.max(tr2).max(tr3);
            sum = sum.add(tr);
            n++;
        }
        if (n == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calcRetN(List<BarDaily> descBars, int n) {
        if (descBars == null || descBars.size() <= n) {
            return BigDecimal.ZERO;
        }
        BigDecimal latest = descBars.get(0).getClosePrice();
        BigDecimal base = descBars.get(n).getClosePrice();
        if (Objects.isNull(latest) || Objects.isNull(base) || base.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return latest.subtract(base).divide(base, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcVolN(List<BarDaily> descBars, int n) {
        if (descBars == null || descBars.size() < n + 1) {
            return BigDecimal.ZERO;
        }
        List<BarDaily> slice = new ArrayList<>(descBars.subList(0, n + 1));
        slice.sort(Comparator.comparing(BarDaily::getTradeDate));
        List<BigDecimal> rets = new ArrayList<>();
        for (int i = 1; i < slice.size(); i++) {
            BigDecimal prev = slice.get(i - 1).getClosePrice();
            BigDecimal curr = slice.get(i).getClosePrice();
            if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                rets.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP));
            }
        }
        if (rets.size() < 3) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = rets.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rets.size()), 8, RoundingMode.HALF_UP);
        BigDecimal var = BigDecimal.ZERO;
        for (BigDecimal r : rets) {
            BigDecimal d = r.subtract(mean);
            var = var.add(d.multiply(d));
        }
        var = var.divide(BigDecimal.valueOf(rets.size() - 1), 8, RoundingMode.HALF_UP);
        double ann = Math.sqrt(var.doubleValue()) * Math.sqrt(252) * 100;
        return BigDecimal.valueOf(ann).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentile(List<BigDecimal> sortedAsc, double p) {
        if (sortedAsc == null || sortedAsc.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int idx = (int) Math.floor((sortedAsc.size() - 1) * p);
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= sortedAsc.size()) {
            idx = sortedAsc.size() - 1;
        }
        return sortedAsc.get(idx).setScale(6, RoundingMode.HALF_UP);
    }

    private List<ClosedTradeSnap> matchClosedTrades(List<PaperOrder> orders) {
        List<ClosedTradeSnap> trades = new ArrayList<>();
        List<PaperOrder> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator.comparing(PaperOrder::getTradeDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(PaperOrder::getId, Comparator.nullsLast(Long::compareTo)));
        Map<String, List<OpenLotSnap>> openLots = new HashMap<>();
        for (PaperOrder order : sorted) {
            int qty = Objects.nonNull(order.getQuantity()) ? order.getQuantity() : 0;
            if (qty <= 0 || Objects.isNull(order.getPrice()) || Objects.isNull(order.getTradeDate())) {
                continue;
            }
            if ("BUY".equalsIgnoreCase(order.getSide())) {
                openLots.computeIfAbsent(order.getCode(), k -> new ArrayList<>())
                        .add(new OpenLotSnap(qty, order.getPrice(), order.getTradeDate()));
                continue;
            }
            if (!"SELL".equalsIgnoreCase(order.getSide())) {
                continue;
            }
            List<OpenLotSnap> queue = openLots.computeIfAbsent(order.getCode(), k -> new ArrayList<>());
            int remain = qty;
            while (remain > 0 && !queue.isEmpty()) {
                OpenLotSnap lot = queue.get(0);
                int matched = Math.min(remain, lot.qty);
                if (lot.price.signum() > 0) {
                    BigDecimal ret = order.getPrice().subtract(lot.price).divide(lot.price, 6, RoundingMode.HALF_UP);
                    int hold = (int) ChronoUnit.DAYS.between(lot.buyDate, order.getTradeDate());
                    ClosedTradeSnap snap = new ClosedTradeSnap();
                    snap.ret = ret;
                    snap.holdDays = Math.max(hold, 0);
                    snap.sellWeekday = order.getTradeDate().getDayOfWeek().getValue();
                    trades.add(snap);
                }
                lot.qty -= matched;
                remain -= matched;
                if (lot.qty <= 0) {
                    queue.remove(0);
                }
            }
        }
        return trades;
    }

    private static class OpenLotSnap {
        private int qty;
        private final BigDecimal price;
        private final LocalDate buyDate;

        private OpenLotSnap(int qty, BigDecimal price, LocalDate buyDate) {
            this.qty = qty;
            this.price = price;
            this.buyDate = buyDate;
        }
    }

    private static class ClosedTradeSnap {
        private BigDecimal ret;
        private int holdDays;
        private int sellWeekday;
    }

    private List<EquityPointResp> buyHoldCurve(String code, LocalDate begin, LocalDate end, BigDecimal initCash) {
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        List<EquityPointResp> points = new ArrayList<>();
        if (bars.size() < 2 || Objects.isNull(bars.get(0).getClosePrice()) || bars.get(0).getClosePrice().signum() <= 0) {
            return points;
        }
        BigDecimal first = bars.get(0).getClosePrice();
        BigDecimal cash = Objects.nonNull(initCash) ? initCash : BigDecimal.valueOf(1000000);
        for (BarDaily bar : bars) {
            if (Objects.isNull(bar.getClosePrice())) {
                continue;
            }
            BigDecimal equity = cash.multiply(bar.getClosePrice()).divide(first, 2, RoundingMode.HALF_UP);
            points.add(EquityPointResp.builder().tradeDate(bar.getTradeDate()).equity(equity).build());
        }
        return points;
    }
}
