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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 智能决策运行元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("decision_run")
public class DecisionRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属用户ID */
    private Long userId;
    private String runNo;
    private LocalDate actionDate;
    private LocalDateTime asOfTime;
    private String groupName;
    private String mode;
    private String ruleVersion;
    private String modelVersion;
    private String featureVersion;
    private String dataLevel;
    private String dataCutoffJson;
    private String configSnapshotJson;
    private String status;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer published;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
