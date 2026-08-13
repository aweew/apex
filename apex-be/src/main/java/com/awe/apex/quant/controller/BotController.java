package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.bot.service.IBotQuestionService;
import com.awe.apex.quant.bot.service.IBotToolService;
import com.awe.apex.quant.domain.dto.BotAskReq;
import com.awe.apex.quant.domain.dto.BotAskResp;
import com.awe.apex.quant.domain.dto.BotToolReq;
import com.awe.apex.quant.domain.dto.BotToolResp;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ClawBot 股票问答接口。
 */
@RestController
@RequestMapping("/bot/v1")
public class BotController {

    @Resource
    private IBotQuestionService botQuestionService;

    @Resource
    private IBotToolService botToolService;

    /**
     * 回答微信中的股票问题。
     *
     * @param request 问答请求
     * @return 股票回答
     */
    @PostMapping("/ask")
    public Result<BotAskResp> ask(@Valid @RequestBody BotAskReq request) {
        return Result.success(botQuestionService.ask(request));
    }

    /**
     * 执行受控的结构化 Bot 工具。
     *
     * @param request 工具请求
     * @return 工具响应
     */
    @PostMapping("/tool")
    public Result<BotToolResp> tool(@Valid @RequestBody BotToolReq request) {
        return Result.success(botToolService.execute(request));
    }
}
