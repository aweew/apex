package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.entity.DailyAction;

import java.time.LocalDate;
import java.util.List;

/**
 * 日终清单服务
 */
public interface IDailyActionService {

    /**
     * 生成当日清单
     *
     * @param date 日期，可空=今天
     * @return 清单
     */
    List<DailyAction> run(LocalDate date);

    /**
     * 查询某日清单
     *
     * @param date 日期
     * @return 清单
     */
    List<DailyAction> listByDate(LocalDate date);
}
