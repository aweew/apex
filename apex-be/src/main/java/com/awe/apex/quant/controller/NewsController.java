package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.NewsItemResp;
import com.awe.apex.quant.domain.dto.NewsOverviewResp;
import com.awe.apex.quant.domain.dto.NewsRefreshResp;
import com.awe.apex.quant.service.INewsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 多源新闻资讯
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    @Resource
    private INewsService newsService;

    /**
     * 新闻总览
     *
     * @param source  来源 all/eastmoney/cls/ths/sina/cctv
     * @param limit   条数
     * @param keyword 关键词
     * @return 总览
     */
    @GetMapping("/overview")
    public Result<NewsOverviewResp> overview(@RequestParam(defaultValue = "all") String source,
                                             @RequestParam(defaultValue = "80") Integer limit,
                                             @RequestParam(required = false) String keyword) {
        return Result.success(newsService.overview(source, limit, keyword));
    }

    /**
     * 按来源列表
     *
     * @param source 来源
     * @param limit  条数
     * @return 列表
     */
    @GetMapping("/list")
    public Result<List<NewsItemResp>> list(@RequestParam(defaultValue = "eastmoney") String source,
                                           @RequestParam(defaultValue = "80") Integer limit) {
        return Result.success(newsService.listBySource(source, limit));
    }

    /**
     * 刷新新闻（调用 AKShare 脚本）
     *
     * @param sources 来源
     * @param limit   每源条数
     * @return 结果
     */
    @PostMapping("/refresh")
    public Result<NewsRefreshResp> refresh(
            @RequestParam(required = false, defaultValue = "eastmoney,cls,ths,sina") String sources,
            @RequestParam(required = false, defaultValue = "80") Integer limit) {
        return Result.success(newsService.refresh(sources, limit));
    }
}
