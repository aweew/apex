package com.awe.apex.common.convert;

import com.awe.apex.manager.domain.area.dto.AreaReq;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 基础转换器
 *
 * @author Awe
 * @since 2025/12/8 16:52
 */
public interface BaseConvert<E, AddReq, UpdateReq, Req, Resp> {

    // Req -> Entity（查询）
    E toEntity(Req req);

    // AddReq -> Entity（新增）
    E addToEntity(AddReq req);

    // UpdateReq -> Entity（更新，覆盖目标对象）
    void updateToEntity(UpdateReq req, @MappingTarget E entity);

    // Entity -> Resp（返回展示对象）
    Resp toResp(E entity);

    // List<Entity> -> List<Resp>
    List<Resp> toRespList(List<E> list);

    // 分页转换（MyBatis Plus）
    default IPage<Resp> toRespPage(IPage<E> page) {
        Page<Resp> respPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        respPage.setRecords(toRespList(page.getRecords()));
        return respPage;
    }
}
