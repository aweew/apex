package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.UniverseSnapshotMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IUniverseService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 股票池服务实现（质量过滤 + 评分排序）
 */
@Service
public class UniverseServiceImpl implements IUniverseService {

    @Resource
    private ApexUserContext userContext;

    private static final int MIN_BARS = 60;
    private static final BigDecimal MAX_PE = new BigDecimal("80");
    private static final BigDecimal MAX_PB = new BigDecimal("20");
    private static final BigDecimal MIN_CIRC_MV = new BigDecimal("3000000000");

    @Resource
    private WatchlistMapper watchlistMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private UniverseSnapshotMapper universeSnapshotMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    /**
     * 刷新股票池
     *
     * @param req 请求
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UniverseRefreshResp refresh(UniverseRefreshReq req) {
        Long currentUserId = currentUserId();
        LocalDate asOfDate = Objects.nonNull(req) ? req.getAsOfDate() : null;
        LocalDate currentDate = LocalDate.now();
        if (Objects.nonNull(asOfDate) && asOfDate.isAfter(currentDate)) {
            throw new BusinessException("股票池截止日期不能晚于今天");
        }
        if (Objects.nonNull(asOfDate) && asOfDate.isBefore(currentDate)) {
            String scope = req.getScope();
            if (!"MARKET".equalsIgnoreCase(scope) || CollUtil.isNotEmpty(req.getCodes())) {
                throw new BusinessException("历史股票池只能基于截止日行情生成，请将 scope 设为 MARKET");
            }
        }
        LocalDate snapshotDate = Objects.nonNull(asOfDate) ? asOfDate : currentDate;
        List<Watchlist> candidates = resolveCandidates(req, currentUserId);
        if (CollUtil.isEmpty(candidates)) {
            throw new BusinessException("无候选股票，请先导入自选或传入 codes");
        }

        Map<String, StockBasic> basicMap = new HashMap<>();
        List<String> codes = new ArrayList<>();
        for (Watchlist item : candidates) {
            codes.add(MarketCodeUtils.normalizeCode(item.getCode()));
        }
        if (CollUtil.isNotEmpty(codes)) {
            int batch = 500;
            for (int i = 0; i < codes.size(); i += batch) {
                List<String> part = codes.subList(i, Math.min(i + batch, codes.size()));
                List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                        .in(StockBasic::getCode, part));
                if (CollUtil.isEmpty(basics)) {
                    continue;
                }
                for (StockBasic basic : basics) {
                    basicMap.put(basic.getCode(), basic);
                }
            }
        }

        Map<String, Long> barCountMap = new HashMap<>();
        if (CollUtil.isNotEmpty(codes)) {
            int batch = 500;
            for (int i = 0; i < codes.size(); i += batch) {
                List<String> part = codes.subList(i, Math.min(i + batch, codes.size()));
                List<Map<String, Object>> stats = barDailyMapper.selectMaps(Wrappers.<BarDaily>query()
                        .select("code", "COUNT(1) AS cnt")
                        .in("code", part)
                        .le(Objects.nonNull(asOfDate), "trade_date", asOfDate)
                        .groupBy("code"));
                if (CollUtil.isEmpty(stats)) {
                    continue;
                }
                for (Map<String, Object> row : stats) {
                    barCountMap.put(String.valueOf(row.get("code")), Long.parseLong(String.valueOf(row.get("cnt"))));
                }
            }
        }

        List<Scored> scoredList = new ArrayList<>();
        boolean asOfSnapshot = Objects.nonNull(asOfDate);
        for (Watchlist item : candidates) {
            String code = MarketCodeUtils.normalizeCode(item.getCode());
            String name = item.getName();
            StockBasic basic = basicMap.get(code);
            if (Objects.nonNull(basic) && StringUtils.isBlank(name)) {
                name = basic.getName();
            }
            if (!asOfSnapshot && isSt(name, basic)) {
                continue;
            }
            // 决策等场景可显式 includeBj=false 剔除北交所；未传则不过滤（避免影响信号页股票池）
            if (Objects.nonNull(req) && Boolean.FALSE.equals(req.getIncludeBj()) && MarketCodeUtils.isBj(code)) {
                continue;
            }
            long barCount = barCountMap.getOrDefault(code, 0L);
            if (barCount < MIN_BARS) {
                continue;
            }
            boolean loose = Objects.nonNull(req) && Boolean.TRUE.equals(req.getLooseFilter());
            List<String> tags = new ArrayList<>();
            tags.add("BARS_OK");
            if (asOfSnapshot) {
                tags.add("RECONSTRUCTED_AS_OF");
                scoredList.add(new Scored(code, name, String.join(",", tags), new BigDecimal("60")));
                continue;
            }
            if (loose) {
                tags.add("LOOSE");
            }
            BigDecimal score = new BigDecimal("60");

            BigDecimal pe = Objects.nonNull(basic) ? basic.getPeTtm() : null;
            BigDecimal pb = Objects.nonNull(basic) ? basic.getPb() : null;
            BigDecimal circMv = Objects.nonNull(basic) ? basic.getCircMv() : null;

            if (Objects.nonNull(pe)) {
                if (pe.signum() <= 0 || pe.compareTo(MAX_PE) > 0) {
                    if (!loose) {
                        continue;
                    }
                    tags.add("PE_SOFT");
                } else {
                    tags.add("PE_OK");
                    // PE 越低越好，20 附近给满分加成
                    BigDecimal peBonus = new BigDecimal("20").subtract(pe.abs().divide(new BigDecimal("4"), 4, RoundingMode.HALF_UP));
                    score = score.add(peBonus.max(BigDecimal.ZERO).min(new BigDecimal("20")));
                }
            } else {
                tags.add("PE_MISS");
            }

            if (Objects.nonNull(pb)) {
                if (pb.signum() <= 0 || pb.compareTo(MAX_PB) > 0) {
                    if (!loose) {
                        continue;
                    }
                    tags.add("PB_SOFT");
                } else {
                    tags.add("PB_OK");
                    score = score.add(new BigDecimal("10").subtract(pb.min(new BigDecimal("10"))));
                }
            } else {
                tags.add("PB_MISS");
            }

            if (Objects.nonNull(circMv)) {
                if (circMv.compareTo(MIN_CIRC_MV) < 0) {
                    if (!loose) {
                        continue;
                    }
                    tags.add("MV_SOFT");
                } else {
                    tags.add("MV_OK");
                    score = score.add(new BigDecimal("8"));
                }
            } else {
                tags.add("MV_MISS");
            }

            if (Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getIndustry())) {
                tags.add("IND_" + basic.getIndustry());
                score = score.add(new BigDecimal("5"));
            }

            score = score.max(BigDecimal.ZERO).min(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            tags.add("SCORE_" + score.toPlainString());
            scoredList.add(new Scored(code, name, String.join(",", tags), score));
        }

        scoredList.sort(Comparator.comparing((Scored s) -> s.score).reversed());

        String batchNo = IdUtil.fastSimpleUUID();
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Scored scored : scoredList) {
            universeSnapshotMapper.insert(UniverseSnapshot.builder()
                    .userId(currentUserId)
                    .batchNo(batchNo)
                    .asOfDate(snapshotDate)
                    .code(scored.code)
                    .name(scored.name)
                    .reasonTags(scored.tags)
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build());
            count++;
        }
        return UniverseRefreshResp.builder().batchNo(batchNo).count(count).build();
    }

    /**
     * 最新批次列表
     *
     * @return 列表
     */
    @Override
    public List<UniverseSnapshot> latest() {
        UniverseSnapshot latest = universeSnapshotMapper.selectOne(Wrappers.<UniverseSnapshot>lambdaQuery()
                .eq(UniverseSnapshot::getUserId, currentUserId())
                .orderByDesc(UniverseSnapshot::getAsOfDate)
                .orderByDesc(UniverseSnapshot::getId)
                .last("limit 1"));
        if (Objects.isNull(latest)) {
            return List.of();
        }
        return listByBatchNo(latest.getBatchNo());
    }

