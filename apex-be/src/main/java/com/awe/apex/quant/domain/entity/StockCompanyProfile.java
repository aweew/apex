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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公司概况（F10 基本资料）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_company_profile")
public class StockCompanyProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 公司全称
     */
    private String orgName;

    /**
     * 英文名称
     */
    private String orgNameEn;

    /**
     * 曾用名
     */
    private String formerName;

    /**
     * A股代码
     */
    private String aCode;

    /**
     * A股简称
     */
    private String aName;

    /**
     * 所属地区
     */
    private String region;

    /**
     * 地区板块
     */
    private String areaBoard;

    /**
     * 东财行业
     */
    private String industryEm;

    /**
     * 证监会行业
     */
    private String industryCsrc;

    /**
     * 所属行业路径（板块层级）
     */
    private String boardPath;

    /**
     * 所属概念（逗号分隔）
     */
    private String concepts;

    /**
     * 董事长
     */
    private String chairman;

    /**
     * 法人代表
     */
    private String legalPerson;

    /**
     * 总经理
     */
    private String president;

    /**
     * 董秘
     */
    private String secretary;

    /**
     * 控股股东
     */
    private String controlHolder;

    /**
     * 控股股东持股比例
     */
    private String controlRatio;

    /**
     * 实际控制人
     */
    private String realController;

    /**
     * 实控人持股比例
     */
    private String realControllerRatio;

    /**
     * 经营性质
     */
    private String orgForm;

    /**
     * 成立日期
     */
    private LocalDate foundDate;

    /**
     * 上市日期
     */
    private LocalDate listDate;

    /**
     * 注册资本（万元）
     */
    private BigDecimal regCapital;

    /**
     * 发行价格
     */
    private BigDecimal issuePrice;

    /**
     * 员工人数
     */
    private Integer employeeNum;

    /**
     * 管理层人数
     */
    private Integer managerNum;

    /**
     * 主营业务
     */
    private String mainBusiness;

    /**
     * 公司介绍
     */
    private String orgProfile;

    /**
     * 公司亮点
     */
    private String orgHighlight;

    /**
     * 经营范围
     */
    private String businessScope;

    /**
     * 公司网站
     */
    private String website;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 传真
     */
    private String fax;

    /**
     * 办公地址
     */
    private String officeAddress;

    /**
     * 注册地址
     */
    private String regAddress;

    /**
     * 统一社会信用代码
     */
    private String regNum;

    /**
     * 交易市场
     */
    private String tradeMarket;

    /**
     * 原始扩展字段 JSON
     */
    private String payload;

    /**
     * 数据来源
     */
    private String source;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
