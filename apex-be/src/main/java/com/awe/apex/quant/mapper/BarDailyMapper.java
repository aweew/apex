package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

/**
 * 日线行情 Mapper
 */
@Mapper
public interface BarDailyMapper extends BaseMapper<BarDaily> {

    /**
     * 查询决策日后实际存在的日线
     *
     * @param code       证券代码
     * @param actionDate 决策日
     * @param limit      最大根数
     * @return 升序日线
     */
    @Select("""
            SELECT t1.code,
                   t1.trade_date,
                   t1.open_price,
                   t1.high_price,
                   t1.low_price,
                   t1.close_price
            FROM bar_daily t1
            WHERE t1.code = #{code}
              AND t1.trade_date > #{actionDate}
              AND t1.deleted = 0
            ORDER BY t1.trade_date ASC
            LIMIT #{limit}
            """)
    List<BarDaily> selectOutcomeBars(@Param("code") String code,
                                     @Param("actionDate") LocalDate actionDate,
                                     @Param("limit") int limit);

    /**
     * 查询指定日期区间内实际存在的日线
     *
     * @param code      证券代码
     * @param beginDate 开始日期
     * @param endDate   结束日期
     * @return 升序日线
     */
    @Select("""
            SELECT t1.code,
                   t1.trade_date,
                   t1.open_price,
                   t1.high_price,
                   t1.low_price,
                   t1.close_price
            FROM bar_daily t1
            WHERE t1.code = #{code}
              AND t1.trade_date >= #{beginDate}
              AND t1.trade_date <= #{endDate}
              AND t1.deleted = 0
            ORDER BY t1.trade_date ASC
            """)
    List<BarDaily> selectOutcomeBarsBetween(@Param("code") String code,
                                            @Param("beginDate") LocalDate beginDate,
                                            @Param("endDate") LocalDate endDate);

    /**
     * 查询指定日期前最近若干根日线
     *
     * @param code     证券代码
     * @param asOfDate 截止日期
     * @param limit    最大根数
     * @return 日线倒序列表
     */
    @Select("""
            SELECT t1.code,
                   t1.trade_date,
                   t1.open_price,
                   t1.high_price,
                   t1.low_price,
                   t1.close_price
            FROM bar_daily t1
            WHERE t1.code = #{code}
              AND t1.trade_date <= #{asOfDate}
              AND t1.deleted = 0
            ORDER BY t1.trade_date DESC
            LIMIT #{limit}
            """)
    List<BarDaily> selectRecentBars(@Param("code") String code,
                                    @Param("asOfDate") LocalDate asOfDate,
                                    @Param("limit") int limit);

    /**
     * 计算全市场最新收盘价站上MA20的股票比例
     *
     * @param asOfDate 截止日期
     * @return 比例，范围0到1
     */
    @Select("""
            SELECT AVG(CASE WHEN t1.close_price >= t1.ma20 THEN 1 ELSE 0 END)
            FROM (
                SELECT t2.code,
                       t2.close_price,
                       AVG(t2.close_price) OVER (
                           PARTITION BY t2.code
                           ORDER BY t2.trade_date
                           ROWS BETWEEN 19 PRECEDING AND CURRENT ROW
                       ) AS ma20,
                       ROW_NUMBER() OVER (PARTITION BY t2.code ORDER BY t2.trade_date DESC) AS latest_no,
                       COUNT(*) OVER (PARTITION BY t2.code) AS bar_count
                FROM bar_daily t2
                INNER JOIN stock_basic t3 ON t3.code = t2.code AND t3.deleted = 0
                WHERE t2.trade_date <= #{asOfDate}
                  AND t2.trade_date >= DATE_SUB(#{asOfDate}, INTERVAL 90 DAY)
                  AND t2.close_price IS NOT NULL
                  AND t2.close_price > 0
                  AND t2.deleted = 0
                  AND (t3.st_flag IS NULL OR t3.st_flag = 0)
            ) t1
            WHERE t1.latest_no = 1
              AND t1.bar_count >= 20
            """)
    BigDecimal selectMarketAboveMa20Ratio(@Param("asOfDate") LocalDate asOfDate);
}
