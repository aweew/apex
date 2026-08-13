package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易者身份。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trader")
public class Trader implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 交易者名称 */
    private String name;
    /** 当前展示昵称 */
    private String nickname;
    /** 微信会话侧稳定身份标识 */
    private String wechatPeerId;
    /** 头像地址 */
    private String avatar;
    /** 初始资金 */
    private BigDecimal initialCapital;
    /** ACTIVE / DISABLED */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
