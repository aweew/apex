package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.MarketHeatmapNode;
import com.awe.apex.quant.domain.dto.MarketHeatmapResp;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SectorBoardResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.IMarketHeatmapService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 大盘云图：行业用 stock_basic 市值加权；概念/题材用板块行情
 */
@Service
public class MarketHeatmapServiceImpl implements IMarketHeatmapService {

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private ISectorBoardService sectorBoardService;

    /**
     * 构建云图色块
     *
     * @param boardType 维度
     * @param colorBy   着色
     * @param sizeBy    块大小
     * @param limit     上限
     * @param excludeSt 保留参数兼容
     * @return 云图
     */
    @Override
    public MarketHeatmapResp heatmap(String boardType, String colorBy, String sizeBy, Integer limit, Boolean excludeSt) {
        String type = StringUtils.isBlank(boardType) ? "INDUSTRY" : boardType.trim().toUpperCase();
        String color = normalizeColorBy(colorBy);
        String size = normalizeSizeBy(sizeBy, type);
        int cap = Objects.nonNull(limit) ? Math.max(5, Math.min(limit, 200)) : 80;

        if ("CONCEPT".equals(type) || "THEME".equals(type)) {
            return fromSectorBoard(type, color, size, cap);
        }
        return fromStockBasicIndustry(color, size, cap);
    }

