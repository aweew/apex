package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.PortfolioBriefResp;
import com.awe.apex.quant.domain.dto.HoldingTradeReq;
import com.awe.apex.quant.domain.dto.PortfolioHoldingSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioImportReq;
import com.awe.apex.quant.domain.dto.PortfolioImportResp;
import com.awe.apex.quant.domain.dto.PortfolioOrderReq;
import com.awe.apex.quant.domain.dto.PortfolioSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.PortfolioTopHoldingResp;
import com.awe.apex.quant.domain.dto.ObserveTechSignal;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioDaily;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.domain.enums.PortfolioTradeSideEnum;
import com.awe.apex.quant.holding.PortfolioBriefBuilder;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.MyHoldingMapper;
import com.awe.apex.quant.mapper.PortfolioDailyMapper;
import com.awe.apex.quant.mapper.PortfolioHoldingMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IPortfolioTradeRecordService;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 实盘组合服务
 */
@Slf4j
@Service
public class PortfolioServiceImpl implements IPortfolioService {

    @Resource
    private ApexUserContext userContext;

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String DEFAULT_NAME = "我的持仓";
    private static final Pattern LINE_SPLIT = Pattern.compile("[,，\\t\\s]+");
    private static final BigDecimal FALLBACK_STOP_PCT = new BigDecimal("0.08");
    private static final BigDecimal FALLBACK_TAKE_PCT = new BigDecimal("0.20");

    /** 批量导入时跳过逐条刷快照，导入结束后统一刷一次 */
    private static final ThreadLocal<Boolean> SKIP_TODAY_SNAPSHOT_REFRESH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Resource
    private PortfolioMapper portfolioMapper;

    @Resource
    private PortfolioHoldingMapper portfolioHoldingMapper;

    @Resource
    private PortfolioDailyMapper portfolioDailyMapper;

    @Resource
    private MyHoldingMapper myHoldingMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private IConfigService configService;

    @Resource
    private IPortfolioTradeRecordService tradeRecordService;

    @Resource
    private ApexUserAuthService userAuthService;

    @Lazy
    @Resource
    private IMyHoldingService myHoldingService;

    /**
     * 确保默认组合存在，并完成 my_holding 首次迁移
     *
     * @return 默认组合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Portfolio ensureDefaultPortfolio() {
        Long currentUserId = currentUserId();
        Portfolio def = portfolioMapper.selectOne(Wrappers.<Portfolio>lambdaQuery()
                .eq(Portfolio::getIsDefault, 1)
                .eq(Portfolio::getUserId, currentUserId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(def)) {
            def = Portfolio.builder()
                    .userId(currentUserId)
                    .name(DEFAULT_NAME)
                    .note("本地实盘默认组合")
                    .ownerLabel("我")
                    .isDefault(1)
                    .status(STATUS_ACTIVE)
                    .sortNo(0)
                    .cashBalance(BigDecimal.ZERO)
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            portfolioMapper.insert(def);
            log.info("已创建默认组合，组合编号={}", def.getId());
        }
        Long cnt = portfolioHoldingMapper.selectCount(Wrappers.<PortfolioHolding>lambdaQuery()
                .eq(PortfolioHolding::getPortfolioId, def.getId()));
        if (Objects.isNull(cnt) || cnt == 0) {
            List<MyHolding> mine = myHoldingMapper.selectList(Wrappers.<MyHolding>lambdaQuery()
                    .eq(MyHolding::getUserId, currentUserId)
                    .orderByAsc(MyHolding::getCode));
            for (MyHolding row : mine) {
                String code = MarketCodeUtils.normalizeHoldingCode(row.getCode());
                if (StringUtils.isBlank(code)) {
                    continue;
                }
                PortfolioHolding holding = PortfolioHolding.builder()
                        .portfolioId(def.getId())
                        .code(code)
                        .name(row.getName())
                        .quantity(row.getQuantity())
                        .costPrice(row.getCostPrice())
                        .stopLoss(row.getStopLoss())
                        .takeProfit(row.getTakeProfit())
                        .note(row.getNote())
                        .createTime(now)
                        .updateTime(now)
                        .deleted(0)
                        .build();
                portfolioHoldingMapper.insert(holding);
            }
            if (CollUtil.isNotEmpty(mine)) {
                log.info("已将原持仓迁移到默认组合，数量={}", mine.size());
            }
        }
        return def;
    }

    /**
     * 持仓变更后：若已有当日快照则静默重打，避免曲线/当日盈亏仍是旧仓位
     *
     * @param portfolioId 组合ID
     */
    private void refreshTodaySnapshotQuietly(Long portfolioId) {
        if (Objects.isNull(portfolioId) || Boolean.TRUE.equals(SKIP_TODAY_SNAPSHOT_REFRESH.get())) {
            return;
        }
        try {
            PortfolioDaily today = portfolioDailyMapper.selectOne(Wrappers.<PortfolioDaily>lambdaQuery()
                    .eq(PortfolioDaily::getPortfolioId, portfolioId)
                    .eq(PortfolioDaily::getTradeDate, LocalDate.now())
                    .last("LIMIT 1"));
            if (Objects.nonNull(today)) {
                snapshot(portfolioId);
            }
        } catch (Exception ex) {
            log.warn("持仓变更后刷新当日快照失败，组合编号={}，异常={}", portfolioId, ex.getMessage());
        }
    }

    /**
     * 组合列表（含今日浮盈摘要）
     *
     * @param includeArchived 是否含归档
     * @return 列表
     */
    @Override
    public List<PortfolioSummaryResp> listPortfolios(boolean includeArchived) {
        Portfolio defaultPortfolio = ensureDefaultPortfolio();
        Long currentUserId = currentUserId();
        boolean currentUserAdmin = userAuthService.isAdmin(currentUserId);
        var query = Wrappers.<Portfolio>lambdaQuery()
                .orderByAsc(Portfolio::getSortNo).orderByDesc(Portfolio::getUpdateTime);
        if (!includeArchived) {
            query.eq(Portfolio::getStatus, STATUS_ACTIVE);
        }
        List<Portfolio> list = new ArrayList<>(portfolioMapper.selectList(query));

        // 当前用户的真实持仓固定置顶，其他共享组合继续沿用管理员排序
        for (int index = 0; index < list.size(); index++) {
            Portfolio portfolio = list.get(index);
            if (Objects.equals(portfolio.getId(), defaultPortfolio.getId())) {
                if (index > 0) {
                    list.remove(index);
                    list.add(0, portfolio);
                }
                break;
            }
        }

        List<PortfolioSummaryResp> result = new ArrayList<>();
        for (Portfolio portfolio : list) {
            result.add(buildSummary(portfolio, false, currentUserId, currentUserAdmin));
        }
        return result;
    }

    /**
     * 查询当前用户全部活跃组合的持仓代码并集
     *
     * @return 去重后的持仓代码
     */
    @Override
    public List<String> listActiveHoldingCodes() {
        ensureDefaultPortfolio();
        List<Portfolio> portfolios = portfolioMapper.selectList(Wrappers.<Portfolio>lambdaQuery()
                .eq(Portfolio::getUserId, currentUserId())
                .eq(Portfolio::getStatus, STATUS_ACTIVE)
                .orderByAsc(Portfolio::getSortNo));
        if (CollUtil.isEmpty(portfolios)) {
            return new ArrayList<>();
        }
        List<Long> portfolioIds = new ArrayList<>();
        for (Portfolio portfolio : portfolios) {
            portfolioIds.add(portfolio.getId());
        }
        List<PortfolioHolding> holdings = portfolioHoldingMapper.selectList(Wrappers.<PortfolioHolding>lambdaQuery()
                .in(PortfolioHolding::getPortfolioId, portfolioIds)
                .gt(PortfolioHolding::getQuantity, 0)
                .orderByAsc(PortfolioHolding::getCode));
        return collectHoldingCodes(holdings);
    }

