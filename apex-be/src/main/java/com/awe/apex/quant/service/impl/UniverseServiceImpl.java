package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        List<Watchlist> candidates = resolveCandidates(req);
        if (CollUtil.isEmpty(candidates)) {
            throw new BusinessException("无候选股票，请先导入自选或传入 codes");
        }

        Map<String, StockBasic> basicMap = new HashMap<>();
        List<String> codes = new ArrayList<>();
        for (Watchlist item : candidates) {
            codes.add(MarketCodeUtils.normalizeCode(item.getCode()));
        }
        if (CollUtil.isNotEmpty(codes)) {
            List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .in(StockBasic::getCode, codes));
            for (StockBasic basic : basics) {
                basicMap.put(basic.getCode(), basic);
            }
        }

        Map<String, Long> barCountMap = new HashMap<>();
        List<Map<String, Object>> stats = barDailyMapper.selectMaps(Wrappers.<BarDaily>query()
                .select("code", "COUNT(1) AS cnt")
                .in("code", codes)
                .groupBy("code"));
        for (Map<String, Object> row : stats) {
            barCountMap.put(String.valueOf(row.get("code")), Long.parseLong(String.valueOf(row.get("cnt"))));
        }

        List<Scored> scoredList = new ArrayList<>();
        for (Watchlist item : candidates) {
            String code = MarketCodeUtils.normalizeCode(item.getCode());
            String name = item.getName();
            StockBasic basic = basicMap.get(code);
            if (Objects.nonNull(basic) && StringUtils.isBlank(name)) {
                name = basic.getName();
            }
            if (isSt(name, basic)) {
                continue;
            }
            long barCount = barCountMap.getOrDefault(code, 0L);
            if (barCount < MIN_BARS) {
                continue;
            }
            List<String> tags = new ArrayList<>();
            tags.add("BARS_OK");
            BigDecimal score = new BigDecimal("60");

            BigDecimal pe = Objects.nonNull(basic) ? basic.getPeTtm() : null;
            BigDecimal pb = Objects.nonNull(basic) ? basic.getPb() : null;
            BigDecimal circMv = Objects.nonNull(basic) ? basic.getCircMv() : null;

            if (Objects.nonNull(pe)) {
                if (pe.signum() <= 0 || pe.compareTo(MAX_PE) > 0) {
                    continue;
                }
                tags.add("PE_OK");
                // PE 越低越好，20 附近给满分加成
                BigDecimal peBonus = new BigDecimal("20").subtract(pe.abs().divide(new BigDecimal("4"), 4, RoundingMode.HALF_UP));
                score = score.add(peBonus.max(BigDecimal.ZERO).min(new BigDecimal("20")));
            } else {
                tags.add("PE_MISS");
            }

            if (Objects.nonNull(pb)) {
                if (pb.signum() <= 0 || pb.compareTo(MAX_PB) > 0) {
                    continue;
                }
                tags.add("PB_OK");
                score = score.add(new BigDecimal("10").subtract(pb.min(new BigDecimal("10"))));
            } else {
                tags.add("PB_MISS");
            }

            if (Objects.nonNull(circMv)) {
                if (circMv.compareTo(MIN_CIRC_MV) < 0) {
                    continue;
                }
                tags.add("MV_OK");
                score = score.add(new BigDecimal("8"));
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

        String batchNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Scored scored : scoredList) {
            universeSnapshotMapper.insert(UniverseSnapshot.builder()
                    .batchNo(batchNo)
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
                .orderByDesc(UniverseSnapshot::getId)
                .last("limit 1"));
        if (Objects.isNull(latest)) {
            return List.of();
        }
        return universeSnapshotMapper.selectList(Wrappers.<UniverseSnapshot>lambdaQuery()
                .eq(UniverseSnapshot::getBatchNo, latest.getBatchNo())
                .orderByAsc(UniverseSnapshot::getCode));
    }

    private boolean isSt(String name, StockBasic basic) {
        if (Objects.nonNull(basic) && Objects.nonNull(basic.getStFlag()) && basic.getStFlag() == 1) {
            return true;
        }
        return StringUtils.isNotBlank(name) && name.toUpperCase().contains("ST");
    }

    private List<Watchlist> resolveCandidates(UniverseRefreshReq req) {
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
        String groupName = Objects.nonNull(req) ? req.getGroupName() : null;
        return watchlistMapper.selectList(Wrappers.<Watchlist>lambdaQuery()
                .eq(StringUtils.isNotBlank(groupName), Watchlist::getGroupName, groupName));
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