    /**
     * 行业成分下钻
     *
     * @param industry 行业
     * @param limit    条数
     * @return 列表
     */
    @Override
    public List<WatchlistResp> industryStocks(String industry, Integer limit) {
        if (StringUtils.isBlank(industry)) {
            return List.of();
        }
        int cap = Objects.nonNull(limit) ? Math.max(1, Math.min(limit, 100)) : 40;
        List<StockBasic> list = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getIndustry, industry.trim())
                .and(w -> w.isNull(StockBasic::getStFlag).or().eq(StockBasic::getStFlag, 0))
                .notLike(StockBasic::getName, "ST")
                .orderByDesc(StockBasic::getCircMv)
                .last("LIMIT " + cap));
        List<WatchlistResp> rows = new ArrayList<>();
        if (CollUtil.isEmpty(list)) {
            return rows;
        }
        for (StockBasic basic : list) {
            rows.add(WatchlistResp.builder()
                    .code(basic.getCode())
                    .name(basic.getName())
                    .market(basic.getMarket())
                    .industry(basic.getIndustry())
                    .latestPrice(basic.getLatestPrice())
                    .pctChg(basic.getPctChg())
                    .peTtm(basic.getPeTtm())
                    .pb(basic.getPb())
                    .circMv(basic.getCircMv())
                    .totalMv(basic.getTotalMv())
                    .build());
        }
        return rows;
    }

    private MarketHeatmapResp fromStockBasicIndustry(String color, String size, int cap) {
        List<Map<String, Object>> rows = stockBasicMapper.aggregateIndustryHeatmap();
        List<MarketHeatmapNode> nodes = new ArrayList<>();
        if (CollUtil.isNotEmpty(rows)) {
            for (Map<String, Object> row : rows) {
                String name = str(row.get("name"));
                if (StringUtils.isBlank(name)) {
                    continue;
                }
                BigDecimal circMv = decimal(row.get("circMv"));
                BigDecimal pct = decimal(row.get("weightedPctChg"));
                BigDecimal avgPe = decimal(row.get("avgPe"));
                Integer stockCount = intVal(row.get("stockCount"));
                Integer upCount = intVal(row.get("upCount"));
                Integer downCount = intVal(row.get("downCount"));
                BigDecimal value = resolveSize(size, circMv, null, stockCount);
                BigDecimal colorValue = resolveColor(color, pct, avgPe, null);
                if (Objects.isNull(value) || value.signum() <= 0) {
                    continue;
                }
                nodes.add(MarketHeatmapNode.builder()
                        .code(name)
                        .name(name)
                        .value(value)
                        .colorValue(colorValue)
                        .pctChg(scale(pct, 2))
                        .circMv(circMv)
                        .stockCount(stockCount)
                        .upCount(upCount)
                        .downCount(downCount)
                        .avgPe(scale(avgPe, 2))
                        .build());
            }
        }
        nodes.sort(Comparator.comparing(MarketHeatmapNode::getValue, Comparator.nullsLast(Comparator.reverseOrder())));
        if (nodes.size() > cap) {
            nodes = new ArrayList<>(nodes.subList(0, cap));
        }
        return MarketHeatmapResp.builder()
                .boardType("INDUSTRY")
                .colorBy(color)
                .sizeBy(size)
                .source("stock_basic")
                .asOf(LocalDateTime.now())
                .nodes(nodes)
                .note("行业块=流通市值加权；涨跌为市值加权涨跌幅。参考金融界大盘云图风格。")
                .build();
    }

    private MarketHeatmapResp fromSectorBoard(String type, String color, String size, int cap) {
        String sortBy = "netInflow".equals(color) ? "netInflow" : "pctChg";
        SectorBoardResp board = sectorBoardService.board(type, sortBy, "desc", cap, null);
        List<MarketHeatmapNode> nodes = new ArrayList<>();
        if (Objects.nonNull(board) && CollUtil.isNotEmpty(board.getItems())) {
            for (SectorBoardItem item : board.getItems()) {
                Integer stockCount = null;
                if (Objects.nonNull(item.getUpCount()) || Objects.nonNull(item.getDownCount())) {
                    stockCount = (Objects.nonNull(item.getUpCount()) ? item.getUpCount() : 0)
                            + (Objects.nonNull(item.getDownCount()) ? item.getDownCount() : 0);
                }
                BigDecimal value = resolveSize(size, null, item.getAmount(), stockCount);
                if (Objects.isNull(value) || value.signum() <= 0) {
                    // 兜底：用家数或 1，避免空白
                    value = Objects.nonNull(stockCount) && stockCount > 0
                            ? BigDecimal.valueOf(stockCount)
                            : BigDecimal.ONE;
                }
                BigDecimal colorValue = resolveColor(color, item.getPctChg(), null, item.getNetInflow());
                nodes.add(MarketHeatmapNode.builder()
                        .code(item.getCode())
                        .name(item.getName())
                        .value(value)
                        .colorValue(colorValue)
                        .pctChg(item.getPctChg())
                        .amount(item.getAmount())
                        .stockCount(stockCount)
                        .upCount(item.getUpCount())
                        .downCount(item.getDownCount())
                        .netInflow(item.getNetInflow())
                        .leadStockCode(item.getLeadStockCode())
                        .leadStockName(item.getLeadStockName())
                        .leadStockPct(item.getLeadStockPct())
                        .build());
            }
        }
        return MarketHeatmapResp.builder()
                .boardType(type)
                .colorBy(color)
                .sizeBy(size)
                .source("sector_quote")
                .tradeDate(Objects.nonNull(board) ? board.getTradeDate() : null)
                .asOf(LocalDateTime.now())
                .nodes(nodes)
                .note(Objects.nonNull(board) && StringUtils.isNotBlank(board.getMessage())
                        ? board.getMessage()
                        : "概念/题材块来自板块行情；块大小优先成交额。")
                .build();
    }

    private String normalizeColorBy(String colorBy) {
        if (StringUtils.isBlank(colorBy)) {
            return "pctChg";
        }
        String c = colorBy.trim();
        if ("pe".equalsIgnoreCase(c) || "peTtm".equalsIgnoreCase(c)) {
            return "pe";
        }
        if ("netInflow".equalsIgnoreCase(c) || "fund".equalsIgnoreCase(c)) {
            return "netInflow";
        }
        return "pctChg";
    }

    private String normalizeSizeBy(String sizeBy, String type) {
        if (StringUtils.isBlank(sizeBy)) {
            return "INDUSTRY".equals(type) ? "circMv" : "amount";
        }
        String s = sizeBy.trim();
        if ("stockCount".equalsIgnoreCase(s) || "count".equalsIgnoreCase(s)) {
            return "stockCount";
        }
        if ("amount".equalsIgnoreCase(s)) {
            return "amount";
        }
        return "circMv";
    }

    private BigDecimal resolveSize(String size, BigDecimal circMv, BigDecimal amount, Integer stockCount) {
        if ("stockCount".equals(size)) {
            return Objects.nonNull(stockCount) ? BigDecimal.valueOf(stockCount) : null;
        }
        if ("amount".equals(size)) {
            if (Objects.nonNull(amount) && amount.signum() > 0) {
                return amount;
            }
            if (Objects.nonNull(circMv) && circMv.signum() > 0) {
                return circMv;
            }
            return Objects.nonNull(stockCount) ? BigDecimal.valueOf(stockCount) : null;
        }
        if (Objects.nonNull(circMv) && circMv.signum() > 0) {
            return circMv;
        }
        if (Objects.nonNull(amount) && amount.signum() > 0) {
            return amount;
        }
        return Objects.nonNull(stockCount) ? BigDecimal.valueOf(stockCount) : null;
    }

    private BigDecimal resolveColor(String color, BigDecimal pct, BigDecimal pe, BigDecimal netInflow) {
        if ("pe".equals(color)) {
            return pe;
        }
        if ("netInflow".equals(color)) {
            return netInflow;
        }
        return pct;
    }

    private BigDecimal decimal(Object raw) {
        if (Objects.isNull(raw)) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer intVal(Object raw) {
        if (Objects.isNull(raw)) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    private String str(Object raw) {
        return Objects.isNull(raw) ? null : String.valueOf(raw);
    }

    private BigDecimal scale(BigDecimal v, int scale) {
        if (Objects.isNull(v)) {
            return null;
        }
        return v.setScale(scale, RoundingMode.HALF_UP);
    }
}