    /**
     * 保存组合
     *
     * @param req 请求
     * @return 组合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Portfolio savePortfolio(PortfolioSaveReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getName())) {
            throw new BusinessException("组合名称不能为空");
        }
        if (Objects.nonNull(req.getCashBalance()) && req.getCashBalance().signum() < 0) {
            throw new BusinessException("组合现金不能小于0");
        }
        ensureDefaultPortfolio();
        String name = req.getName().trim();
        String status = StringUtils.isNotBlank(req.getStatus()) ? req.getStatus().trim().toUpperCase() : STATUS_ACTIVE;
        if (!STATUS_ACTIVE.equals(status) && !STATUS_ARCHIVED.equals(status)) {
            throw new BusinessException("状态仅支持 ACTIVE/ARCHIVED");
        }
        LocalDateTime now = LocalDateTime.now();
        if (Objects.nonNull(req.getId())) {
            Portfolio exist = requireEditablePortfolio(req.getId());
            if (Objects.equals(exist.getIsDefault(), 1) && STATUS_ARCHIVED.equals(status)) {
                throw new BusinessException("默认组合不能归档");
            }
            assertNameUnique(name, exist.getId(), exist.getUserId());
            exist.setName(name);
            exist.setNote(StringUtils.trim(req.getNote()));
            exist.setOwnerLabel(StringUtils.trim(req.getOwnerLabel()));
            exist.setStatus(status);
            if (Objects.nonNull(req.getSortNo())) {
                exist.setSortNo(req.getSortNo());
            }
            if (Objects.nonNull(req.getCashBalance())) {
                exist.setCashBalance(req.getCashBalance());
            }
            exist.setUpdateTime(now);
            portfolioMapper.updateById(exist);
            if (Objects.nonNull(req.getCashBalance())) {
                refreshTodaySnapshotQuietly(exist.getId());
            }
            return exist;
        }
        Long currentUserId = currentUserId();
        assertNameUnique(name, null, currentUserId);
        Portfolio created = Portfolio.builder()
                .userId(currentUserId)
                .name(name)
                .note(StringUtils.trim(req.getNote()))
                .ownerLabel(StringUtils.trim(req.getOwnerLabel()))
                .isDefault(0)
                .status(status)
                .sortNo(Objects.nonNull(req.getSortNo()) ? req.getSortNo() : 100)
                .cashBalance(Objects.nonNull(req.getCashBalance()) ? req.getCashBalance() : BigDecimal.ZERO)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        portfolioMapper.insert(created);
        return created;
    }

    /**
     * 保存组合展示顺序
     *
     * @param req 排序请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortPortfolios(PortfolioOrderReq req) {
        if (Objects.isNull(req) || CollUtil.isEmpty(req.getPortfolioIds())) {
            throw new BusinessException("组合排序不能为空");
        }
        Set<Long> portfolioIdSet = new HashSet<>(req.getPortfolioIds());
        if (portfolioIdSet.size() != req.getPortfolioIds().size() || portfolioIdSet.contains(null)) {
            throw new BusinessException("组合排序包含重复或无效ID");
        }
        userAuthService.requireAdmin();
        for (int index = 0; index < req.getPortfolioIds().size(); index++) {
            Portfolio portfolio = requireVisiblePortfolio(req.getPortfolioIds().get(index));
            portfolio.setSortNo(index);
            portfolio.setUpdateTime(LocalDateTime.now());
            portfolioMapper.updateById(portfolio);
        }
    }

    /**
     * 删除组合（禁止删默认）
     *
     * @param id 组合ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePortfolio(Long id) {
        Portfolio portfolio = requireEditablePortfolio(id);
        if (Objects.equals(portfolio.getIsDefault(), 1)) {
            throw new BusinessException("默认组合不可删除");
        }
        portfolioHoldingMapper.delete(Wrappers.<PortfolioHolding>lambdaQuery()
                .eq(PortfolioHolding::getPortfolioId, id));
        portfolioDailyMapper.delete(Wrappers.<PortfolioDaily>lambdaQuery()
                .eq(PortfolioDaily::getPortfolioId, id));
        portfolioMapper.deleteById(id);
    }

    /**
     * 组合详情
     *
     * @param id 组合ID
     * @return 详情
     */
    @Override
    public PortfolioSummaryResp detail(Long id) {
        ensureDefaultPortfolio();
        Portfolio portfolio = requireVisiblePortfolio(id);
        Long currentUserId = currentUserId();
        boolean currentUserAdmin = userAuthService.isAdmin(currentUserId);
        return buildSummary(portfolio, true, currentUserId, currentUserAdmin);
    }

    /**
     * 查询盘中快照所需的轻量组合摘要
     *
     * @param id 组合ID
     * @return 组合权益与当日收益摘要
     */
    @Override
    public PortfolioSummaryResp intradaySummary(Long id) {
        ensureDefaultPortfolio();
        Portfolio portfolio = requireVisiblePortfolio(id);
        Long currentUserId = currentUserId();
        boolean currentUserAdmin = userAuthService.isAdmin(currentUserId);
        return buildSummary(portfolio, false, currentUserId, currentUserAdmin);
    }

