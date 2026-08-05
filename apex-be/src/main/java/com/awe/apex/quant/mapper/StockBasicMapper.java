package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.StockBasic;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 股票基础信息 Mapper
 */
@Mapper
public interface StockBasicMapper extends BaseMapper<StockBasic> {

    /**
     * 按行业聚合：流通市值加权涨跌、家数、平均 PE
     *
     * @return 聚合行
     */
    @Select("""
            SELECT industry AS name,
                   COUNT(1) AS stockCount,
                   SUM(IFNULL(circ_mv, 0)) AS circMv,
                   SUM(CASE
                           WHEN circ_mv IS NOT NULL AND circ_mv > 0 AND pct_chg IS NOT NULL
                               THEN circ_mv * pct_chg
                           ELSE 0 END)
                       / NULLIF(SUM(CASE
                                       WHEN circ_mv IS NOT NULL AND circ_mv > 0 AND pct_chg IS NOT NULL
                                           THEN circ_mv
                                       ELSE 0 END), 0) AS weightedPctChg,
                   AVG(CASE WHEN pe_ttm IS NOT NULL AND pe_ttm > 0 THEN pe_ttm END) AS avgPe,
                   SUM(CASE WHEN pct_chg IS NOT NULL AND pct_chg > 0 THEN 1 ELSE 0 END) AS upCount,
                   SUM(CASE WHEN pct_chg IS NOT NULL AND pct_chg < 0 THEN 1 ELSE 0 END) AS downCount
            FROM stock_basic
            WHERE deleted = 0
              AND industry IS NOT NULL
              AND industry <> ''
              AND (st_flag IS NULL OR st_flag = 0)
              AND (name IS NULL OR name NOT LIKE '%ST%')
            GROUP BY industry
            HAVING SUM(IFNULL(circ_mv, 0)) > 0
            ORDER BY circMv DESC
            """)
    List<Map<String, Object>> aggregateIndustryHeatmap();
}
