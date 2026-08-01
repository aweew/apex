package com.awe.apex.common.api;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应对象
 *
 * @author Awe
 * @date 2023/4/6 13:41
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class PageResponse<T> {

    /**
     * 列表数据
     */
    private List<T> records = Collections.emptyList();

    /**
     * 当前页码
     */
    @Schema(description = "当前页码")
    private Long current;

    /**
     * 每页条数
     */
    private Long size;

    /**
     * 总数
     */
    private Long total;

    public static <R, T> PageResponse<R> empty(PageRequest pageRequest) {
        PageResponse<R> response = new PageResponse<>();
        response.setCurrent(pageRequest.getCurrent());
        response.setSize(pageRequest.getSize());
        response.setTotal(0L);
        return response;
    }

    public static <R, T> PageResponse<R> empty(IPage<T> page) {
        PageResponse<R> response = new PageResponse<>();
        response.setCurrent(page.getCurrent());
        response.setSize(page.getSize());
        response.setTotal(page.getTotal());
        return response;
    }

    public static <R> PageResponse<R> createEmpty(Class<R> clazz, PageResponse r) {
        PageResponse<R> response = new PageResponse<>();
        response.setCurrent(r.getCurrent());
        response.setSize(r.getSize());
        response.setTotal(r.getTotal());
        List<R> records = new ArrayList<>();
        response.setRecords(records);
        return response;
    }

    public static <R, T> PageResponse<R> create(IPage<T> page, DataProcessor<R, T> dateProcessor) {
        PageResponse<R> response = new PageResponse<>();
        response.setCurrent(page.getCurrent());
        response.setSize(page.getSize());
        response.setTotal(page.getTotal());
        List<R> records = new ArrayList<>();
        if (CollUtil.isNotEmpty(page.getRecords())) {
            for (T record : page.getRecords()) {
                R data = dateProcessor.process(record);
                records.add(data);
            }
        }
        response.setRecords(records);
        return response;
    }

    @FunctionalInterface
    public interface DataProcessor<R, T> {
        R process(T data);
    }

}
