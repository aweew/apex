package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.IBotQuestionService;
import com.awe.apex.quant.bot.service.IBotToolService;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.BotAskReq;
import com.awe.apex.quant.domain.dto.BotAskResp;
import com.awe.apex.quant.domain.dto.BotToolReq;
import com.awe.apex.quant.domain.dto.BotToolResp;
import com.awe.apex.quant.service.ApexUserAuthService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

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

    @Resource
    private ApexBotProperties botProperties;

    @Resource
    private ApexUserContext userContext;

    @Resource
    private ApexUserAuthService userAuthService;

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
            BotAskResp response = userContext.runAsUser(requireBoundUserId(request.getUserId()),
                    () -> botQuestionService.ask(request));
            log.info("Bot 问答完成，请求编号={}，意图={}，耗时毫秒={}",
                    response.getRequestId(), response.getIntent(), elapsedMillis(startedAt));
            return Result.success(response);
        } catch (Exception ex) {
            log.warn("Bot 问答失败，请求编号={}，耗时毫秒={}，原因={}",
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
        return Result.success(userContext.runAsUser(requireBoundUserId(request.getUserId()),
                () -> botToolService.execute(request)));
    }

    private Long requireBoundUserId(String externalUserId) {
        Long userId = botProperties.getApexUserId();
        if (Objects.isNull(userId) || userId <= 0) {
            throw new BusinessException("Bot API 未绑定 Apex 用户");
        }
        if (StringUtils.isBlank(botProperties.getExternalUserId())) {
            throw new BusinessException("Bot API 未绑定外部用户");
        }
        if (!botProperties.getExternalUserId().equals(externalUserId)) {
            throw new BusinessException("Bot 用户无权访问该 Apex 账户");
        }
        userAuthService.requireEnabledUser(userId);
        return userId;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
