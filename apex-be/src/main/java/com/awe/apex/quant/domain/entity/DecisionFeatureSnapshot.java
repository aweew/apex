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
 * 决策时实际使用的证券特征快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("decision_feature_snapshot")
public class DecisionFeatureSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private String code;
    private String action;
    private String featureVersion;
    private String featureHash;
    private BigDecimal signalScore;
    private Integer confluenceCount;
    private Integer hotSourceCount;
    private Integer mainlineMatch;
    private String valuationLevel;
    private String marketStance;
    private String dataQuality;
    private String selectionStatus;
    private String rejectReason;
    private Integer rankNo;
    private String featureJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
