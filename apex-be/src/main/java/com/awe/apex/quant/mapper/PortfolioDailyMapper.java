package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.PortfolioDaily;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 组合每日快照
 */
public interface PortfolioDailyMapper extends BaseMapper<PortfolioDaily> {

    /**
     * 查询指定交易日前的最高组合权益
     *
     * @param portfolioId 组合ID
     * @param tradeDate   交易日
     * @return 历史最高权益
     */
    @Select("""
            SELECT MAX(t1.total_equity)
            FROM portfolio_daily t1
            WHERE t1.portfolio_id = #{portfolioId}
              AND t1.trade_date < #{tradeDate}
              AND t1.deleted = 0
            """)
    BigDecimal selectPeakEquityBefore(@Param("portfolioId") Long portfolioId,
                                      @Param("tradeDate") LocalDate tradeDate);
}
