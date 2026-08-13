package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.ObserveGuideTemplateResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.ObservePoolSaveReq;
import com.awe.apex.quant.domain.dto.ObserveTechSignal;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.ObservePool;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.indicator.IndicatorUtils;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.ObservePoolMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IValuationService;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.StrategyParams;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 观察池服务：CRUD + 触发评估 + 指导模板
 */
@Slf4j
@Service
public class ObservePoolServiceImpl implements IObservePoolService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal NEAR_PCT = new BigDecimal("1.5");
    private static final BigDecimal BUY_STOP_PCT = new BigDecimal("0.05");
    private static final BigDecimal BUY_TARGET_PCT = new BigDecimal("0.10");
    private static final BigDecimal BUY_PULLBACK = new BigDecimal("0.015");
    /** 买入观察自动写入上限 */
    private static final int AUTO_WATCH_LIMIT = 30;
    /** 情绪风向标自动写入上限 */
    private static final int AUTO_MOOD_LIMIT = 15;
    private static final int AUTO_BUY_EXPIRE_TRADING_DAYS = 5;
    private static final int AUTO_MOOD_EXPIRE_TRADING_DAYS = 2;
    private static final int TECH_LOOKBACK_DAYS = 220;
    private static final BigDecimal VOL_SURGE = new BigDecimal("1.5");
    /** 仅归档锁定；HIT_TARGET/STOPPED 允许按现价重估，避免买卖逻辑错判后锁死 */
    private static final Set<String> TERMINAL = Set.of("ARCHIVED");
    private static final Set<String> TRIGGER_TYPES = Set.of(
            "PRICE_ABOVE", "PRICE_BELOW", "PCT_FROM_BASE", "BREAK_HIGH", "MANUAL");

    @Resource
    private ObservePoolMapper observePoolMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private IValuationService valuationService;

    @Resource
    private StrategyParams strategyParams;

    /**
     * 观察池列表（含现场评估）
     *
     * @param status  状态过滤；空则默认排除 ARCHIVED
     * @param side    方向 BUY/SELL/MOOD
     * @param keyword 代码/名称关键字
     * @return 列表
     */
    @Override
    public List<ObservePoolResp> list(String status, String side, String keyword) {
        LambdaQueryWrapper<ObservePool> qw = Wrappers.<ObservePool>lambdaQuery()
                .orderByAsc(ObservePool::getPriority)
                .orderByDesc(ObservePool::getUpdateTime);
        String statusFilter = StringUtils.isNotBlank(status) ? status.trim().toUpperCase() : null;
        if (StringUtils.isBlank(statusFilter)) {
            qw.ne(ObservePool::getStatus, "ARCHIVED");
        } else if ("ARCHIVED".equals(statusFilter)) {
            qw.eq(ObservePool::getStatus, "ARCHIVED");
        } else {
            // 现场评估状态可能与落库不同，库内不过滤非归档状态
            qw.ne(ObservePool::getStatus, "ARCHIVED");
        }
        if (StringUtils.isNotBlank(side)) {
            qw.eq(ObservePool::getSide, side.trim().toUpperCase());
        }
        if (StringUtils.isNotBlank(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(ObservePool::getCode, kw).or().like(ObservePool::getName, kw));
        }
        List<ObservePool> rows = observePoolMapper.selectList(qw);
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }
        Map<String, StockBasic> basics = loadBasics(rows);
        Map<String, List<BarDaily>> barsByCode = loadBarsGrouped(rows);
        List<String> codes = new ArrayList<>();
        for (ObservePool row : rows) {
            if (Objects.nonNull(row) && StringUtils.isNotBlank(row.getCode())) {
                codes.add(row.getCode());
            }
        }
        Map<String, ValuationBriefResp> valuationMap = valuationService.briefBatch(codes);
        List<ObservePoolResp> result = new ArrayList<>();
        for (ObservePool row : rows) {
            // 列表现场评估状态（不落库）；落库走 refresh
            ObservePoolResp item = toResp(row, basics.get(row.getCode()), barsByCode.get(row.getCode()),
                    valuationMap.get(row.getCode()));
            if (StringUtils.isNotBlank(statusFilter)) {
                if ("READY".equals(statusFilter)) {
                    String st = item.getStatus();
                    if (!"TRIGGERED".equals(st) && !"NEAR".equals(st)) {
                        continue;
                    }
                } else if (!statusFilter.equals(item.getStatus())) {
                    continue;
                }
            }
            result.add(item);
        }
        result.sort((a, b) -> {
            int da = statusOrder(a.getStatus());
            int db = statusOrder(b.getStatus());
            if (da != db) {
                return Integer.compare(da, db);
            }
            int sa = sideOrder(a.getSide());
            int sb = sideOrder(b.getSide());
            if (sa != sb) {
                return Integer.compare(sa, sb);
            }
            int pa = Objects.nonNull(a.getPriority()) ? a.getPriority() : 3;
            int pb = Objects.nonNull(b.getPriority()) ? b.getPriority() : 3;
            return Integer.compare(pa, pb);
        });
        return result;
    }

    /**
     * 看板用轻量告警：仅现价评估 TRIGGERED/NEAR，不做估值与技术指标
     *
     * @param limit 返回条数上限
     * @return 接近/已触发列表
     */
    @Override
    public List<ObservePoolResp> listReadyAlerts(int limit) {
        int cap = limit > 0 ? limit : 6;
        List<ObservePool> rows = observePoolMapper.selectList(Wrappers.<ObservePool>lambdaQuery()
                .ne(ObservePool::getStatus, "ARCHIVED")
                .orderByAsc(ObservePool::getPriority)
                .orderByDesc(ObservePool::getUpdateTime));
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }
        Map<String, StockBasic> basics = loadBasics(rows);
        List<ObservePoolResp> ready = new ArrayList<>();
        for (ObservePool row : rows) {
            StockBasic basic = basics.get(row.getCode());
            BigDecimal latest = Objects.nonNull(basic) ? basic.getLatestPrice() : null;
            String status = evaluateStatus(row, latest);
            if (!"TRIGGERED".equals(status) && !"NEAR".equals(status)) {
                continue;
            }
            String name = row.getName();
            if (StringUtils.isBlank(name) && Objects.nonNull(basic)) {
                name = basic.getName();
            }
            ready.add(ObservePoolResp.builder()
                    .id(row.getId())
                    .code(row.getCode())
                    .name(name)
                    .market(row.getMarket())
                    .side(resolveSide(row.getSide(), row.getTags(), row.getReason()))
                    .priority(row.getPriority())
                    .status(status)
                    .latestPrice(latest)
                    .pctChg(Objects.nonNull(basic) ? basic.getPctChg() : null)
                    .build());
        }
        ready.sort((a, b) -> {
            int da = statusOrder(a.getStatus());
            int db = statusOrder(b.getStatus());
            if (da != db) {
                return Integer.compare(da, db);
            }
            int pa = Objects.nonNull(a.getPriority()) ? a.getPriority() : 3;
            int pb = Objects.nonNull(b.getPriority()) ? b.getPriority() : 3;
            return Integer.compare(pa, pb);
        });
        if (ready.size() > cap) {
            return ready.subList(0, cap);
        }
        return ready;
    }

    private int statusOrder(String status) {
        if ("TRIGGERED".equals(status)) {
            return 0;
        }
        if ("NEAR".equals(status)) {
            return 1;
        }
        if ("WATCHING".equals(status)) {
            return 2;
        }
        if ("HIT_TARGET".equals(status)) {
            return 3;
        }
        if ("STOPPED".equals(status)) {
            return 4;
        }
        if ("ARCHIVED".equals(status)) {
            return 5;
        }
        return 9;
    }

    private int sideOrder(String side) {
        if ("BUY".equalsIgnoreCase(side)) {
            return 0;
        }
        if ("MOOD".equalsIgnoreCase(side)) {
            return 1;
        }
        if ("SELL".equalsIgnoreCase(side)) {
            return 2;
        }
        return 9;
    }

    /**
     * 新增或更新
     *
     * @param req 请求
     * @return 实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObservePool save(ObservePoolSaveReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("证券代码不能为空");
        }
        String code = MarketCodeUtils.normalizeHoldingCode(req.getCode());
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("证券代码无效");
        }
        String triggerType = StringUtils.isNotBlank(req.getTriggerType())
                ? req.getTriggerType().trim().toUpperCase()
                : "PRICE_ABOVE";
        if (!TRIGGER_TYPES.contains(triggerType)) {
            throw new BusinessException("不支持的触发类型: " + triggerType);
        }
        int priority = Objects.nonNull(req.getPriority()) ? req.getPriority() : 3;
        if (priority < 1 || priority > 5) {
            throw new BusinessException("优先级需在 1–5");
        }

        String name = StringUtils.trim(req.getName());
        String market = StringUtils.trim(req.getMarket());
        StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("LIMIT 1"));
        if (Objects.nonNull(basic)) {
            if (StringUtils.isBlank(name)) {
                name = basic.getName();
            }
            if (StringUtils.isBlank(market)) {
                market = basic.getMarket();
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ObservePool exist = null;
        if (Objects.nonNull(req.getId())) {
            exist = observePoolMapper.selectById(req.getId());
            if (Objects.isNull(exist)) {
                throw new BusinessException("观察项不存在");
            }
        } else {
            String reqSide = resolveSide(req.getSide(), req.getTags(), req.getReason());
            ObservePool same = observePoolMapper.selectOne(Wrappers.<ObservePool>lambdaQuery()
                    .eq(ObservePool::getCode, code)
                    .eq(ObservePool::getSide, reqSide)
                    .ne(ObservePool::getStatus, "ARCHIVED")
                    .last("LIMIT 1"));
            if (Objects.nonNull(same)) {
                exist = same;
            }
        }

        String status = StringUtils.isNotBlank(req.getStatus()) ? req.getStatus().trim().toUpperCase() : null;
        String side = resolveSide(req.getSide(), req.getTags(), req.getReason());
        if (Objects.nonNull(exist)) {
            exist.setCode(code);
            exist.setName(name);
            exist.setMarket(market);
            exist.setSide(side);
            exist.setReason(StringUtils.trim(req.getReason()));
            exist.setGuideText(StringUtils.trim(req.getGuideText()));
            exist.setTriggerType(triggerType);
            exist.setTriggerExpr(StringUtils.trim(req.getTriggerExpr()));
            exist.setTriggerPrice(req.getTriggerPrice());
            exist.setStopLoss(req.getStopLoss());
            exist.setTargetPrice(req.getTargetPrice());
            exist.setBasePrice(req.getBasePrice());
            exist.setPriority(priority);
            if (StringUtils.isNotBlank(status)) {
                exist.setStatus(status);
            }
            exist.setNote(StringUtils.trim(req.getNote()));
            exist.setTags(StringUtils.trim(req.getTags()));
            exist.setUpdateTime(now);
            observePoolMapper.updateById(exist);
            log.info("观察池更新 id={} code={}", exist.getId(), code);
            return exist;
        }

        ObservePool created = ObservePool.builder()
                .code(code)
                .name(name)
                .market(market)
                .side(side)
                .reason(StringUtils.trim(req.getReason()))
                .guideText(StringUtils.trim(req.getGuideText()))
                .triggerType(triggerType)
                .triggerExpr(StringUtils.trim(req.getTriggerExpr()))
                .triggerPrice(req.getTriggerPrice())
                .stopLoss(req.getStopLoss())
                .targetPrice(req.getTargetPrice())
                .basePrice(req.getBasePrice())
                .priority(priority)
                .status(StringUtils.isNotBlank(status) ? status : "WATCHING")
                .note(StringUtils.trim(req.getNote()))
                .tags(StringUtils.trim(req.getTags()))
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        observePoolMapper.insert(created);
        log.info("观察池新增 id={} code={}", created.getId(), code);
        return created;
    }

    /**
     * 删除
     *
     * @param id 主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (Objects.isNull(id)) {
            throw new BusinessException("id 不能为空");
        }
        ObservePool row = observePoolMapper.selectById(id);
        if (Objects.isNull(row)) {
            throw new BusinessException("观察项不存在");
        }
        observePoolMapper.deleteById(id);
    }

    /**
     * 归档
     *
     * @param id 主键
     * @return 实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObservePool archive(Long id) {
        if (Objects.isNull(id)) {
            throw new BusinessException("id 不能为空");
        }
        ObservePool row = observePoolMapper.selectById(id);
        if (Objects.isNull(row)) {
            throw new BusinessException("观察项不存在");
        }
        row.setStatus("ARCHIVED");
        row.setUpdateTime(LocalDateTime.now());
        observePoolMapper.updateById(row);
        return row;
    }

    /**
     * 刷新评估并回写状态
     *
     * @return 统计
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refresh() {
        List<ObservePool> rows = observePoolMapper.selectList(Wrappers.<ObservePool>lambdaQuery()
                .notIn(ObservePool::getStatus, List.of("ARCHIVED"))
                .orderByAsc(ObservePool::getPriority));
        Map<String, StockBasic> basics = loadBasics(rows);
        int near = 0;
        int triggered = 0;
        int hitTarget = 0;
        int stopped = 0;
        int watching = 0;
        int archived = 0;
        LocalDateTime now = LocalDateTime.now();
        Map<String, List<BarDaily>> barsByCode = loadBarsGrouped(rows);
        for (ObservePool row : rows) {
            String resolvedSide = resolveSide(row.getSide(), row.getTags(), row.getReason());
            boolean sideDirty = !resolvedSide.equalsIgnoreCase(nullToEmpty(row.getSide()));
            if (sideDirty) {
                row.setSide(resolvedSide);
            }
            ObservePoolResp eval = toResp(row, basics.get(row.getCode()), barsByCode.get(row.getCode()), null);
            String next = eval.getStatus();
            String archiveReason = autoArchiveReason(row, next, now.toLocalDate());
            if (StringUtils.isNotBlank(archiveReason)) {
                row.setStatus("ARCHIVED");
                row.setNote(appendNote(row.getNote(), archiveReason));
                row.setUpdateTime(now);
                observePoolMapper.updateById(row);
                archived++;
                continue;
            }
            if (sideDirty || !Objects.equals(row.getStatus(), next)) {
                if ("TRIGGERED".equals(next) && Objects.isNull(row.getTriggeredAt())) {
                    row.setTriggeredAt(now);
                }
                row.setStatus(next);
                row.setUpdateTime(now);
                observePoolMapper.updateById(row);
            }
            switch (next) {
                case "NEAR" -> near++;
                case "TRIGGERED" -> triggered++;
                case "HIT_TARGET" -> hitTarget++;
                case "STOPPED" -> stopped++;
                default -> watching++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", rows.size());
        stats.put("watching", watching);
        stats.put("near", near);
        stats.put("triggered", triggered);
        stats.put("hitTarget", hitTarget);
        stats.put("stopped", stopped);
        stats.put("archived", archived);
        log.info("观察池刷新完成 total={} near={} triggered={} archived={}",
                rows.size(), near, triggered, archived);
        return stats;
    }

    /**
     * 按原因返回指导模板
     *
     * @param reason 原因关键词
     * @return 模板
     */
    @Override
    public ObserveGuideTemplateResp guideTemplate(String reason) {
        List<ObserveGuideTemplateResp> all = guideTemplates();
        if (StringUtils.isBlank(reason)) {
            return all.get(0);
        }
        String key = reason.trim();
        for (ObserveGuideTemplateResp t : all) {
            if (key.contains(t.getReason()) || t.getReason().contains(key)
                    || (StringUtils.isNotBlank(t.getTags()) && t.getTags().contains(key))) {
                return t;
            }
        }
        return all.get(0);
    }

    /**
     * 全部指导模板
     *
     * @return 模板列表
     */
    @Override
    public List<ObserveGuideTemplateResp> guideTemplates() {
        List<ObserveGuideTemplateResp> list = new ArrayList<>();
        list.add(ObserveGuideTemplateResp.builder()
                .reason("突破回踩")
                .triggerType("PRICE_ABOVE")
                .triggerExpr("回踩不破平台低点，成交量缩至突破日 50% 以内再放量转强")
                .tags("突破,回踩,趋势")
                .guideText("""
                        【场景】标的刚突破关键压力位/平台，等待回踩确认而非追高。
                        【观察】记录突破日高点与平台低点；回踩时看量能是否明显萎缩。
                        【触发】现价重新站上触发价（建议取突破日收盘或平台上沿），且补充条件满足。
                        【买入】触发后分 2 批：首仓 30%–40%，确认站稳再加。
                        【止损】跌破止损价（平台低点或触发价下 3%–5%）当日收盘确认离场。
                        【目标】目标价 1 看前高/量度升幅；到达后减仓 1/2，余仓抬止损至成本。
                        【禁忌】放量跌破平台不抄；开盘一字板不追。
                        """)
                .build());
        list.add(ObserveGuideTemplateResp.builder()
                .reason("低估等待")
                .triggerType("PRICE_BELOW")
                .triggerExpr("估值分位偏低且无重大利空；最好配合大盘非极弱日")
                .tags("价值,低估,定投")
                .guideText("""
                        【场景】基本面尚可、估值偏低，等待价格跌至心理买点。
                        【观察】跟踪 PE/PB 分位与行业景气，避免「越跌越买」无纪律。
                        【触发】现价跌破或触及触发价（你的买点），再检查补充条件。
                        【买入】触发后分批：先 1/3，跌破再加 1/3，反弹确认后补齐。
                        【止损】若跌破止损价且基本面恶化（业绩雷/监管），停止加仓并减仓。
                        【目标】目标价对应合理估值中枢；到达后评估是否转为持有或兑现。
                        【禁忌】只因便宜无视流动性与退市风险。
                        """)
                .build());
        list.add(ObserveGuideTemplateResp.builder()
                .reason("情绪分歧")
                .triggerType("PCT_FROM_BASE")
                .triggerExpr("高位分歧日缩量，次日低开不破关键均线可试错")
                .tags("短线,情绪,连板")
                .guideText("""
                        【场景】题材高潮后分歧，观察能否低吸晋级或止盈离场。
                        【观察】记基准价（分歧日收盘）；看竞价与前 30 分钟量价。
                        【触发】相对基准价达到设定涨跌幅（如回撤 -3% 或反抽 +2%），按你填写的方向执行。
                        【买入/卖出】偏做多：缩量回踩触发价分仓试错；偏兑现：冲高到目标价减仓。
                        【止损】跌破止损价或板块集体退潮，立即离场，不扛板。
                        【目标】目标价设前高或空间板位置；到达先兑现利润。
                        【禁忌】高潮日尾盘追高；忽略大盘系统性杀跌。
                        """)
                .build());
        list.add(ObserveGuideTemplateResp.builder()
                .reason("均线支撑")
                .triggerType("PRICE_ABOVE")
                .triggerExpr("收盘重新站上 MA20，且 MA5 拐头向上")
                .tags("趋势,均线")
                .guideText("""
                        【场景】上升趋势中短暂跌破均线，等待重新站上再介入。
                        【观察】以 MA20/MA60 为生命线，观察是否缩量回踩。
                        【触发】现价/收盘站上触发价（可填均线附近价格）且补充条件满足。
                        【买入】触发次日开盘附近建仓，仓位不超过计划上限的 50%。
                        【止损】再次跌破止损价（均线下 2%–3%）离场。
                        【目标】前高或通道上轨；到达减半仓，余仓跟踪均线。
                        【禁忌】阴跌阴跌中的「假站上」连续两日确认再加仓。
                        """)
                .build());
        list.add(ObserveGuideTemplateResp.builder()
                .reason("事件驱动")
                .triggerType("MANUAL")
                .triggerExpr("公告落地/解禁过峰/政策催化确认后再动手")
                .tags("事件,公告,催化")
                .guideText("""
                        【场景】有明确事件日历，价格反应可能滞后或过度。
                        【观察】写下事件日与预期差；盘前核对是否已兑现。
                        【触发】本类型以手工确认触发：事件落地且价量符合你的 trigger_expr。
                        【买入】利好超预期且未大幅高开：开盘 15 分钟内分批；已炒作透支则放弃。
                        【止损】事件证伪或跌破止损价，无条件走。
                        【目标】按事件弹性设目标价，到达即减，不赌无限想象。
                        【禁忌】小道消息重仓；停牌临近不做博弈。
                        """)
                .build());
        return list;
    }

    /**
     * 观察池只承接「准备买入」剧本；持仓卖出留在决策页，不写入观察池。
     *
     * @param buys  买入建议
     * @param sells 卖出建议（忽略，保留参数兼容调用方）
     * @return 同步统计
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncFromDecision(List<DecisionItemResp> buys, List<DecisionItemResp> sells) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int archivedSells = archiveAutoSellRows();
        List<DecisionItemResp> buyList = CollUtil.isEmpty(buys) ? List.of() : buys;

        List<DecisionItemResp> sorted = new ArrayList<>(buyList);
        sorted.sort(Comparator.comparing(
                (DecisionItemResp x) -> Objects.nonNull(x.getScore()) ? x.getScore() : BigDecimal.ZERO).reversed());
        int watchCount = 0;
        int moodCount = 0;
        for (DecisionItemResp item : sorted) {
            if (Objects.isNull(item) || StringUtils.isBlank(item.getCode())) {
                skipped++;
                continue;
            }
            // 已持仓加仓信号不进观察池
            if (nullToEmpty(item.getReason()).contains("已在我的持仓")) {
                skipped++;
                continue;
            }
            boolean mood = "MOOD".equalsIgnoreCase(item.getAction())
                    || "MOOD".equalsIgnoreCase(item.getStrategyId())
                    || nullToEmpty(item.getReason()).contains("情绪观察");
            if (mood) {
                if (moodCount >= AUTO_MOOD_LIMIT) {
                    skipped++;
                    continue;
                }
            } else if (watchCount >= AUTO_WATCH_LIMIT) {
                skipped++;
                continue;
            }
            boolean isNew = upsertDecisionItem(item, mood ? "MOOD" : "BUY");
            if (isNew) {
                created++;
            } else {
                updated++;
            }
            if (mood) {
                moodCount++;
            } else {
                watchCount++;
            }
        }
        if (CollUtil.isNotEmpty(sells)) {
            skipped += sells.size();
        }

        Map<String, Object> eval = refresh();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("created", created);
        stats.put("updated", updated);
        stats.put("skipped", skipped);
        stats.put("archivedSells", archivedSells);
        stats.put("watchCount", watchCount);
        stats.put("moodCount", moodCount);
        stats.put("upserted", created + updated);
        stats.put("near", eval.get("near"));
        stats.put("triggered", eval.get("triggered"));
        stats.put("watching", eval.get("watching"));
        log.info("决策同步观察池 created={} updated={} watch={} mood={} archivedSells={}",
                created, updated, watchCount, moodCount, archivedSells);
        return stats;
    }

    /**
     * 归档历史上误写入的「自动卖出」观察项
     */
    private int archiveAutoSellRows() {
        List<ObservePool> sellRows = observePoolMapper.selectList(Wrappers.<ObservePool>lambdaQuery()
                .eq(ObservePool::getSide, "SELL")
                .ne(ObservePool::getStatus, "ARCHIVED")
                .and(w -> w.like(ObservePool::getTags, "决策")
                        .or()
                        .like(ObservePool::getReason, "决策卖出")
                        .or()
                        .like(ObservePool::getReason, "持仓卖出")));
        if (CollUtil.isEmpty(sellRows)) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        for (ObservePool row : sellRows) {
            row.setStatus("ARCHIVED");
            row.setUpdateTime(now);
            observePoolMapper.updateById(row);
            n++;
        }
        return n;
    }

    /**
     * @param sideKind BUY / SELL / MOOD
     * @return true=新建，false=更新
     */
    private boolean upsertDecisionItem(DecisionItemResp item, String sideKind) {
        String code = MarketCodeUtils.normalizeHoldingCode(item.getCode());
        if (StringUtils.isBlank(code)) {
            return false;
        }
        StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("LIMIT 1"));
        BigDecimal latest = Objects.nonNull(basic) ? basic.getLatestPrice() : null;
        if (Objects.isNull(latest) || latest.signum() <= 0) {
            log.warn("决策写入观察池跳过：无最新价 code={}", code);
            return false;
        }

        String side = StringUtils.isNotBlank(sideKind) ? sideKind.toUpperCase() : "BUY";
        if (!"BUY".equals(side) && !"SELL".equals(side) && !"MOOD".equals(side)) {
            side = "BUY";
        }
        boolean buySide = "BUY".equals(side);
        boolean moodSide = "MOOD".equals(side);
        String strategyId = StringUtils.isNotBlank(item.getStrategyId()) ? item.getStrategyId()
                : (moodSide ? "MOOD" : "S1");
        String templateReason = mapStrategyReason(strategyId, buySide || moodSide);
        ObserveGuideTemplateResp template = guideTemplate(templateReason);

        BigDecimal triggerPrice;
        BigDecimal stopLoss;
        BigDecimal targetPrice;
        BigDecimal basePrice = latest;
        String triggerType;
        String triggerExpr;
        String status;
        String reason;
        int priority = priorityFromScore(item.getScore());

        if (moodSide) {
            triggerType = "MANUAL";
            triggerPrice = latest;
            stopLoss = latest.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP);
            targetPrice = latest.multiply(new BigDecimal("1.08")).setScale(2, RoundingMode.HALF_UP);
            triggerExpr = "非买卖触发：关注热度退潮、跌幅收敛、题材跟风强弱；类似德明利式情绪温度计";
            status = "WATCHING";
            reason = trim("情绪观察 · " + strategyLabel(strategyId) + " · " + nullToEmpty(item.getReason()), 240);
            priority = Math.min(Math.max(priority, 2), 4);
        } else if (buySide) {
            boolean chaseRisk = nullToEmpty(item.getReason()).contains("勿追")
                    || nullToEmpty(item.getReason()).contains("回踩再评估")
                    || nullToEmpty(item.getReason()).contains("优先观察回踩")
                    || containsRiskFlag(item, "勿追高");
            // Scorer 禁止可执行 / 高估突破：一律等回踩，不得因高分直接 TRIGGERED
            boolean scorerBlock = Boolean.FALSE.equals(item.getExecutableHint())
                    || containsRiskFlag(item, "高估突破降权");
            BigDecimal executableFloor = strategyParams.decisionExecutableScore();
            boolean scoreOk = Objects.nonNull(item.getScore())
                    && item.getScore().compareTo(executableFloor) >= 0;
            boolean hintOk = Objects.isNull(item.getExecutableHint())
                    || Boolean.TRUE.equals(item.getExecutableHint());
            boolean executable = !chaseRisk && !scorerBlock && scoreOk && hintOk;
            if (executable) {
                triggerType = "PRICE_ABOVE";
                triggerPrice = latest;
                triggerExpr = "信号强、可按指导分批试错；" + nullToEmpty(item.getExitRule());
                status = "TRIGGERED";
                reason = trim("决策关注 · 可执行 · " + strategyLabel(strategyId) + " · " + nullToEmpty(item.getReason()), 240);
            } else if (chaseRisk) {
                // 近端已大涨：强制等回调，禁止写成「可执行追涨」
                triggerType = "PRICE_BELOW";
                triggerPrice = latest.multiply(BigDecimal.ONE.subtract(BUY_PULLBACK.max(new BigDecimal("0.03"))))
                        .setScale(2, RoundingMode.HALF_UP);
                triggerExpr = "近端涨幅偏大，回踩至触发价且量能收敛后再评估，禁止追高；"
                        + nullToEmpty(item.getExitRule());
                status = "WATCHING";
                reason = trim("决策观察 · " + strategyLabel(strategyId) + " · " + nullToEmpty(item.getReason()), 240);
            } else if ("S3".equalsIgnoreCase(strategyId)) {
                triggerType = "PRICE_ABOVE";
                triggerPrice = latest.multiply(new BigDecimal("1.01")).setScale(2, RoundingMode.HALF_UP);
                triggerExpr = "观察突破后回踩确认，站稳触发价且放量再评估买入；" + nullToEmpty(item.getExitRule());
                status = "WATCHING";
                reason = trim("决策观察 · " + strategyLabel(strategyId) + " · " + nullToEmpty(item.getReason()), 240);
            } else {
                triggerType = "PRICE_BELOW";
                triggerPrice = latest.multiply(BigDecimal.ONE.subtract(BUY_PULLBACK)).setScale(2, RoundingMode.HALF_UP);
                triggerExpr = "观察回调至触发价附近企稳放量，再评估分批买入；" + nullToEmpty(template.getTriggerExpr());
                status = "WATCHING";
                reason = trim("决策观察 · " + strategyLabel(strategyId) + " · " + nullToEmpty(item.getReason()), 240);
            }
            stopLoss = latest.multiply(BigDecimal.ONE.subtract(BUY_STOP_PCT)).setScale(2, RoundingMode.HALF_UP);
            targetPrice = latest.multiply(BigDecimal.ONE.add(BUY_TARGET_PCT)).setScale(2, RoundingMode.HALF_UP);
        } else {
            triggerType = "PRICE_BELOW";
            triggerPrice = latest;
            stopLoss = latest.multiply(new BigDecimal("1.03")).setScale(2, RoundingMode.HALF_UP);
            targetPrice = latest.multiply(new BigDecimal("0.95")).setScale(2, RoundingMode.HALF_UP);
            triggerExpr = "决策卖出/风控触发，优先减仓或清仓；" + nullToEmpty(item.getExitRule());
            status = "TRIGGERED";
            reason = trim("决策卖出 · " + strategyLabel(strategyId) + " · " + nullToEmpty(item.getReason()), 240);
            priority = Math.min(priority, 2);
        }

        String guide = moodSide
                ? buildMoodGuide(item, latest, stopLoss, targetPrice)
                : buildAutoGuide(buySide, item, template, triggerPrice, stopLoss, targetPrice, latest);
        String tags = moodSide
                ? "决策,自动,情绪,风向标," + strategyId
                : (buySide
                ? "决策,自动,买入," + strategyId + (Boolean.TRUE.equals(item.getMainlineMatch()) ? ",主线" : "")
                : "决策,自动,卖出," + strategyId);
        String name = StringUtils.isNotBlank(item.getName())
                ? item.getName()
                : (Objects.nonNull(basic) ? basic.getName() : null);
        String market = Objects.nonNull(basic) ? basic.getMarket() : null;

        LocalDateTime now = LocalDateTime.now();
        ObservePool exist = observePoolMapper.selectOne(Wrappers.<ObservePool>lambdaQuery()
                .eq(ObservePool::getCode, code)
                .eq(ObservePool::getSide, side)
                .ne(ObservePool::getStatus, "ARCHIVED")
                .last("LIMIT 1"));
        if (Objects.isNull(exist) && moodSide) {
            ObservePool legacyHot = observePoolMapper.selectOne(Wrappers.<ObservePool>lambdaQuery()
                    .eq(ObservePool::getCode, code)
                    .eq(ObservePool::getSide, "BUY")
                    .ne(ObservePool::getStatus, "ARCHIVED")
                    .and(w -> w.like(ObservePool::getTags, "HOT")
                            .or()
                            .like(ObservePool::getReason, "热点共振"))
                    .last("LIMIT 1"));
            if (Objects.nonNull(legacyHot)) {
                exist = legacyHot;
            }
        }
        if (Objects.isNull(exist)) {
            ObservePool legacy = observePoolMapper.selectOne(Wrappers.<ObservePool>lambdaQuery()
                    .eq(ObservePool::getCode, code)
                    .ne(ObservePool::getStatus, "ARCHIVED")
                    .last("LIMIT 1"));
            if (Objects.nonNull(legacy)) {
                String legacySide = resolveSide(legacy.getSide(), legacy.getTags(), legacy.getReason());
                if (side.equals(legacySide) || StringUtils.isBlank(legacy.getSide())) {
                    exist = legacy;
                }
            }
        }
        if (Objects.nonNull(exist) && StringUtils.isNotBlank(exist.getTags()) && exist.getTags().contains("手动")) {
            return false;
        }

        if (Objects.nonNull(exist)) {
            exist.setName(name);
            exist.setMarket(market);
            exist.setSide(side);
            exist.setReason(reason);
            exist.setGuideText(guide);
            exist.setTriggerType(triggerType);
            exist.setTriggerExpr(triggerExpr);
            exist.setTriggerPrice(triggerPrice);
            exist.setStopLoss(stopLoss);
            exist.setTargetPrice(targetPrice);
            exist.setBasePrice(basePrice);
            exist.setPriority(priority);
            exist.setStatus(status);
            if ("TRIGGERED".equals(status) && Objects.isNull(exist.getTriggeredAt())) {
                exist.setTriggeredAt(now);
            }
            exist.setTags(tags);
            exist.setNote(buildObserveNote(item));
            exist.setDecisionUpdatedAt(now);
            exist.setUpdateTime(now);
            observePoolMapper.updateById(exist);
            return false;
        }

        ObservePool created = ObservePool.builder()
                .code(code)
                .name(name)
                .market(market)
                .side(side)
                .reason(reason)
                .guideText(guide)
                .triggerType(triggerType)
                .triggerExpr(triggerExpr)
                .triggerPrice(triggerPrice)
                .stopLoss(stopLoss)
                .targetPrice(targetPrice)
                .basePrice(basePrice)
                .priority(priority)
                .status(status)
                .triggeredAt("TRIGGERED".equals(status) ? now : null)
                .tags(tags)
                .note(buildObserveNote(item))
                .decisionUpdatedAt(now)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        observePoolMapper.insert(created);
        return true;
    }

    static String autoArchiveReason(ObservePool row, String status, LocalDate today) {
        if (!isAutomatic(row)) {
            return null;
        }
        if ("HIT_TARGET".equals(status)) {
            return "自动归档：触及目标";
        }
        if ("STOPPED".equals(status)) {
            return "自动归档：触发止损";
        }
        LocalDateTime decisionUpdatedAt = Objects.nonNull(row.getDecisionUpdatedAt())
                ? row.getDecisionUpdatedAt() : row.getUpdateTime();
        if (Objects.isNull(decisionUpdatedAt) || Objects.isNull(today)) {
            return null;
        }
        int elapsedTradingDays = countElapsedTradingDays(decisionUpdatedAt.toLocalDate(), today);
        if (row.getTags().contains("情绪") && elapsedTradingDays >= AUTO_MOOD_EXPIRE_TRADING_DAYS) {
            return "自动归档：自动情绪观察超过 2 个交易日未更新";
        }
        if (elapsedTradingDays >= AUTO_BUY_EXPIRE_TRADING_DAYS) {
            return "自动归档：自动买入观察超过 5 个交易日未更新";
        }
        return null;
    }

    private static boolean isAutomatic(ObservePool row) {
        return Objects.nonNull(row) && StringUtils.isNotBlank(row.getTags()) && row.getTags().contains("自动");
    }

    private static int countElapsedTradingDays(LocalDate start, LocalDate end) {
        if (Objects.isNull(start) || Objects.isNull(end) || !start.isBefore(end)) {
            return 0;
        }
        int count = 0;
        LocalDate cursor = start.plusDays(1);
        while (!cursor.isAfter(end)) {
            if (TradingCalendar.isTradingDay(cursor)) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    private String appendNote(String note, String archiveReason) {
        if (StringUtils.isBlank(note)) {
            return archiveReason;
        }
        if (note.contains(archiveReason)) {
            return note;
        }
        return trim(note + "；" + archiveReason, 512);
    }

    private String buildObserveNote(DecisionItemResp item) {
        StringBuilder sb = new StringBuilder();
        sb.append("来源智能决策");
        if (Objects.nonNull(item.getActionDate())) {
            sb.append(' ').append(item.getActionDate());
        }
        if (StringUtils.isNotBlank(item.getLinkHint())) {
            sb.append(" · ").append(item.getLinkHint());
        }
        if (StringUtils.isNotBlank(item.getScoreExplain())) {
            sb.append(" · ").append(item.getScoreExplain());
        }
        if (StringUtils.isNotBlank(item.getFundNote())) {
            sb.append(" · ").append(item.getFundNote());
        }
        return trim(sb.toString(), 240);
    }

    /**
     * 决策风险旗标是否包含关键字
     */
    private boolean containsRiskFlag(DecisionItemResp item, String keyword) {
        if (Objects.isNull(item) || CollUtil.isEmpty(item.getRiskFlags()) || StringUtils.isBlank(keyword)) {
            return false;
        }
        for (String flag : item.getRiskFlags()) {
            if (StringUtils.isNotBlank(flag) && flag.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析方向：情绪 / 卖出 / 买入
     */
    private String resolveSide(String side, String tags, String reason) {
        String blob = (nullToEmpty(tags) + " " + nullToEmpty(reason)).toLowerCase();
        boolean moodHint = blob.contains("情绪观察") || blob.contains("风向标") || blob.contains(",情绪")
                || blob.contains("情绪,") || blob.contains("温度计") || blob.contains("非买入");
        if (moodHint || "MOOD".equalsIgnoreCase(side)) {
            return "MOOD";
        }
        boolean sellHint = blob.contains("决策卖出") || blob.contains("持仓卖出")
                || blob.contains("卖出剧本") || blob.contains(",卖出") || blob.contains("卖出,");
        boolean buyHint = blob.contains("决策买入") || blob.contains("买入剧本") || blob.contains("决策观察")
                || blob.contains(",买入") || blob.contains("买入,");
        if (sellHint && !buyHint) {
            return "SELL";
        }
        if (buyHint && !sellHint) {
            return "BUY";
        }
        if ("SELL".equalsIgnoreCase(side) || "BUY".equalsIgnoreCase(side) || "MOOD".equalsIgnoreCase(side)) {
            return side.toUpperCase();
        }
        if (blob.contains("卖出") || blob.contains("sell")) {
            return "SELL";
        }
        return "BUY";
    }

    private String buildMoodGuide(DecisionItemResp item, BigDecimal latest, BigDecimal stop, BigDecimal target) {
        StringBuilder sb = new StringBuilder();
        sb.append("【情绪观察】非买入剧本。用来读市场温度/风险偏好，类似「德明利」式焦点票：\n")
                .append("即使连续下跌，只要仍是热搜/题材核心，就能反映短线情绪强弱。\n");
        sb.append("【现价参考】").append(money(latest))
                .append(" · 退潮参考 ").append(money(stop))
                .append(" · 修复参考 ").append(money(target))
                .append('\n');
        if (StringUtils.isNotBlank(item.getReason())) {
            sb.append("【为何盯它】").append(item.getReason()).append('\n');
        }
        sb.append("【看什么】热度是否退潮、跌幅是否收敛、板块跟风是否减弱、有无资金继续博弈。\n");
        sb.append("【怎么用】默认不加仓；仅当情绪修复且出现独立买点时，再手工转为「买入观察」。\n");
        if (StringUtils.isNotBlank(item.getExitRule())) {
            sb.append("【移出】").append(item.getExitRule()).append('\n');
        }
        return sb.toString();
    }

    private String buildAutoGuide(boolean buySide, DecisionItemResp item, ObserveGuideTemplateResp template,
                                  BigDecimal trigger, BigDecimal stop, BigDecimal target, BigDecimal latest) {
        StringBuilder sb = new StringBuilder();
        sb.append("【自动决策】").append(buySide ? "买入观察剧本" : "卖出剧本")
                .append(" · 策略 ").append(strategyLabel(item.getStrategyId()))
                .append(" · 评分 ").append(Objects.nonNull(item.getScore()) ? item.getScore().setScale(1, RoundingMode.HALF_UP) : "-")
                .append('\n');
        if (Objects.nonNull(item.getSuggestedWeight())) {
            sb.append("【建议仓位】")
                    .append(item.getSuggestedWeight().multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP))
                    .append("%\n");
        }
        sb.append("【现价基准】").append(money(latest))
                .append(" · 触发 ").append(money(trigger))
                .append(" · 止损 ").append(money(stop))
                .append(" · 目标 ").append(money(target))
                .append('\n');
        if (StringUtils.isNotBlank(item.getReason())) {
            sb.append("【决策理由】").append(item.getReason()).append('\n');
        }
        if (StringUtils.isNotBlank(item.getScoreExplain())) {
            sb.append("【评分拆解】").append(item.getScoreExplain()).append('\n');
        }
        if (StringUtils.isNotBlank(item.getExitRule())) {
            sb.append("【离场规则】").append(item.getExitRule()).append('\n');
        }
        if (Boolean.TRUE.equals(item.getMainlineMatch())) {
            sb.append("【主线】匹配「").append(nullToEmpty(item.getMainlineName())).append("」\n");
        }
        if (Objects.nonNull(template) && StringUtils.isNotBlank(template.getGuideText())) {
            sb.append('\n').append(template.getGuideText().trim());
        } else if (buySide) {
            sb.append('\n').append("""
                    【执行】触发后分 2 批建仓；严格止损；到目标先减半。
                    【禁忌】无视止损扛单；逆主线满仓猛加。
                    """);
        } else {
            sb.append('\n').append("""
                    【执行】已触发卖出信号：优先减仓/清仓，不博弈反弹。
                    【禁忌】止损单改为「再看看」。
                    """);
        }
        return sb.toString();
    }

    private String mapStrategyReason(String strategyId, boolean buySide) {
        if (!buySide) {
            return "事件驱动";
        }
        if ("HOT".equalsIgnoreCase(strategyId) || "MOOD".equalsIgnoreCase(strategyId)) {
            return "情绪分歧";
        }
        if ("S3".equalsIgnoreCase(strategyId)) {
            return "突破回踩";
        }
        if ("S2".equalsIgnoreCase(strategyId)) {
            return "均线支撑";
        }
        if ("S1".equalsIgnoreCase(strategyId)) {
            return "均线支撑";
        }
        return "突破回踩";
    }

    private String strategyLabel(String strategyId) {
        if ("S1".equalsIgnoreCase(strategyId)) {
            return "S1均线趋势";
        }
        if ("S2".equalsIgnoreCase(strategyId)) {
            return "S2 RSI回调";
        }
        if ("S3".equalsIgnoreCase(strategyId)) {
            return "S3突破放量";
        }
        if ("RISK".equalsIgnoreCase(strategyId)) {
            return "风控";
        }
        if ("HOT".equalsIgnoreCase(strategyId)) {
            return "热点共振";
        }
        if ("MOOD".equalsIgnoreCase(strategyId)) {
            return "情绪风向";
        }
        return StringUtils.isNotBlank(strategyId) ? strategyId : "策略";
    }

    private int priorityFromScore(BigDecimal score) {
        if (Objects.isNull(score)) {
            return 3;
        }
        if (score.compareTo(new BigDecimal("85")) >= 0) {
            return 1;
        }
        if (score.compareTo(new BigDecimal("70")) >= 0) {
            return 2;
        }
        if (score.compareTo(new BigDecimal("55")) >= 0) {
            return 3;
        }
        return 4;
    }

    private String nullToEmpty(String s) {
        return StringUtils.isNotBlank(s) ? s : "";
    }

    private String trim(String s, int max) {
        if (StringUtils.isBlank(s)) {
            return s;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private Map<String, StockBasic> loadBasics(List<ObservePool> rows) {
        Map<String, StockBasic> map = new HashMap<>();
        if (CollUtil.isEmpty(rows)) {
            return map;
        }
        List<String> codes = rows.stream().map(ObservePool::getCode).filter(StringUtils::isNotBlank).distinct().toList();
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, codes));
        for (StockBasic b : basics) {
            map.put(b.getCode(), b);
        }
        return map;
    }

    /**
     * 转响应并现场评估状态 + 技术指标
     */
    private ObservePoolResp toResp(ObservePool row, StockBasic basic, List<BarDaily> bars,
                                   ValuationBriefResp valuation) {
        BigDecimal latest = Objects.nonNull(basic) ? basic.getLatestPrice() : null;
        BigDecimal pctChg = Objects.nonNull(basic) ? basic.getPctChg() : null;
        BigDecimal pctToTrigger = pctDistance(latest, effectiveTriggerPrice(row, latest));
        BigDecimal pctToStop = pctDistance(latest, row.getStopLoss());
        BigDecimal pctToTarget = pctDistance(latest, row.getTargetPrice());

        String side = resolveSide(row.getSide(), row.getTags(), row.getReason());
        String status = evaluateStatus(row, latest);
        String hint = buildHint(row, latest, pctToTrigger, status);

        List<ObserveTechSignal> techSignals = evaluateTech(side, bars);
        int hit = 0;
        for (ObserveTechSignal s : techSignals) {
            if (Boolean.TRUE.equals(s.getHit())) {
                hit++;
            }
        }
        String techSummary = CollUtil.isEmpty(techSignals)
                ? "日线不足，无法评估指标"
                : ("技术 " + hit + "/" + techSignals.size()
                + (hit >= 5 ? " · 共振偏强" : hit >= 3 ? " · 部分满足" : " · 偏弱"));

        String strategyId = extractStrategyId(row);
        String strategyName = strategyLabel(strategyId);
        String setupStyle = setupStyleOf(side, strategyId, row.getReason());
        BigDecimal pct2d = recentPct(bars, 2);
        BigDecimal pct5d = recentPct(bars, 5);
        List<String> pickReasons = buildPickReasons(row, side, strategyName, setupStyle, pctChg, pct2d, pct5d,
                techSummary, valuation);
        List<String> riskFlags = buildRiskFlags(side, pctChg, pct2d, pct5d, row.getReason(), status);
        if (Objects.nonNull(valuation) && ("OVERVALUED".equals(valuation.getLevel())
                || "SLIGHTLY_EXPENSIVE".equals(valuation.getLevel()))) {
            riskFlags = new ArrayList<>(riskFlags);
            riskFlags.add("估值" + valuation.getLevelLabel() + "，谨慎追高");
        }

        return ObservePoolResp.builder()
                .id(row.getId())
                .code(row.getCode())
                .name(row.getName())
                .market(row.getMarket())
                .side(side)
                .reason(row.getReason())
                .guideText(row.getGuideText())
                .triggerType(row.getTriggerType())
                .triggerExpr(row.getTriggerExpr())
                .triggerPrice(row.getTriggerPrice())
                .stopLoss(row.getStopLoss())
                .targetPrice(row.getTargetPrice())
                .basePrice(row.getBasePrice())
                .priority(row.getPriority())
                .status(status)
                .triggeredAt(row.getTriggeredAt())
                .note(row.getNote())
                .tags(row.getTags())
                .decisionUpdatedAt(row.getDecisionUpdatedAt())
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .latestPrice(latest)
                .pctChg(pctChg)
                .pctToTrigger(pctToTrigger)
                .pctToStop(pctToStop)
                .pctToTarget(pctToTarget)
                .statusHint(hint)
                .triggerLabel(triggerLabel(row))
                .techSignals(techSignals)
                .techHitCount(hit)
                .techTotal(techSignals.size())
                .techSummary(techSummary)
                .strategyId(strategyId)
                .strategyName(strategyName)
                .setupStyle(setupStyle)
                .pickReasons(pickReasons)
                .riskFlags(riskFlags)
                .pct2d(pct2d)
                .pct5d(pct5d)
                .valuationLevel(Objects.nonNull(valuation) ? valuation.getLevel() : null)
                .valuationLabel(Objects.nonNull(valuation) ? valuation.getLevelLabel() : null)
                .valuationScore(Objects.nonNull(valuation) ? valuation.getScore() : null)
                .valuationSummary(Objects.nonNull(valuation) ? valuation.getSummary() : null)
                .build();
    }

    private String extractStrategyId(ObservePool row) {
        String tags = nullToEmpty(row.getTags());
        for (String sid : List.of("S1", "S2", "S3", "MOOD", "HOT", "RISK")) {
            if (tags.contains(sid) || nullToEmpty(row.getReason()).contains(sid)) {
                return sid;
            }
        }
        if ("MOOD".equalsIgnoreCase(row.getSide())) {
            return "MOOD";
        }
        return "S1";
    }

    private String setupStyleOf(String side, String strategyId, String reason) {
        if ("MOOD".equals(side)) {
            return "情绪温度计";
        }
        if ("SELL".equals(side)) {
            return "卖出观察";
        }
        String blob = nullToEmpty(reason);
        if (blob.contains("勿追") || blob.contains("回踩") || blob.contains("回调")) {
            return "等回调再评估";
        }
        if ("S2".equalsIgnoreCase(strategyId)) {
            return "回调买入";
        }
        if ("S3".equalsIgnoreCase(strategyId)) {
            return "突破观察";
        }
        if ("S1".equalsIgnoreCase(strategyId)) {
            return "趋势观察";
        }
        return "信号观察";
    }

    private List<String> buildPickReasons(ObservePool row, String side, String strategyName, String setupStyle,
                                          BigDecimal pctChg, BigDecimal pct2d, BigDecimal pct5d, String techSummary,
                                          ValuationBriefResp valuation) {
        List<String> reasons = new ArrayList<>();
        reasons.add("策略：" + strategyName + " · " + setupStyle);
        if (Objects.nonNull(valuation) && StringUtils.isNotBlank(valuation.getLevelLabel())
                && !"UNKNOWN".equals(valuation.getLevel())) {
            String valLine = "估值：" + valuation.getLevelLabel();
            if (Objects.nonNull(valuation.getScore())) {
                valLine = valLine + " · 性价比分 " + valuation.getScore();
            }
            if (Objects.nonNull(valuation.getPeg())) {
                valLine = valLine + " · PEG " + valuation.getPeg();
            }
            reasons.add(valLine);
        }
        String raw = nullToEmpty(row.getReason())
                .replace("决策关注 · 可执行 · ", "")
                .replace("决策观察 · ", "")
                .replace("情绪观察 · ", "")
                .replace("决策买入 · ", "")
                .replace("决策卖出 · ", "");
        // 拆开「·」里偏规则的句子
        for (String part : raw.split("·|；|;")) {
            String p = part == null ? "" : part.trim();
            if (p.isEmpty() || p.length() < 2) {
                continue;
            }
            if (p.startsWith("S1") || p.startsWith("S2") || p.startsWith("S3") || p.startsWith("情绪")) {
                // 规则正文
                if (p.contains("：") || p.contains(":")) {
                    String after = p.replaceFirst("^[^：:]+[：:]", "").trim();
                    if (StringUtils.isNotBlank(after)) {
                        reasons.add("信号：" + after);
                    } else {
                        reasons.add("信号：" + p);
                    }
                } else if (!p.equals(strategyName) && !p.contains("均线趋势") && !p.contains("突破放量")
                        && !p.contains("RSI") && !p.contains("情绪风向")) {
                    reasons.add(p);
                } else if (p.contains("上穿") || p.contains("新高") || p.contains("超卖") || p.contains("共振")
                        || p.contains("热榜") || p.contains("连板") || p.contains("放量")) {
                    reasons.add("信号：" + p);
                }
                continue;
            }
            if (p.contains("勿追") || p.contains("回踩") || p.contains("基本面") || p.contains("主线")
                    || p.contains("市场") || p.contains("降权") || p.contains("热点") || p.contains("温度")) {
                reasons.add(p);
            } else if (p.contains("买入") || p.contains("观察") || p.contains("加仓")) {
                reasons.add(p);
            }
        }
        if (StringUtils.isNotBlank(row.getTriggerExpr())) {
            reasons.add("触发条件：" + trim(row.getTriggerExpr(), 48));
        }
        if (StringUtils.isNotBlank(techSummary)) {
            reasons.add(techSummary);
        }
        if (Objects.nonNull(pct2d) || Objects.nonNull(pct5d) || Objects.nonNull(pctChg)) {
            reasons.add("近况：今日 " + fmtPct(pctChg) + " · 2日 " + fmtPct(pct2d) + " · 5日 " + fmtPct(pct5d));
        }
        if (StringUtils.isNotBlank(row.getNote()) && row.getNote().contains("·")) {
            // note 可能含评分拆解
            String note = trim(row.getNote(), 80);
            if (!note.startsWith("来源")) {
                reasons.add("评分：" + note);
            } else if (note.contains(" · ")) {
                String rest = note.substring(note.indexOf(" · ") + 3).trim();
                if (StringUtils.isNotBlank(rest)) {
                    reasons.add("备注：" + rest);
                }
            }
        }
        // 去重保序
        List<String> uniq = new ArrayList<>();
        for (String r : reasons) {
            if (StringUtils.isBlank(r)) {
                continue;
            }
            boolean dup = false;
            for (String u : uniq) {
                if (u.equals(r) || u.contains(r) || r.contains(u)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                uniq.add(r);
            }
            if (uniq.size() >= 6) {
                break;
            }
        }
        if (uniq.isEmpty()) {
            uniq.add(StringUtils.isNotBlank(raw) ? raw : "暂无结构化理由，请看完整指导");
        }
        return uniq;
    }

    private List<String> buildRiskFlags(String side, BigDecimal pctChg, BigDecimal pct2d, BigDecimal pct5d,
                                        String reason, String status) {
        List<String> flags = new ArrayList<>();
        if ("MOOD".equals(side)) {
            flags.add("情绪标的·非买入");
        }
        if (nullToEmpty(reason).contains("勿追") || nullToEmpty(reason).contains("追高")) {
            flags.add("勿追高·等回踩");
        }
        if (Objects.nonNull(pctChg) && pctChg.compareTo(new BigDecimal("5")) >= 0) {
            flags.add("今日大涨·买点偏差");
        }
        if (Objects.nonNull(pct2d) && pct2d.compareTo(new BigDecimal("10")) >= 0) {
            flags.add("近2日涨幅大");
        }
        if (Objects.nonNull(pct5d) && pct5d.compareTo(new BigDecimal("15")) >= 0) {
            flags.add("近5日涨幅大");
        }
        if ("TRIGGERED".equals(status) && flags.stream().anyMatch(f -> f.contains("涨"))) {
            flags.add("已触发仍建议分批/确认");
        }
        return flags;
    }

    private BigDecimal recentPct(List<BarDaily> bars, int days) {
        if (CollUtil.isEmpty(bars) || bars.size() <= days) {
            return null;
        }
        // bars 按日期升序
        BarDaily last = bars.get(bars.size() - 1);
        BarDaily base = bars.get(bars.size() - 1 - days);
        if (Objects.isNull(last) || Objects.isNull(base)
                || Objects.isNull(last.getClosePrice()) || Objects.isNull(base.getClosePrice())
                || base.getClosePrice().signum() <= 0) {
            return null;
        }
        return last.getClosePrice().subtract(base.getClosePrice())
                .multiply(HUNDRED)
                .divide(base.getClosePrice(), 2, RoundingMode.HALF_UP);
    }

    private Map<String, List<BarDaily>> loadBarsGrouped(List<ObservePool> rows) {
        Map<String, List<BarDaily>> map = new HashMap<>();
        if (CollUtil.isEmpty(rows)) {
            return map;
        }
        List<String> codes = rows.stream().map(ObservePool::getCode).filter(StringUtils::isNotBlank).distinct().toList();
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
     * 重点技术指标监控（参考常见量化雷达：均线/MACD/量能/RSI/突破）
     */
    private List<ObserveTechSignal> evaluateTech(String side, List<BarDaily> bars) {
        List<ObserveTechSignal> list = new ArrayList<>();
        if (CollUtil.isEmpty(bars) || bars.size() < 35) {
            return list;
        }
        BarSeries series = BarSeries.from(bars);
        int i = series.size() - 1;
        List<BigDecimal> closes = series.getCloses();
        List<BigDecimal> highs = series.getHighs();
        List<BigDecimal> lows = series.getLows();
        List<BigDecimal> volumes = series.getVolumes();
        BigDecimal close = closes.get(i);
        if (Objects.isNull(close)) {
            return list;
        }
        boolean sell = "SELL".equals(side);

        BigDecimal ma5 = IndicatorUtils.ma(closes, 5, i);
        BigDecimal ma10 = IndicatorUtils.ma(closes, 10, i);
        BigDecimal ma20 = IndicatorUtils.ma(closes, 20, i);
        BigDecimal volMa5 = IndicatorUtils.ma(volumes, 5, i);
        BigDecimal rsi = IndicatorUtils.rsi(closes, 14, i);
        BigDecimal high20 = IndicatorUtils.highest(highs, 20, i);
        BigDecimal low20 = null;
        if (i >= 19) {
            low20 = lows.get(i - 19);
            for (int k = i - 18; k <= i; k++) {
                BigDecimal v = lows.get(k);
                if (Objects.nonNull(v) && (Objects.isNull(low20) || v.compareTo(low20) < 0)) {
                    low20 = v;
                }
            }
        }

        List<BigDecimal> difSeries = new ArrayList<>(closes.size());
        for (int k = 0; k < closes.size(); k++) {
            difSeries.add(IndicatorUtils.macdDif(closes, k));
        }
        List<BigDecimal> deaSeries = buildDeaSeries(difSeries);
        BigDecimal dif = difSeries.get(i);
        BigDecimal dea = deaSeries.get(i);
        BigDecimal prevDif = i > 0 ? difSeries.get(i - 1) : null;
        BigDecimal prevDea = i > 0 ? deaSeries.get(i - 1) : null;
        BigDecimal hist = IndicatorUtils.macdHist(dif, dea);
        BigDecimal prevHist = IndicatorUtils.macdHist(prevDif, prevDea);
        boolean macdGold = IndicatorUtils.crossedUp(prevDif, prevDea, dif, dea);
        boolean macdDead = IndicatorUtils.crossedDown(prevDif, prevDea, dif, dea);
        boolean macdZone = Objects.nonNull(dif) && Objects.nonNull(dea) && dif.compareTo(dea) > 0;

        // 1 站稳/跌破 5 日
        boolean aboveMa5 = Objects.nonNull(ma5) && close.compareTo(ma5) >= 0;
        list.add(signal("MA5", sell ? "跌破5日" : "站稳5日",
                sell ? !aboveMa5 : aboveMa5,
                Objects.nonNull(ma5) ? ("MA5 " + ma5.stripTrailingZeros().toPlainString()) : null));

        // 2 站稳/跌破 20 日
        boolean aboveMa20 = Objects.nonNull(ma20) && close.compareTo(ma20) >= 0;
        list.add(signal("MA20", sell ? "跌破20日" : "站稳20日",
                sell ? !aboveMa20 : aboveMa20,
                Objects.nonNull(ma20) ? ("MA20 " + ma20.stripTrailingZeros().toPlainString()) : null));

        // 3 均线多头/空头
        boolean maBull = Objects.nonNull(ma5) && Objects.nonNull(ma10) && Objects.nonNull(ma20)
                && ma5.compareTo(ma10) > 0 && ma10.compareTo(ma20) > 0;
        boolean maBear = Objects.nonNull(ma5) && Objects.nonNull(ma10) && Objects.nonNull(ma20)
                && ma5.compareTo(ma10) < 0 && ma10.compareTo(ma20) < 0;
        list.add(signal("MA_ALIGN", sell ? "空头排列" : "多头排列",
                sell ? maBear : maBull, null));

        // 4 MACD 金叉/死叉（当日交叉优先，否则看多空区）
        boolean macdHit = sell ? (macdDead || !macdZone) : (macdGold || macdZone);
        if (Objects.isNull(dif) || Objects.isNull(dea)) {
            macdHit = false;
        }
        String macdDetail = macdGold ? "今日金叉" : macdDead ? "今日死叉"
                : macdZone ? "DIF>DEA" : "DIF≤DEA";
        list.add(signal("MACD", sell ? "MACD死叉/空" : "MACD金叉/多", macdHit, macdDetail));

        // 5 柱状动能
        boolean histUp = Objects.nonNull(hist) && Objects.nonNull(prevHist)
                && hist.compareTo(ZERO) > 0 && hist.compareTo(prevHist) > 0;
        boolean histDown = Objects.nonNull(hist) && Objects.nonNull(prevHist)
                && hist.compareTo(ZERO) < 0 && hist.compareTo(prevHist) < 0;
        list.add(signal("MACD_HIST", sell ? "绿柱扩大" : "红柱放大",
                sell ? histDown : histUp,
                Objects.nonNull(hist) ? hist.stripTrailingZeros().toPlainString() : null));

        // 6 放量
        BigDecimal vol = volumes.get(i);
        boolean volSurge = Objects.nonNull(vol) && Objects.nonNull(volMa5) && volMa5.signum() > 0
                && vol.compareTo(volMa5.multiply(VOL_SURGE)) >= 0;
        String volDetail = null;
        if (Objects.nonNull(vol) && Objects.nonNull(volMa5) && volMa5.signum() > 0) {
            volDetail = "量比 "
                    + vol.divide(volMa5, 2, RoundingMode.HALF_UP).toPlainString();
        }
        list.add(signal("VOL", "放量确认", volSurge, volDetail));

        // 7 RSI
        boolean rsiBuyOk = Objects.nonNull(rsi) && rsi.compareTo(new BigDecimal("40")) >= 0
                && rsi.compareTo(new BigDecimal("72")) <= 0;
        boolean rsiSellOk = Objects.nonNull(rsi) && rsi.compareTo(new BigDecimal("55")) <= 0;
        list.add(signal("RSI", sell ? "RSI转弱" : "RSI健康",
                sell ? rsiSellOk : rsiBuyOk,
                Objects.nonNull(rsi) ? ("RSI " + rsi.setScale(1, RoundingMode.HALF_UP)) : null));

        // 8 突破/破位
        boolean breakHigh = Objects.nonNull(high20) && close.compareTo(high20) >= 0;
        boolean breakLow = Objects.nonNull(low20) && close.compareTo(low20) <= 0;
        list.add(signal("BREAK", sell ? "破20日低" : "近20日高",
                sell ? breakLow : breakHigh, null));

        return list;
    }

    private ObserveTechSignal signal(String key, String label, boolean hit, String detail) {
        return ObserveTechSignal.builder()
                .key(key)
                .label(label)
                .hit(hit)
                .detail(detail)
                .build();
    }

    private List<BigDecimal> buildDeaSeries(List<BigDecimal> difSeries) {
        List<BigDecimal> deaSeries = new ArrayList<>(difSeries.size());
        for (int k = 0; k < difSeries.size(); k++) {
            deaSeries.add(IndicatorUtils.macdDea(difSeries, k));
        }
        return deaSeries;
    }

    private BigDecimal effectiveTriggerPrice(ObservePool row, BigDecimal latest) {
        String type = StringUtils.isNotBlank(row.getTriggerType()) ? row.getTriggerType() : "PRICE_ABOVE";
        if ("PCT_FROM_BASE".equals(type) && Objects.nonNull(row.getBasePrice()) && Objects.nonNull(row.getTriggerPrice())) {
            // triggerPrice 存涨跌幅百分点，如 -3 表示基准下 3%
            return row.getBasePrice().multiply(
                    BigDecimal.ONE.add(row.getTriggerPrice().divide(HUNDRED, 6, RoundingMode.HALF_UP)));
        }
        return row.getTriggerPrice();
    }

    private String evaluateStatus(ObservePool row, BigDecimal latest) {
        String current = StringUtils.isNotBlank(row.getStatus()) ? row.getStatus() : "WATCHING";
        if (TERMINAL.contains(current)) {
            return current;
        }
        if (Objects.isNull(latest) || latest.signum() <= 0) {
            return current;
        }
        String side = resolveSide(row.getSide(), row.getTags(), row.getReason());
        boolean sell = "SELL".equals(side);

        if (sell) {
            // 卖出剧本：止损价高于触发价 = 反弹作废线；目标价低于现价 = 继续看低的减仓目标
            boolean stopAbove = Objects.nonNull(row.getStopLoss()) && Objects.nonNull(row.getTriggerPrice())
                    && row.getStopLoss().compareTo(row.getTriggerPrice()) > 0;
            if (stopAbove && latest.compareTo(row.getStopLoss()) >= 0) {
                return "STOPPED";
            }
            if (Objects.nonNull(row.getTargetPrice()) && latest.compareTo(row.getTargetPrice()) <= 0) {
                return "HIT_TARGET";
            }
            if ("MANUAL".equals(row.getTriggerType())) {
                return current;
            }
            BigDecimal triggerPx = effectiveTriggerPrice(row, latest);
            String type = StringUtils.isNotBlank(row.getTriggerType()) ? row.getTriggerType() : "PRICE_BELOW";
            boolean hit = "TRIGGERED".equals(current);
            boolean near = false;
            if (Objects.nonNull(triggerPx) && triggerPx.signum() > 0) {
                boolean below = "PRICE_BELOW".equals(type)
                        || ("PCT_FROM_BASE".equals(type)
                        && Objects.nonNull(row.getTriggerPrice())
                        && row.getTriggerPrice().signum() < 0);
                if (below) {
                    hit = hit || latest.compareTo(triggerPx) <= 0;
                    near = !hit && pctAbs(latest, triggerPx).compareTo(NEAR_PCT) <= 0;
                } else {
                    hit = hit || latest.compareTo(triggerPx) >= 0;
                    near = !hit && pctAbs(latest, triggerPx).compareTo(NEAR_PCT) <= 0;
                }
            }
            if (hit) {
                return "TRIGGERED";
            }
            if (near) {
                return "NEAR";
            }
            return "WATCHING";
        }

        if (Objects.nonNull(row.getStopLoss()) && latest.compareTo(row.getStopLoss()) <= 0) {
            return "STOPPED";
        }
        if (Objects.nonNull(row.getTargetPrice()) && latest.compareTo(row.getTargetPrice()) >= 0
                && ("TRIGGERED".equals(current) || "NEAR".equals(current) || "HIT_TARGET".equals(current))) {
            return "HIT_TARGET";
        }
        // 情绪风向 / 手工确认：不自动判买卖触发，仅保留止损·修复参考
        if ("MANUAL".equals(row.getTriggerType()) || "MOOD".equals(resolveSide(row.getSide(), row.getTags(), row.getReason()))) {
            if (Objects.nonNull(row.getTargetPrice()) && latest.compareTo(row.getTargetPrice()) >= 0) {
                return "HIT_TARGET";
            }
            return "WATCHING".equals(current) || "NEAR".equals(current) || "TRIGGERED".equals(current)
                    ? "WATCHING" : current;
        }

        BigDecimal triggerPx = effectiveTriggerPrice(row, latest);
        String type = StringUtils.isNotBlank(row.getTriggerType()) ? row.getTriggerType() : "PRICE_ABOVE";
        boolean hit = false;
        boolean near = false;
        if (Objects.nonNull(triggerPx) && triggerPx.signum() > 0) {
            boolean below = "PRICE_BELOW".equals(type)
                    || ("PCT_FROM_BASE".equals(type)
                    && Objects.nonNull(row.getTriggerPrice())
                    && row.getTriggerPrice().signum() < 0);
            if (below) {
                hit = latest.compareTo(triggerPx) <= 0;
                near = !hit && pctAbs(latest, triggerPx).compareTo(NEAR_PCT) <= 0;
            } else {
                hit = latest.compareTo(triggerPx) >= 0;
                near = !hit && pctAbs(latest, triggerPx).compareTo(NEAR_PCT) <= 0;
            }
        }
        if (hit || "TRIGGERED".equals(current)) {
            if (Objects.nonNull(row.getTargetPrice()) && latest.compareTo(row.getTargetPrice()) >= 0) {
                return "HIT_TARGET";
            }
            return "TRIGGERED";
        }
        if (near) {
            return "NEAR";
        }
        return "WATCHING";
    }

    private String buildHint(ObservePool row, BigDecimal latest, BigDecimal pctToTrigger, String status) {
        if (Objects.isNull(latest)) {
            return "暂无最新价，请先同步个股行情";
        }
        String side = resolveSide(row.getSide(), row.getTags(), row.getReason());
        boolean sell = "SELL".equals(side);
        boolean mood = "MOOD".equals(side);
        if (mood) {
            return switch (status) {
                case "STOPPED" -> "跌破情绪退潮参考 " + money(row.getStopLoss()) + "，热度可能失效，考虑移出";
                case "HIT_TARGET" -> "触及情绪修复参考 " + money(row.getTargetPrice()) + "，观察是否转强（仍非默认买入）";
                case "ARCHIVED" -> "已归档";
                default -> "情绪风向标：盯热度/跟风，不默认买入；退潮参考 "
                        + money(row.getStopLoss()) + "，修复参考 " + money(row.getTargetPrice());
            };
        }
        // sell already resolved
        return switch (status) {
            case "NEAR" -> sell
                    ? "接近卖出触发（约 " + fmtPct(pctToTrigger) + "），准备按指导减仓"
                    : "接近买入触发（约 " + fmtPct(pctToTrigger) + "），准备按指导分批买入";
            case "TRIGGERED" -> sell
                    ? "卖出已触发，建议按指导减仓/清仓；反弹至 " + money(row.getStopLoss()) + " 需重新评估"
                    : "买入已触发，按指导分批建仓，严格止损 " + money(row.getStopLoss());
            case "HIT_TARGET" -> sell
                    ? "已到卖出目标价 " + money(row.getTargetPrice()) + "，建议完成减仓/清仓"
                    : "已到买入目标价 " + money(row.getTargetPrice()) + "，可分批兑现利润";
            case "STOPPED" -> sell
                    ? "价格反弹触及作废线 " + money(row.getStopLoss()) + "，卖出计划需重新评估"
                    : "触及止损 " + money(row.getStopLoss()) + "，停止加仓并复盘原因";
            case "ARCHIVED" -> "已归档";
            default -> {
                if ("MANUAL".equals(row.getTriggerType())) {
                    yield "手动确认型：事件落地并满足条件后再动手";
                }
                yield Objects.nonNull(pctToTrigger)
                        ? (sell ? "观察中，距卖出触发约 " : "观察中，距买入触发约 ") + fmtPct(pctToTrigger)
                        : "观察中，请完善触发价与条件";
            }
        };
    }

    private String triggerLabel(ObservePool row) {
        String type = StringUtils.isNotBlank(row.getTriggerType()) ? row.getTriggerType() : "PRICE_ABOVE";
        String pricePart = Objects.nonNull(row.getTriggerPrice()) ? row.getTriggerPrice().stripTrailingZeros().toPlainString() : "--";
        String base = switch (type) {
            case "PRICE_BELOW" -> "跌破/触及 " + pricePart;
            case "PCT_FROM_BASE" -> "相对基准 " + pricePart + "%";
            case "BREAK_HIGH" -> "突破前高 " + pricePart;
            case "MANUAL" -> "手工确认";
            default -> "站上 " + pricePart;
        };
        if (StringUtils.isNotBlank(row.getTriggerExpr())) {
            return base + " · " + row.getTriggerExpr();
        }
        return base;
    }

    private BigDecimal pctDistance(BigDecimal latest, BigDecimal target) {
        if (Objects.isNull(latest) || Objects.isNull(target) || target.signum() == 0) {
            return null;
        }
        return target.subtract(latest)
                .multiply(HUNDRED)
                .divide(latest, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal pctAbs(BigDecimal a, BigDecimal b) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.signum() == 0) {
            return HUNDRED;
        }
        return a.subtract(b).abs().multiply(HUNDRED).divide(a, 2, RoundingMode.HALF_UP);
    }

    private String fmtPct(BigDecimal v) {
        if (Objects.isNull(v)) {
            return "--";
        }
        String sign = v.compareTo(ZERO) > 0 ? "+" : "";
        return sign + v.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String money(BigDecimal v) {
        if (Objects.isNull(v)) {
            return "--";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