    /**
     * 保存持仓
     *
     * @param portfolioId 组合ID
     * @param req         请求
     * @return 持仓
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioHolding saveHolding(Long portfolioId, PortfolioHoldingSaveReq req) {
        return saveHolding(portfolioId, req, PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);
    }

    /**
     * 按指定来源保存持仓并生成交易流水。
     *
     * @param portfolioId 组合ID
     * @param req         请求
     * @param source      变动来源
     * @param sourceRef   来源幂等引用
     * @return 持仓
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioHolding saveHolding(Long portfolioId, PortfolioHoldingSaveReq req,
                                        PortfolioTradeSourceEnum source, String sourceRef) {
        Portfolio portfolio = requireEditablePortfolio(portfolioId);
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
        PortfolioHolding exist = null;
        if (Objects.nonNull(req.getId())) {
            exist = portfolioHoldingMapper.selectById(req.getId());
            if (Objects.nonNull(exist) && !Objects.equals(exist.getPortfolioId(), portfolioId)) {
                throw new BusinessException("持仓不属于该组合");
            }
        }
        if (Objects.isNull(exist)) {
            exist = portfolioHoldingMapper.selectOne(Wrappers.<PortfolioHolding>lambdaQuery()
                    .eq(PortfolioHolding::getPortfolioId, portfolioId)
                    .eq(PortfolioHolding::getCode, code)
                    .last("LIMIT 1"));
        }
        if (Objects.nonNull(exist)) {
            String beforeCode = exist.getCode();
            String beforeName = exist.getName();
            int beforeQuantity = Objects.nonNull(exist.getQuantity()) ? exist.getQuantity() : 0;
            exist.setCode(code);
            exist.setName(name);
            exist.setQuantity(quantity);
            exist.setCostPrice(req.getCostPrice());
            exist.setStopLoss(req.getStopLoss());
            exist.setTakeProfit(req.getTakeProfit());
            exist.setNote(StringUtils.trim(req.getNote()));
            exist.setUpdateTime(now);
            // 显式 set，避免 updateById 策略跳过空值导致成本/数量未落库
            portfolioHoldingMapper.update(null, Wrappers.<PortfolioHolding>lambdaUpdate()
                    .eq(PortfolioHolding::getId, exist.getId())
                    .set(PortfolioHolding::getCode, code)
                    .set(PortfolioHolding::getName, name)
                    .set(PortfolioHolding::getQuantity, quantity)
                    .set(PortfolioHolding::getCostPrice, req.getCostPrice())
                    .set(PortfolioHolding::getStopLoss, req.getStopLoss())
                    .set(PortfolioHolding::getTakeProfit, req.getTakeProfit())
                    .set(PortfolioHolding::getNote, StringUtils.trim(req.getNote()))
                    .set(PortfolioHolding::getUpdateTime, now));
            if (StringUtils.isNotBlank(beforeCode) && !beforeCode.equals(code)) {
                tradeRecordService.recordChange(portfolio, beforeCode, beforeName, beforeQuantity, 0,
                        req.getTradePrice(), req.getTradeTime(), source, sourceRef);
                tradeRecordService.recordChange(portfolio, code, name, 0, quantity,
                        req.getTradePrice(), req.getTradeTime(), source, sourceRef);
            } else {
                tradeRecordService.recordChange(portfolio, code, name, beforeQuantity, quantity,
                        req.getTradePrice(), req.getTradeTime(), source, sourceRef);
            }
            fillPnl(exist, basic);
            if (Objects.equals(portfolio.getIsDefault(), 1)) {
                mirrorToMyHolding(portfolio.getUserId(), exist);
            }
            touchPortfolio(portfolioId);
            refreshTodaySnapshotQuietly(portfolioId);
            return exist;
        }
        PortfolioHolding created = PortfolioHolding.builder()
                .portfolioId(portfolioId)
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
        portfolioHoldingMapper.insert(created);
        tradeRecordService.recordChange(portfolio, code, name, 0, quantity,
                req.getTradePrice(), req.getTradeTime(), source, sourceRef);
        fillPnl(created, basic);
        if (Objects.equals(portfolio.getIsDefault(), 1)) {
            mirrorToMyHolding(portfolio.getUserId(), created);
        }
        touchPortfolio(portfolioId);
        refreshTodaySnapshotQuietly(portfolioId);
        return created;
    }

    /**
     * 买入或卖出组合持仓。
     *
     * @param portfolioId 组合ID
     * @param req         成交请求
     * @return 变更后的持仓，全部卖出时返回空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioHolding tradeHolding(Long portfolioId, HoldingTradeReq req) {
        return tradeHolding(portfolioId, req, PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);
    }

    /**
     * 按指定来源买入或卖出组合持仓。
     *
     * @param portfolioId 组合ID
     * @param req         成交请求
     * @param source      变动来源
     * @return 变更后的持仓，全部卖出时返回空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioHolding tradeHolding(Long portfolioId, HoldingTradeReq req,
                                         PortfolioTradeSourceEnum source) {
        return tradeHolding(portfolioId, req, source, null);
    }

    /**
     * 按指定来源和幂等引用买入或卖出组合持仓。
     *
     * @param portfolioId 组合ID
     * @param req         成交请求
     * @param source      变动来源
     * @param sourceRef   来源幂等引用
     * @return 变更后的持仓，全部卖出时返回空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioHolding tradeHolding(Long portfolioId, HoldingTradeReq req,
                                         PortfolioTradeSourceEnum source, String sourceRef) {
        Portfolio portfolio = requireEditablePortfolio(portfolioId);
        if (Objects.isNull(req)) {
            throw new BusinessException("成交请求不能为空");
        }
        String side = StringUtils.isNotBlank(req.getSide()) ? req.getSide().trim().toUpperCase() : "";
        boolean buying = PortfolioTradeSideEnum.BUY.getCode().equals(side);
        boolean selling = PortfolioTradeSideEnum.SELL.getCode().equals(side);
        if (!buying && !selling) {
            throw new BusinessException("成交方向仅支持 BUY/SELL");
        }
        if (Objects.isNull(req.getQuantity()) || req.getQuantity() <= 0) {
            throw new BusinessException("成交数量必须大于0");
        }
        if (Objects.isNull(req.getTradePrice()) || req.getTradePrice().signum() <= 0) {
            throw new BusinessException("成交价必须大于0");
        }

        PortfolioHolding holding = null;
        String normalizedCode = null;
        if (Objects.nonNull(req.getHoldingId())) {
            holding = portfolioHoldingMapper.selectById(req.getHoldingId());
            if (Objects.isNull(holding) || !Objects.equals(holding.getPortfolioId(), portfolioId)) {
                throw new BusinessException("持仓不存在");
            }
        } else if (StringUtils.isNotBlank(req.getCode())) {
            normalizedCode = MarketCodeUtils.normalizeHoldingCode(req.getCode());
            if (StringUtils.isBlank(normalizedCode)) {
                throw new BusinessException("证券代码无效");
            }
            holding = portfolioHoldingMapper.selectOne(Wrappers.<PortfolioHolding>lambdaQuery()
                    .eq(PortfolioHolding::getPortfolioId, portfolioId)
                    .eq(PortfolioHolding::getCode, normalizedCode)
                    .last("LIMIT 1"));
        }
        if (selling && Objects.isNull(holding)) {
            throw new BusinessException("卖出持仓不存在");
        }
        if (buying && Objects.isNull(holding) && StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("证券代码不能为空");
        }
        if (StringUtils.isBlank(normalizedCode) && Objects.nonNull(holding)) {
            normalizedCode = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
        }

        if (StringUtils.isNotBlank(sourceRef)) {
            JournalTrade recordedTrade = tradeRecordService.findBySourceRef(portfolio.getUserId(), normalizedCode,
                    source, sourceRef);
            if (Objects.nonNull(recordedTrade)) {
                int currentQuantity = Objects.nonNull(holding) && Objects.nonNull(holding.getQuantity())
                        ? holding.getQuantity() : 0;
                boolean samePrice = Objects.nonNull(recordedTrade.getPrice())
                        && recordedTrade.getPrice().compareTo(req.getTradePrice()) == 0;
                if (!Objects.equals(recordedTrade.getPortfolioId(), portfolioId)
                        || !Objects.equals(recordedTrade.getSide(), side)
                        || !Objects.equals(recordedTrade.getQuantity(), req.getQuantity())
                        || !Objects.equals(recordedTrade.getAfterQuantity(), currentQuantity)
                        || !samePrice) {
                    throw new BusinessException("重复请求的持仓变动内容不一致");
                }
                return holding;
            }
        }

        int beforeQuantity = Objects.nonNull(holding) && Objects.nonNull(holding.getQuantity())
                ? holding.getQuantity() : 0;
        if (selling && req.getQuantity() > beforeQuantity) {
            throw new BusinessException("卖出数量不能超过当前持仓");
        }
        long changedQuantity = buying
                ? (long) beforeQuantity + req.getQuantity()
                : (long) beforeQuantity - req.getQuantity();
        if (changedQuantity > Integer.MAX_VALUE) {
            throw new BusinessException("成交后持仓数量超出范围");
        }

        if (selling && changedQuantity == 0) {
            portfolioHoldingMapper.deleteById(holding.getId());
            tradeRecordService.recordChange(portfolio, holding.getCode(), holding.getName(),
                    beforeQuantity, 0, req.getTradePrice(), req.getTradeTime(), source, sourceRef);
            if (Objects.equals(portfolio.getIsDefault(), 1)) {
                MyHolding myHolding = myHoldingMapper.selectOne(Wrappers.<MyHolding>lambdaQuery()
                        .eq(MyHolding::getUserId, portfolio.getUserId())
                        .eq(MyHolding::getCode, holding.getCode())
                        .last("LIMIT 1"));
                if (Objects.nonNull(myHolding)) {
                    myHoldingMapper.deleteById(myHolding.getId());
                }
            }
            touchPortfolio(portfolioId);
            refreshTodaySnapshotQuietly(portfolioId);
            return null;
        }

        BigDecimal costPrice = Objects.nonNull(holding) ? holding.getCostPrice() : req.getTradePrice();
        if (buying && Objects.nonNull(holding) && beforeQuantity > 0 && Objects.nonNull(holding.getCostPrice())) {
            BigDecimal beforeCost = holding.getCostPrice().multiply(BigDecimal.valueOf(beforeQuantity));
            BigDecimal tradeCost = req.getTradePrice().multiply(BigDecimal.valueOf(req.getQuantity()));
            costPrice = beforeCost.add(tradeCost)
                    .divide(BigDecimal.valueOf(changedQuantity), 4, RoundingMode.HALF_UP);
        }

        PortfolioHoldingSaveReq saveReq = new PortfolioHoldingSaveReq();
        saveReq.setId(Objects.nonNull(holding) ? holding.getId() : null);
        saveReq.setCode(Objects.nonNull(holding) ? holding.getCode() : req.getCode());
        saveReq.setName(Objects.nonNull(holding) ? holding.getName() : req.getName());
        saveReq.setQuantity((int) changedQuantity);
        saveReq.setCostPrice(costPrice);
        saveReq.setStopLoss(Objects.nonNull(holding) ? holding.getStopLoss() : null);
        saveReq.setTakeProfit(Objects.nonNull(holding) ? holding.getTakeProfit() : null);
        saveReq.setNote(Objects.nonNull(holding) ? holding.getNote() : null);
        saveReq.setTradePrice(req.getTradePrice());
        saveReq.setTradeTime(req.getTradeTime());
        return saveHolding(portfolioId, saveReq, source, sourceRef);
    }

    /**
     * 删除持仓
     *
     * @param portfolioId 组合ID
     * @param holdingId   持仓ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeHolding(Long portfolioId, Long holdingId) {
        removeHolding(portfolioId, holdingId, PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);
    }

    /**
     * 按指定来源删除持仓并生成清仓流水。
     *
     * @param portfolioId 组合ID
     * @param holdingId   持仓ID
     * @param source      变动来源
     * @param sourceRef   来源幂等引用
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeHolding(Long portfolioId, Long holdingId,
                              PortfolioTradeSourceEnum source, String sourceRef) {
        Portfolio portfolio = requireEditablePortfolio(portfolioId);
        PortfolioHolding holding = portfolioHoldingMapper.selectById(holdingId);
        if (Objects.isNull(holding) || !Objects.equals(holding.getPortfolioId(), portfolioId)) {
            throw new BusinessException("持仓不存在");
        }
        portfolioHoldingMapper.deleteById(holdingId);
        tradeRecordService.recordChange(portfolio, holding.getCode(), holding.getName(),
                holding.getQuantity(), 0, null, null, source, sourceRef);
        if (Objects.equals(portfolio.getIsDefault(), 1) && StringUtils.isNotBlank(holding.getCode())) {
            MyHolding mine = myHoldingMapper.selectOne(Wrappers.<MyHolding>lambdaQuery()
                    .eq(MyHolding::getUserId, portfolio.getUserId())
                    .eq(MyHolding::getCode, holding.getCode())
                    .last("LIMIT 1"));
            if (Objects.nonNull(mine)) {
                myHoldingMapper.deleteById(mine.getId());
            }
        }
        touchPortfolio(portfolioId);
        refreshTodaySnapshotQuietly(portfolioId);
    }

    /**
     * 文本导入持仓
     *
     * @param portfolioId 组合ID
     * @param req         文本
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioImportResp importHoldings(Long portfolioId, PortfolioImportReq req) {
        requireEditablePortfolio(portfolioId);
        if (Objects.isNull(req) || StringUtils.isBlank(req.getText())) {
            throw new BusinessException("导入文本不能为空");
        }
        String[] lines = req.getText().replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int success = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();
        SKIP_TODAY_SNAPSHOT_REFRESH.set(Boolean.TRUE);
        try {
            for (int i = 0; i < lines.length; i++) {
                String raw = lines[i].trim();
                if (StringUtils.isBlank(raw) || raw.startsWith("#")) {
                    continue;
                }
                try {
                    PortfolioHoldingSaveReq saveReq = parseImportLine(raw);
                    saveHolding(portfolioId, saveReq, PortfolioTradeSourceEnum.PORTFOLIO_IMPORT, null);
                    success++;
                } catch (Exception ex) {
                    fail++;
                    errors.add("第" + (i + 1) + "行: " + ex.getMessage());
                }
            }
        } finally {
            SKIP_TODAY_SNAPSHOT_REFRESH.set(Boolean.FALSE);
            if (success > 0) {
                refreshTodaySnapshotQuietly(portfolioId);
            }
        }
        return PortfolioImportResp.builder().success(success).fail(fail).errors(errors).build();
    }

    /**
     * 打当日快照
     *
     * @param portfolioId 组合ID
     * @return 快照
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioDaily snapshot(Long portfolioId) {
        requireEditablePortfolio(portfolioId);
        PortfolioSummaryResp summary = detail(portfolioId);
        LocalDate tradeDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> payloadRows = new ArrayList<>();
        for (PortfolioHolding holding : summary.getHoldings()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", holding.getCode());
            row.put("name", holding.getName());
            row.put("quantity", holding.getQuantity());
            row.put("costPrice", holding.getCostPrice());
            row.put("marketPrice", holding.getMarketPrice());
            row.put("marketValue", holding.getMarketValue());
            row.put("todayPnl", holding.getTodayPnl());
            row.put("pnl", holding.getPnl());
            payloadRows.add(row);
        }
        String payload = JsonUtils.toJsonString(payloadRows);
        PortfolioDaily exist = portfolioDailyMapper.selectOne(Wrappers.<PortfolioDaily>lambdaQuery()
                .eq(PortfolioDaily::getPortfolioId, portfolioId)
                .eq(PortfolioDaily::getTradeDate, tradeDate)
                .last("LIMIT 1"));
        BigDecimal totalEquity = Objects.nonNull(summary.getTotalEquity())
                ? summary.getTotalEquity()
                : zeroIfNull(summary.getMarketValue()).add(zeroIfNull(summary.getCashBalance()));
        totalEquity = totalEquity.setScale(2, RoundingMode.HALF_UP);
        BigDecimal peakEquity = portfolioDailyMapper.selectPeakEquityBefore(portfolioId, tradeDate);
        if (Objects.nonNull(exist) && Objects.nonNull(exist.getPeakEquity())) {
            peakEquity = Objects.isNull(peakEquity) ? exist.getPeakEquity() : peakEquity.max(exist.getPeakEquity());
        }
        peakEquity = Objects.isNull(peakEquity) ? totalEquity : peakEquity.max(totalEquity);
        BigDecimal drawdown = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        if (peakEquity.signum() > 0 && totalEquity.compareTo(peakEquity) < 0) {
            drawdown = peakEquity.subtract(totalEquity)
                    .divide(peakEquity, 6, RoundingMode.HALF_UP);
        }
        if (Objects.nonNull(exist)) {
            exist.setMarketValue(summary.getMarketValue());
            exist.setCostValue(summary.getCostValue());
            exist.setTotalPnl(summary.getTotalPnl());
            exist.setTodayPnl(summary.getTodayPnl());
            exist.setTodayPct(summary.getTodayPct());
            exist.setPositionCount(summary.getPositionCount());
            exist.setCash(summary.getCashBalance());
            exist.setTotalEquity(totalEquity);
            exist.setPeakEquity(peakEquity);
            exist.setDrawdown(drawdown);
            exist.setPayload(payload);
            exist.setUpdateTime(now);
            portfolioDailyMapper.updateById(exist);
            return exist;
        }
        PortfolioDaily created = PortfolioDaily.builder()
                .portfolioId(portfolioId)
                .tradeDate(tradeDate)
                .marketValue(summary.getMarketValue())
                .costValue(summary.getCostValue())
                .totalPnl(summary.getTotalPnl())
                .todayPnl(summary.getTodayPnl())
                .todayPct(summary.getTodayPct())
                .positionCount(summary.getPositionCount())
                .cash(summary.getCashBalance())
                .totalEquity(totalEquity)
                .peakEquity(peakEquity)
                .drawdown(drawdown)
                .payload(payload)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        portfolioDailyMapper.insert(created);
        return created;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return Objects.nonNull(value) ? value : BigDecimal.ZERO;
    }

    /**
     * 全部活跃组合打快照
     *
     * @return 成功数
     */
    @Override
    public int snapshotAll() {
        ensureDefaultPortfolio();
        List<Portfolio> list = portfolioMapper.selectList(Wrappers.<Portfolio>lambdaQuery()
                .eq(Portfolio::getUserId, currentUserId())
                .eq(Portfolio::getStatus, STATUS_ACTIVE));
        int ok = 0;
        for (Portfolio portfolio : list) {
            try {
                snapshot(portfolio.getId());
                ok++;
            } catch (Exception ex) {
                log.warn("组合快照失败，组合编号={}，异常={}", portfolio.getId(), ex.getMessage());
            }
        }
        return ok;
    }

