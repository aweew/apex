package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 财务分析指标 Mapper
 */
@Mapper
public interface StockFinIndicatorMapper extends BaseMapper<StockFinIndicator> {

    /**
     * 查询截至目标交易日的财务指标。
     *
     * @param asOfDate 目标交易日
     * @return 按证券和报告期倒序排列的财务指标
     */
    @Select("""
            SELECT t1.*
            FROM stock_fin_indicator t1
            WHERE t1.report_date <= #{asOfDate}
              AND t1.deleted = 0
            ORDER BY t1.code ASC, t1.report_date DESC
            """)
    List<StockFinIndicator> selectFactorResearchIndicators(@Param("asOfDate") LocalDate asOfDate);
}