    /**
     * 查询指定股票池批次
     *
     * @param batchNo 批次号
     * @return 列表
     */
    @Override
    public List<UniverseSnapshot> listByBatchNo(String batchNo) {
        if (StringUtils.isBlank(batchNo)) {
            throw new BusinessException("股票池批次号不能为空");
        }
        return universeSnapshotMapper.selectList(Wrappers.<UniverseSnapshot>lambdaQuery()
                .eq(UniverseSnapshot::getUserId, currentUserId())
                .eq(UniverseSnapshot::getBatchNo, batchNo)
                .orderByAsc(UniverseSnapshot::getCode));
    }

    /**
     * 查询截止日期当时可用的最新批次
     *
     * @param asOfDate 截止日期
     * @return 列表
     */
    @Override
    public List<UniverseSnapshot> latestAsOf(LocalDate asOfDate) {
        if (Objects.isNull(asOfDate)) {
            throw new BusinessException("股票池截止日期不能为空");
        }
        UniverseSnapshot latest = universeSnapshotMapper.selectOne(Wrappers.<UniverseSnapshot>lambdaQuery()
                .eq(UniverseSnapshot::getUserId, currentUserId())
                .le(UniverseSnapshot::getAsOfDate, asOfDate)
                .orderByDesc(UniverseSnapshot::getAsOfDate)
                .orderByDesc(UniverseSnapshot::getId)
                .last("limit 1"));
        if (Objects.isNull(latest)) {
            return List.of();
        }
        return listByBatchNo(latest.getBatchNo());
    }

