package com.awe.apex.manager.domain.menu.dto.resp;

import com.awe.apex.common.constant.enums.StatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统权限响应对象
 *
 * @author Awe
 * @since 2025-12-12 15:17:34
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class MenuResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 577478979174390162L;

    /**
     * 主键id
     */
    private Long id;

    /**
     * 权限名称
     */
    private String name;

    /**
     * 标题
     */
    private String title;

    /**
     * 权限编码
     */
    private String code;

    /**
     * 父级权限id
     */
    private Long parentId;

    /**
     * 权限类型（1菜单，2按钮，3页面）
     */
    private Integer type;

    /**
     * 状态（1正常，2禁用）
     */
    private StatusEnum status;

    /**
     * 层级
     */
    private Integer level;

    /**
     * 排序，数字越小越靠前
     */
    private Integer sort;

    /**
     * 菜单/按钮等链接地址
     */
    private String url;

    /**
     * 菜单/按钮的图标
     */
    private String icon;

    /**
     * 打开类型（1当前页，2新标签）
     */
    private Integer openType;

    /**
     * 路由地址
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 路由参数
     */
    private String queryParam;

    /**
     * 是否外链
     */
    private Boolean isFrame;

    /**
     * 是否缓存
     */
    private Boolean isCache;

    /**
     * 是否显示
     */
    private Boolean isVisible;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人id
     */
    private Long createUserId;

    /**
     * 更新人id
     */
    private Long updateUserId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 子菜单列表
     */
    private List<MenuResp> childList;

}
