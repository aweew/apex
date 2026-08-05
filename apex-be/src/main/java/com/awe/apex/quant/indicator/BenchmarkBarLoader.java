package com.awe.apex.quant.indicator;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.IndexBar;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.IndexBarMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 沪深300 等基准日线加载（相对强弱用）
 */
@Component
public class BenchmarkBarLoader {

    public static final String HS300_CODE = "000300";
    public static final String HS300_INDEX = "CN_HS300";
    public static final String SH_INDEX = "CN_SH";

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private IndexBarMapper indexBarMapper;

    /**
     * 加载升序基准日线，优先 bar_daily.000300，其次 index_bar 沪深300/上证
     *
     * @param limit 条数上限
     * @return 升序日线；无数据返回空列表
     */
    public List<BarDaily> loadHs300Asc(int limit) {
        int size = Math.max(30, Math.min(limit, 400));
        List<BarDaily> fromStock = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, HS300_CODE)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit " + size));
        if (CollUtil.isNotEmpty(fromStock) && fromStock.size() >= 25) {
            fromStock.sort((a, b) -> a.getTradeDate().compareTo(b.getTradeDate()));
            return fromStock;
        }
        List<BarDaily> fromIndex = loadFromIndex(HS300_INDEX, size);
        if (CollUtil.isNotEmpty(fromIndex) && fromIndex.size() >= 25) {
            return fromIndex;
        }
        return loadFromIndex(SH_INDEX, size);
    }

    private List<BarDaily> loadFromIndex(String indexCode, int size) {
        List<IndexBar> rows = indexBarMapper.selectList(Wrappers.<IndexBar>lambdaQuery()
                .eq(IndexBar::getCode, indexCode)
                .orderByDesc(IndexBar::getTradeDate)
                .last("limit " + size));
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }
        List<BarDaily> list = new ArrayList<>(rows.size());
        for (IndexBar row : rows) {
            if (Objects.isNull(row) || Objects.isNull(row.getTradeDate()) || Objects.isNull(row.getClosePrice())) {
                continue;
            }
            list.add(BarDaily.builder()
                    .code(HS300_CODE)
                    .tradeDate(row.getTradeDate())
                    .openPrice(row.getOpenPrice())
                    .highPrice(row.getHighPrice())
                    .lowPrice(row.getLowPrice())
                    .closePrice(row.getClosePrice())
                    .volume(row.getVolume())
                    .amount(row.getAmount())
                    .pctChg(row.getPctChg())
                    .build());
        }
        list.sort((a, b) -> a.getTradeDate().compareTo(b.getTradeDate()));
        return list;
    }
}