    /**
     * 日收益序列
     *
     * @param portfolioId 组合ID
     * @param days        近 N 日
     * @return 列表
     */
    @Override
    public List<PortfolioDaily> listDaily(Long portfolioId, Integer days) {
        requireVisiblePortfolio(portfolioId);
        int limit = Objects.nonNull(days) && days > 0 ? Math.min(days, 365) : 60;
        return portfolioDailyMapper.selectList(Wrappers.<PortfolioDaily>lambdaQuery()
                .eq(PortfolioDaily::getPortfolioId, portfolioId)
                .orderByDesc(PortfolioDaily::getTradeDate)
                .last("LIMIT " + limit));
    }

    /**
     * 刷新组合持仓行情
     *
     * @param portfolioId 组合ID
     * @param onlyMissing 是否只刷缺现价的
     * @return 结果（含最新 detail）
     */
    @Override
    public Map<String, Object> refreshQuotes(Long portfolioId, Boolean onlyMissing) {
        requireEditablePortfolio(portfolioId);
        List<PortfolioHolding> holdings = portfolioHoldingMapper.selectList(Wrappers.<PortfolioHolding>lambdaQuery()
                .eq(PortfolioHolding::getPortfolioId, portfolioId)
                .orderByAsc(PortfolioHolding::getCode));
        List<String> codes = collectHoldingCodes(holdings);
        Map<String, Object> core = refreshQuotesForHoldings(holdings, codes, onlyMissing);
        core.put("detail", detail(portfolioId));
        return core;
    }

