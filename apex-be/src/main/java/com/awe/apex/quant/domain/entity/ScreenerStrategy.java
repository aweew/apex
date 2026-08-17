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
import java.time.LocalDateTime;

/**
 * 用户选股策略
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("screener_strategy")
public class ScreenerStrategy implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 策略名称 */
    private String name;

    /** 策略说明 */
    private String description;

    /** 来源类型 */
    private String sourceType;

    /** 系统模板标识 */
    private String templateKey;

    /** 运行模式 */
    private String runMode;

    /** 是否启用 */
    private Integer enabled;

    /** 排序号 */
    private Integer sortNo;

    /** 版本号 */
    private Integer versionNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
