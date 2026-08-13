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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ClawBot 股票问答接口。
 */
@RestController
@RequestMapping("/bot/v1")
@Slf4j
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
        long startedAt = System.nanoTime();
        try {
            BotAskResp response = botQuestionService.ask(request);
            log.info("Bot 问答完成 requestId={} intent={} durationMs={}",
                    response.getRequestId(), response.getIntent(), elapsedMillis(startedAt));
            return Result.success(response);
        } catch (Exception ex) {
            log.warn("Bot 问答失败 requestId={} durationMs={} reason={}",
                    request.getRequestId(), elapsedMillis(startedAt), ex.getMessage());
            throw ex;
        }
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

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