    /**
     * 一键刷新全部活跃组合行情（代码去重）
     *
     * @param onlyMissing 是否只刷缺现价的
     * @return 结果
     */
    @Override
    public Map<String, Object> refreshQuotesAll(Boolean onlyMissing) {
        ensureDefaultPortfolio();
        List<Portfolio> portfolios = portfolioMapper.selectList(Wrappers.<Portfolio>lambdaQuery()
                .eq(Portfolio::getUserId, currentUserId())
                .eq(Portfolio::getStatus, STATUS_ACTIVE));
        if (CollUtil.isEmpty(portfolios)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("portfolioCount", 0);
            empty.put("codeCount", 0);
            empty.put("success", 0);
            empty.put("fail", 0);
            empty.put("barSuccess", 0);
            empty.put("barFail", 0);
            empty.put("barCount", 0);
            empty.put("message", "暂无活跃组合");
            return empty;
        }
        List<Long> portfolioIds = new ArrayList<>();
        for (Portfolio portfolio : portfolios) {
            portfolioIds.add(portfolio.getId());
        }
        List<PortfolioHolding> holdings = portfolioHoldingMapper.selectList(Wrappers.<PortfolioHolding>lambdaQuery()
                .in(PortfolioHolding::getPortfolioId, portfolioIds)
                .orderByAsc(PortfolioHolding::getCode));
        List<String> codes = collectHoldingCodes(holdings);
        Map<String, Object> core = refreshQuotesForHoldings(holdings, codes, onlyMissing);
        core.put("portfolioCount", portfolios.size());
        core.put("codeCount", codes.size());
        core.put("message", "已刷新 " + portfolios.size() + " 个组合 / "
                + codes.size() + " 只标的（去重）；"
                + core.get("message"));
        return core;
    }

