package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 可展示市场主体白名单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("market_actor")
public class MarketActor {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主体唯一编码 */
    private String actorCode;

    /** 主体名称 */
    private String actorName;

    /** 主体类型 SEAT/KOL */
    private String actorType;

    /** 公开平台 */
    private String platform;

    /** 已核验公开账号主页 */
    private String accountUrl;

    /** 已授权订阅源地址 */
    private String feedUrl;

    /** 来源状态 READY/PENDING_VERIFICATION */
    private String sourceStatus;

    /** 核验或限制说明 */
    private String sourceNote;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