    private boolean isSt(String name, StockBasic basic) {
        if (Objects.nonNull(basic) && Objects.nonNull(basic.getStFlag()) && basic.getStFlag() == 1) {
            return true;
        }
        return StringUtils.isNotBlank(name) && name.toUpperCase().contains("ST");
    }

    private List<Watchlist> resolveCandidates(UniverseRefreshReq req, Long currentUserId) {
        if (Objects.nonNull(req) && CollUtil.isNotEmpty(req.getCodes())) {
            List<Watchlist> list = new ArrayList<>();
            for (String code : req.getCodes()) {
                list.add(Watchlist.builder()
                        .code(MarketCodeUtils.normalizeCode(code))
                        .name(null)
                        .build());
            }
            return list;
        }
        String scope = Objects.nonNull(req) ? req.getScope() : null;
        if (StringUtils.isNotBlank(scope) && "MARKET".equalsIgnoreCase(scope.trim())) {
            return loadMarketCandidatesFromBars(req.getAsOfDate());
        }
        String groupName = Objects.nonNull(req) ? req.getGroupName() : null;
        return watchlistMapper.selectList(Wrappers.<Watchlist>lambdaQuery()
                .eq(Watchlist::getUserId, currentUserId)
                .eq(StringUtils.isNotBlank(groupName), Watchlist::getGroupName, groupName));
    }

    private Long currentUserId() {
        return userContext.currentUserId();
    }

    /**
     * 全市场候选：本地已有足够日线的标的（不依赖现价是否已刷）
     */
    private List<Watchlist> loadMarketCandidatesFromBars(LocalDate asOfDate) {
        List<Map<String, Object>> stats = barDailyMapper.selectMaps(Wrappers.<BarDaily>query()
                .select("code", "COUNT(1) AS cnt")
                .le(Objects.nonNull(asOfDate), "trade_date", asOfDate)
                .groupBy("code")
                .having("COUNT(1) >= {0}", MIN_BARS));
        if (CollUtil.isEmpty(stats)) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (Map<String, Object> row : stats) {
            if (Objects.isNull(row) || Objects.isNull(row.get("code"))) {
                continue;
            }
            codes.add(MarketCodeUtils.normalizeCode(String.valueOf(row.get("code"))));
        }
        Map<String, String> nameMap = new HashMap<>();
        if (CollUtil.isNotEmpty(codes)) {
            // 分批查名称，避免 IN 过长
            int batch = 500;
            for (int i = 0; i < codes.size(); i += batch) {
                List<String> part = codes.subList(i, Math.min(i + batch, codes.size()));
                List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                        .in(StockBasic::getCode, part)
                        .select(StockBasic::getCode, StockBasic::getName, StockBasic::getStFlag));
                if (CollUtil.isEmpty(basics)) {
                    continue;
                }
                for (StockBasic basic : basics) {
                    nameMap.put(basic.getCode(), basic.getName());
                }
            }
        }
        List<Watchlist> list = new ArrayList<>();
        for (String code : codes) {
            list.add(Watchlist.builder()
                    .code(code)
                    .name(nameMap.get(code))
                    .build());
        }
        return list;
    }

    private static final class Scored {
        private final String code;
        private final String name;
        private final String tags;
        private final BigDecimal score;

        private Scored(String code, String name, String tags, BigDecimal score) {
            this.code = code;
            this.name = name;
            this.tags = tags;
            this.score = score;
        }
    }
}