    /**
     * 汇总持仓代码（去重保序）
     *
     * @param holdings 持仓
     * @return 代码列表
     */
    private List<String> collectHoldingCodes(List<PortfolioHolding> holdings) {
        List<String> codes = new ArrayList<>();
        if (CollUtil.isEmpty(holdings)) {
            return codes;
        }
        for (PortfolioHolding holding : holdings) {
            if (Objects.isNull(holding.getQuantity()) || holding.getQuantity() <= 0) {
                continue;
            }
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isNotBlank(code) && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    /**
     * 刷新行情并回填持仓名称
     *
     * @param holdings     持仓行
     * @param codes        去重代码
     * @param onlyMissing  是否只刷缺现价
     * @return 结果
     */
    private Map<String, Object> refreshQuotesForHoldings(List<PortfolioHolding> holdings,
                                                         List<String> codes,
                                                         Boolean onlyMissing) {
        Map<String, Object> quoteResult = myHoldingService.refreshQuotesForCodes(codes, onlyMissing);
        LocalDateTime now = LocalDateTime.now();
        if (CollUtil.isNotEmpty(holdings)) {
            for (PortfolioHolding holding : holdings) {
                if (StringUtils.isNotBlank(holding.getName()) || StringUtils.isBlank(holding.getCode())) {
                    continue;
                }
                String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
                StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                        .eq(StockBasic::getCode, code)
                        .last("LIMIT 1"));
                if (Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getName())) {
                    holding.setName(basic.getName());
                    holding.setUpdateTime(now);
                    portfolioHoldingMapper.updateById(holding);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", quoteResult.get("success"));
        result.put("fail", quoteResult.get("fail"));
        result.put("message", quoteResult.get("message"));
        return result;
    }

    /**
     * 我的持仓变更后同步到默认组合
     *
     * @param holding 持仓
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mirrorMyHoldingSave(MyHolding holding) {
        mirrorMyHoldingSave(holding, null, null);
    }

    /**
     * 我的持仓变更后携带成交信息同步到默认组合。
     *
     * @param holding    持仓
     * @param tradePrice 实际成交价
     * @param tradeTime  实际成交时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mirrorMyHoldingSave(MyHolding holding, BigDecimal tradePrice, LocalDateTime tradeTime) {
        if (Objects.isNull(holding) || StringUtils.isBlank(holding.getCode())) {
            return;
        }
        Portfolio def = ensureDefaultPortfolio();
        String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
        LocalDateTime now = LocalDateTime.now();
        PortfolioHolding exist = portfolioHoldingMapper.selectOne(Wrappers.<PortfolioHolding>lambdaQuery()
                .eq(PortfolioHolding::getPortfolioId, def.getId())
                .eq(PortfolioHolding::getCode, code)
                .last("LIMIT 1"));
        int beforeQuantity = Objects.nonNull(exist) && Objects.nonNull(exist.getQuantity())
                ? exist.getQuantity() : 0;
        if (Objects.nonNull(exist)) {
            exist.setName(holding.getName());
            exist.setQuantity(holding.getQuantity());
            exist.setCostPrice(holding.getCostPrice());
            exist.setStopLoss(holding.getStopLoss());
            exist.setTakeProfit(holding.getTakeProfit());
            exist.setNote(holding.getNote());
            exist.setUpdateTime(now);
            portfolioHoldingMapper.update(null, Wrappers.<PortfolioHolding>lambdaUpdate()
                    .eq(PortfolioHolding::getId, exist.getId())
                    .set(PortfolioHolding::getName, holding.getName())
                    .set(PortfolioHolding::getQuantity, holding.getQuantity())
                    .set(PortfolioHolding::getCostPrice, holding.getCostPrice())
                    .set(PortfolioHolding::getStopLoss, holding.getStopLoss())
                    .set(PortfolioHolding::getTakeProfit, holding.getTakeProfit())
                    .set(PortfolioHolding::getNote, holding.getNote())
                    .set(PortfolioHolding::getUpdateTime, now));
        } else {
            portfolioHoldingMapper.insert(PortfolioHolding.builder()
                    .portfolioId(def.getId())
                    .code(code)
                    .name(holding.getName())
                    .quantity(holding.getQuantity())
                    .costPrice(holding.getCostPrice())
                    .stopLoss(holding.getStopLoss())
                    .takeProfit(holding.getTakeProfit())
                    .note(holding.getNote())
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build());
        }
        tradeRecordService.recordChange(def, code, holding.getName(), beforeQuantity, holding.getQuantity(),
                tradePrice, tradeTime, PortfolioTradeSourceEnum.HOLDING_WEB, null);
        touchPortfolio(def.getId());
        refreshTodaySnapshotQuietly(def.getId());
    }

    /**
     * 我的持仓删除后同步默认组合
     *
     * @param code 证券代码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mirrorMyHoldingRemove(String code) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        Portfolio def = ensureDefaultPortfolio();
        String pure = MarketCodeUtils.normalizeHoldingCode(code);
        PortfolioHolding exist = portfolioHoldingMapper.selectOne(Wrappers.<PortfolioHolding>lambdaQuery()
                .eq(PortfolioHolding::getPortfolioId, def.getId())
                .eq(PortfolioHolding::getCode, pure)
                .last("LIMIT 1"));
        if (Objects.nonNull(exist)) {
            portfolioHoldingMapper.deleteById(exist.getId());
            tradeRecordService.recordChange(def, pure, exist.getName(), exist.getQuantity(), 0,
                    null, null, PortfolioTradeSourceEnum.HOLDING_WEB, null);
            touchPortfolio(def.getId());
        }
    }

    private PortfolioSummaryResp buildSummary(Portfolio portfolio, boolean withHoldings,
                                              Long currentUserId, boolean currentUserAdmin) {
        List<PortfolioHolding> holdings = portfolioHoldingMapper.selectList(Wrappers.<PortfolioHolding>lambdaQuery()
                .eq(PortfolioHolding::getPortfolioId, portfolio.getId())
                .orderByDesc(PortfolioHolding::getUpdateTime)
                .orderByAsc(PortfolioHolding::getCode));
        if (withHoldings) {
            enrichHoldingsDeep(holdings);
        } else {
            enrichHoldings(holdings);
        }
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal costValue = BigDecimal.ZERO;
        BigDecimal todayPnl = BigDecimal.ZERO;
        boolean hasToday = false;
        LocalDateTime quoteTime = null;
        int missingQuoteCount = 0;
        for (PortfolioHolding holding : holdings) {
            if (Objects.nonNull(holding.getMarketValue())) {
                marketValue = marketValue.add(holding.getMarketValue());
            }
            if (Objects.nonNull(holding.getCostPrice()) && Objects.nonNull(holding.getQuantity()) && holding.getQuantity() > 0) {
                costValue = costValue.add(holding.getCostPrice().multiply(BigDecimal.valueOf(holding.getQuantity())));
            }
            if (Objects.nonNull(holding.getTodayPnl())) {
                todayPnl = todayPnl.add(holding.getTodayPnl());
                hasToday = true;
            }
            if (Objects.isNull(holding.getQuoteTime())) {
                missingQuoteCount++;
            } else if (Objects.isNull(quoteTime) || holding.getQuoteTime().isBefore(quoteTime)) {
                quoteTime = holding.getQuoteTime();
            }
        }
        marketValue = marketValue.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashBalance = Objects.nonNull(portfolio.getCashBalance())
                ? portfolio.getCashBalance().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalEquity = marketValue.add(cashBalance).setScale(2, RoundingMode.HALF_UP);
        costValue = costValue.setScale(2, RoundingMode.HALF_UP);
        todayPnl = hasToday ? todayPnl.setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal totalPnl = null;
        if (costValue.signum() > 0) {
            totalPnl = marketValue.subtract(costValue).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal todayPct = null;
        if (hasToday && Objects.nonNull(todayPnl)) {
            BigDecimal preMv = marketValue.subtract(todayPnl);
            if (preMv.abs().compareTo(BigDecimal.ONE) >= 0) {
                todayPct = todayPnl.multiply(BigDecimal.valueOf(100))
                        .divide(preMv, 4, RoundingMode.HALF_UP);
            }
        }
        if (marketValue.signum() > 0) {
            for (PortfolioHolding holding : holdings) {
                if (Objects.nonNull(holding.getMarketValue())) {
                    holding.setWeightPct(holding.getMarketValue()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(marketValue, 2, RoundingMode.HALF_UP));
                }
            }
        }
        PortfolioBriefResp brief = null;
        if (withHoldings) {
            brief = PortfolioBriefBuilder.build(holdings);
        }
        List<PortfolioTopHoldingResp> topHoldings = List.of();
        if (!withHoldings && CollUtil.isNotEmpty(holdings)) {
            List<PortfolioHolding> ranked = new ArrayList<>(holdings);
            ranked.sort((a, b) -> {
                BigDecimal wa = Objects.nonNull(a.getWeightPct()) ? a.getWeightPct() : BigDecimal.ZERO;
                BigDecimal wb = Objects.nonNull(b.getWeightPct()) ? b.getWeightPct() : BigDecimal.ZERO;
                return wb.compareTo(wa);
            });
            List<PortfolioTopHoldingResp> tops = new ArrayList<>();
            int lim = Math.min(3, ranked.size());
            for (int i = 0; i < lim; i++) {
                PortfolioHolding h = ranked.get(i);
                tops.add(PortfolioTopHoldingResp.builder()
                        .code(h.getCode())
                        .name(h.getName())
                        .pctChg(h.getPctChg())
                        .weightPct(h.getWeightPct())
                        .todayPnl(h.getTodayPnl())
                        .build());
            }
            topHoldings = tops;
        }
        boolean ownedByCurrentUser = Objects.equals(currentUserId, portfolio.getUserId());
        boolean editable = ownedByCurrentUser || currentUserAdmin;
        boolean systemDefault = Objects.equals(portfolio.getIsDefault(), 1);
        boolean currentDefault = ownedByCurrentUser && systemDefault;
        return PortfolioSummaryResp.builder()
                .id(portfolio.getId())
                .name(currentDefault ? DEFAULT_NAME : portfolio.getName())
                .note(portfolio.getNote())
                .ownerLabel(portfolio.getOwnerLabel())
                .isDefault(currentDefault)
                .systemDefault(systemDefault)
                .editable(editable)
                .status(portfolio.getStatus())
                .sortNo(portfolio.getSortNo())
                .positionCount(holdings.size())
                .marketValue(marketValue)
                .cashBalance(cashBalance)
                .totalEquity(totalEquity)
                .costValue(costValue)
                .totalPnl(totalPnl)
                .todayPnl(todayPnl)
                .todayPct(todayPct)
                .updateTime(portfolio.getUpdateTime())
                .quoteTime(quoteTime)
                .missingQuoteCount(missingQuoteCount)
                .brief(brief)
                .topHoldings(topHoldings)
                .holdings(withHoldings ? holdings : List.of())
                .build();
    }

    private void enrichHoldingsDeep(List<PortfolioHolding> holdings) {
        if (CollUtil.isEmpty(holdings)) {
            return;
        }
        // 先补止损/止盈，再 enrich，评价建议才能用到价位
        ensureMissingStopTake(holdings);
        List<MyHolding> seeds = new ArrayList<>();
        for (PortfolioHolding holding : holdings) {
            seeds.add(MyHolding.builder()
                    .code(holding.getCode())
                    .name(holding.getName())
                    .quantity(holding.getQuantity())
                    .costPrice(holding.getCostPrice())
                    .stopLoss(holding.getStopLoss())
                    .takeProfit(holding.getTakeProfit())
                    .note(holding.getNote())
                    .build());
        }
        List<MyHolding> enriched = myHoldingService.enrichHoldings(seeds);
        Map<String, MyHolding> byCode = new HashMap<>();
        for (MyHolding row : enriched) {
            String code = MarketCodeUtils.normalizeHoldingCode(row.getCode());
            if (StringUtils.isNotBlank(code)) {
                byCode.put(code, row);
            }
        }
        for (PortfolioHolding holding : holdings) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            MyHolding src = byCode.get(code);
            if (Objects.isNull(src)) {
                continue;
            }
            if (StringUtils.isNotBlank(src.getName())) {
                holding.setName(src.getName());
            }
            holding.setMarketPrice(src.getMarketPrice());
            holding.setPctChg(src.getPctChg());
            holding.setQuoteTime(src.getQuoteTime());
            holding.setMarketValue(src.getMarketValue());
            holding.setPnl(src.getPnl());
            holding.setPnlPct(src.getPnlPct());
            holding.setTodayPnl(src.getTodayPnl());
            holding.setIndustry(src.getIndustry());
            holding.setConcepts(src.getConcepts());
            holding.setThemeTags(src.getThemeTags());
            holding.setTechSignals(src.getTechSignals());
            holding.setTechSummary(src.getTechSummary());
            holding.setValuationLevel(src.getValuationLevel());
            holding.setValuationLabel(src.getValuationLabel());
            holding.setValuationSummary(src.getValuationSummary());
            holding.setPeDynamic(src.getPeDynamic());
            holding.setPeStatic(src.getPeStatic());
            holding.setPeTtm(src.getPeTtm());
            holding.setVerdict(src.getVerdict());
            holding.setAdvice(src.getAdvice());
        }
    }

    /**
     * 组合持仓缺止损/止盈时自动生成并回写（已有值不覆盖）
     */
    private void ensureMissingStopTake(List<PortfolioHolding> holdings) {
        if (CollUtil.isEmpty(holdings)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PortfolioHolding holding : holdings) {
            boolean needStop = Objects.isNull(holding.getStopLoss()) || holding.getStopLoss().signum() <= 0;
            boolean needTake = Objects.isNull(holding.getTakeProfit()) || holding.getTakeProfit().signum() <= 0;
            if (!needStop && !needTake) {
                continue;
            }
            BigDecimal base = null;
            if (Objects.nonNull(holding.getCostPrice()) && holding.getCostPrice().signum() > 0) {
                base = holding.getCostPrice();
            } else if (Objects.nonNull(holding.getMarketPrice()) && holding.getMarketPrice().signum() > 0) {
                base = holding.getMarketPrice();
            } else {
                String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
                StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                        .eq(StockBasic::getCode, code)
                        .last("LIMIT 1"));
                if (Objects.nonNull(basic) && Objects.nonNull(basic.getLatestPrice())
                        && basic.getLatestPrice().signum() > 0) {
                    base = basic.getLatestPrice();
                }
            }
            BigDecimal[] levels = suggestStopTake(holding.getCode(), base);
            boolean changed = false;
            if (needStop && Objects.nonNull(levels[0])) {
                holding.setStopLoss(levels[0]);
                changed = true;
            }
            if (needTake && Objects.nonNull(levels[1])) {
                holding.setTakeProfit(levels[1]);
                changed = true;
            }
            if (changed && Objects.nonNull(holding.getId())) {
                PortfolioHolding patch = new PortfolioHolding();
                patch.setId(holding.getId());
                patch.setStopLoss(holding.getStopLoss());
                patch.setTakeProfit(holding.getTakeProfit());
                patch.setUpdateTime(now);
                portfolioHoldingMapper.updateById(patch);
                log.info("组合持仓止损止盈已补全，组合编号={}，证券代码={}，止损价={}，止盈价={}",
                        holding.getPortfolioId(), holding.getCode(),
                        holding.getStopLoss(), holding.getTakeProfit());
            }
        }
    }

    /**
     * 建议止损/止盈：优先 ATR14×倍数，否则固定比例（-8% / +20%）
     */
    private BigDecimal[] suggestStopTake(String code, BigDecimal basePrice) {
        BigDecimal[] result = new BigDecimal[]{null, null};
        if (Objects.isNull(basePrice) || basePrice.signum() <= 0) {
            return result;
        }
        BigDecimal stopMult = configService.getDecimal("atr_stop_mult", new BigDecimal("2.0"));
        BigDecimal takeMult = configService.getDecimal("atr_take_mult", new BigDecimal("3.0"));
        BigDecimal atr = calcAtr14(code);
        BigDecimal stop;
        BigDecimal take;
        if (Objects.nonNull(atr) && atr.signum() > 0) {
            stop = basePrice.subtract(atr.multiply(stopMult)).setScale(2, RoundingMode.HALF_UP);
            take = basePrice.add(atr.multiply(takeMult)).setScale(2, RoundingMode.HALF_UP);
            if (stop.signum() <= 0) {
                stop = basePrice.multiply(BigDecimal.ONE.subtract(FALLBACK_STOP_PCT))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            stop = basePrice.multiply(BigDecimal.ONE.subtract(FALLBACK_STOP_PCT))
                    .setScale(2, RoundingMode.HALF_UP);
            take = basePrice.multiply(BigDecimal.ONE.add(FALLBACK_TAKE_PCT))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        result[0] = stop;
        result[1] = take;
        return result;
    }

    private BigDecimal calcAtr14(String code) {
        if (StringUtils.isBlank(code)) {
            return BigDecimal.ZERO;
        }
        String normalized = MarketCodeUtils.normalizeHoldingCode(code);
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, normalized)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit 20"));
        if (CollUtil.isEmpty(bars) || bars.size() < 15) {
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

    private void enrichHoldings(List<PortfolioHolding> holdings) {
        if (CollUtil.isEmpty(holdings)) {
            return;
        }
        List<String> codes = new ArrayList<>();
        for (PortfolioHolding holding : holdings) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isNotBlank(code) && !codes.contains(code)) {
                codes.add(code);
            }
        }
        Map<String, StockBasic> basicMap = new HashMap<>();
        Map<String, List<BigDecimal>> sparkCloseMap = new HashMap<>();
        if (CollUtil.isNotEmpty(codes)) {
            List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .in(StockBasic::getCode, codes));
            for (StockBasic basic : basics) {
                basicMap.put(basic.getCode(), basic);
            }
            List<BarDaily> recentBars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, codes)
                    .ge(BarDaily::getTradeDate, LocalDate.now().minusDays(45))
                    .orderByAsc(BarDaily::getCode)
                    .orderByAsc(BarDaily::getTradeDate));
            for (BarDaily bar : recentBars) {
                if (Objects.nonNull(bar.getClosePrice())) {
                    List<BigDecimal> closes = sparkCloseMap.computeIfAbsent(bar.getCode(), ignored -> new ArrayList<>());
                    closes.add(bar.getClosePrice());
                    if (closes.size() > 20) {
                        closes.remove(0);
                    }
                }
            }
        }
        for (PortfolioHolding holding : holdings) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            fillPnl(holding, basicMap.get(code));
            holding.setSparkCloses(sparkCloseMap.getOrDefault(code, List.of()));
        }
    }

    private void fillPnl(PortfolioHolding holding, StockBasic basic) {
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
        }
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
        BigDecimal pct = holding.getPctChg();
        if (Objects.isNull(pct)) {
            holding.setTodayPnl(null);
        } else {
            BigDecimal denom = BigDecimal.valueOf(100).add(pct);
            if (denom.signum() == 0) {
                holding.setTodayPnl(null);
            } else {
                holding.setTodayPnl(marketValue.multiply(pct).divide(denom, 2, RoundingMode.HALF_UP));
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

    private PortfolioHoldingSaveReq parseImportLine(String raw) {
        String[] parts = LINE_SPLIT.split(raw.trim());
        if (parts.length < 2) {
            throw new BusinessException("至少需要代码与数量");
        }
        String first = parts[0].trim();
        String code = MarketCodeUtils.normalizeHoldingCode(first);
        String name = null;
        if (StringUtils.isBlank(code) || !first.matches(".*\\d.*")) {
            StockBasic byName = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getName, first)
                    .last("LIMIT 1"));
            if (Objects.isNull(byName) || StringUtils.isBlank(byName.getCode())) {
                throw new BusinessException("无法识别代码/名称: " + first);
            }
            code = byName.getCode();
            name = byName.getName();
        }
        int quantity;
        try {
            quantity = Integer.parseInt(parts[1].replace(",", "").trim());
        } catch (Exception ex) {
            throw new BusinessException("数量无效: " + parts[1]);
        }
        BigDecimal cost = null;
        if (parts.length >= 3 && StringUtils.isNotBlank(parts[2])) {
            try {
                cost = new BigDecimal(parts[2].replace(",", "").trim());
            } catch (Exception ex) {
                throw new BusinessException("成本价无效: " + parts[2]);
            }
        }
        PortfolioHoldingSaveReq req = new PortfolioHoldingSaveReq();
        req.setCode(code);
        req.setName(name);
        req.setQuantity(quantity);
        req.setCostPrice(cost);
        return req;
    }

    private void mirrorToMyHolding(Long ownerUserId, PortfolioHolding holding) {
        String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
        LocalDateTime now = LocalDateTime.now();
        MyHolding exist = myHoldingMapper.selectOne(Wrappers.<MyHolding>lambdaQuery()
                .eq(MyHolding::getUserId, ownerUserId)
                .eq(MyHolding::getCode, code)
                .last("LIMIT 1"));
        if (Objects.nonNull(exist)) {
            exist.setName(holding.getName());
            exist.setQuantity(holding.getQuantity());
            exist.setCostPrice(holding.getCostPrice());
            exist.setStopLoss(holding.getStopLoss());
            exist.setTakeProfit(holding.getTakeProfit());
            exist.setNote(holding.getNote());
            exist.setUpdateTime(now);
            myHoldingMapper.updateById(exist);
            return;
        }
        myHoldingMapper.insert(MyHolding.builder()
                .userId(ownerUserId)
                .code(code)
                .name(holding.getName())
                .quantity(holding.getQuantity())
                .costPrice(holding.getCostPrice())
                .stopLoss(holding.getStopLoss())
                .takeProfit(holding.getTakeProfit())
                .note(holding.getNote())
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build());
    }

    private Portfolio requireVisiblePortfolio(Long id) {
        if (Objects.isNull(id)) {
            throw new BusinessException("组合ID不能为空");
        }
        currentUserId();
        Portfolio portfolio = portfolioMapper.selectById(id);
        if (Objects.isNull(portfolio)) {
            throw new BusinessException("组合不存在");
        }
        return portfolio;
    }

    private Portfolio requireEditablePortfolio(Long id) {
        Portfolio portfolio = requireVisiblePortfolio(id);
        Long currentUserId = currentUserId();
        if (!Objects.equals(currentUserId, portfolio.getUserId()) && !userAuthService.isAdmin(currentUserId)) {
            throw new BusinessException("无权修改该组合");
        }
        return portfolio;
    }

    private void assertNameUnique(String name, Long excludeId, Long ownerUserId) {
        var query = Wrappers.<Portfolio>lambdaQuery().eq(Portfolio::getName, name)
                .eq(Portfolio::getUserId, ownerUserId);
        if (Objects.nonNull(excludeId)) {
            query.ne(Portfolio::getId, excludeId);
        }
        Long cnt = portfolioMapper.selectCount(query);
        if (Objects.nonNull(cnt) && cnt > 0) {
            throw new BusinessException("组合名称已存在: " + name);
        }
    }

    private void touchPortfolio(Long portfolioId) {
        Portfolio portfolio = portfolioMapper.selectById(portfolioId);
        if (Objects.nonNull(portfolio)) {
            portfolio.setUpdateTime(LocalDateTime.now());
            portfolioMapper.updateById(portfolio);
        }
    }

    private Long currentUserId() {
        Long currentUserId = userContext.currentUserIdOrNull();
        if (Objects.isNull(currentUserId)) {
            throw new BusinessException("未获取到当前用户");
        }
        return currentUserId;
    }
}
