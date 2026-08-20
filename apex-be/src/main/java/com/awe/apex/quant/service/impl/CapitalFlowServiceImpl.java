package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.CapitalFlowOverviewResp;
import com.awe.apex.quant.domain.dto.DragonTigerItemResp;
import com.awe.apex.quant.domain.dto.NorthboundFlowResp;
import com.awe.apex.quant.domain.dto.StockFundFlowItem;
import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.domain.entity.DragonTigerItem;
import com.awe.apex.quant.domain.entity.NorthboundFlow;
import com.awe.apex.quant.domain.entity.StockFundFlow;
import com.awe.apex.quant.mapper.DragonTigerItemMapper;
import com.awe.apex.quant.mapper.NorthboundFlowMapper;
import com.awe.apex.quant.mapper.StockFundFlowMapper;
import com.awe.apex.quant.service.ICapitalFlowService;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 资金面与龙虎榜服务实现。
 */
@Service
public class CapitalFlowServiceImpl implements ICapitalFlowService {

    private static final int REFRESH_TIMEOUT_SECONDS = 900;

    @Resource
    private NorthboundFlowMapper northboundFlowMapper;

    @Resource
    private StockFundFlowMapper stockFundFlowMapper;

    @Resource
    private DragonTigerItemMapper dragonTigerItemMapper;

    @Resource
    private ISectorBoardService sectorBoardService;

    @Resource
    private IDataSyncJobService dataSyncJobService;

    /**
     * 查询最新资金面总览。
     *
     * @param limit 每类榜单条数
     * @return 资金面总览
     */
    @Override
    public CapitalFlowOverviewResp overview(Integer limit) {
        int size = Objects.isNull(limit) || limit <= 0 ? 20 : Math.min(limit, 100);

        NorthboundFlow northboundFlow = northboundFlowMapper.selectOne(Wrappers.<NorthboundFlow>lambdaQuery()
                .orderByDesc(NorthboundFlow::getTradeDate)
                .orderByDesc(NorthboundFlow::getId)
                .last("LIMIT 1"));
        NorthboundFlowResp northboundFlowResp = null;
        if (Objects.nonNull(northboundFlow)) {
            northboundFlowResp = NorthboundFlowResp.builder()
                    .tradeDate(northboundFlow.getTradeDate())
                    .netBuyAmount(northboundFlow.getNetBuyAmount())
                    .buyAmount(northboundFlow.getBuyAmount())
                    .sellAmount(northboundFlow.getSellAmount())
                    .cumulativeNetBuyAmount(northboundFlow.getCumulativeNetBuyAmount())
                    .dataStatus(northboundFlow.getDataStatus())
                    .syncedAt(northboundFlow.getSyncedAt())
                    .build();
        }

        List<StockFundFlow> stockRows = stockFundFlowMapper.selectList(Wrappers.<StockFundFlow>lambdaQuery()
                .apply("trade_date = (SELECT MAX(t1.trade_date) FROM stock_fund_flow t1 WHERE t1.deleted = 0)")
                .orderByDesc(StockFundFlow::getMainNetInflow)
                .last("LIMIT " + size));
        List<StockFundFlowItem> stockFlows = new ArrayList<>();
        LocalDate stockTradeDate = null;
        LocalDateTime stockSyncedAt = null;
        if (CollUtil.isNotEmpty(stockRows)) {
            stockTradeDate = stockRows.get(0).getTradeDate();
            stockSyncedAt = stockRows.get(0).getSyncedAt();
            for (StockFundFlow stockRow : stockRows) {
                stockFlows.add(StockFundFlowItem.builder()
                        .code(stockRow.getCode())
                        .name(stockRow.getName())
                        .tradeDate(stockRow.getTradeDate())
                        .pctChg(stockRow.getPctChg())
                        .mainNetInflow(stockRow.getMainNetInflow())
                        .mainNetInflowPct(stockRow.getMainNetInflowPct())
                        .superLargeNetInflow(stockRow.getSuperLargeNetInflow())
                        .largeNetInflow(stockRow.getLargeNetInflow())
                        .mediumNetInflow(stockRow.getMediumNetInflow())
                        .smallNetInflow(stockRow.getSmallNetInflow())
                        .syncedAt(stockRow.getSyncedAt())
                        .build());
            }
        }

        List<DragonTigerItem> dragonTigerRows = dragonTigerItemMapper.selectList(
                Wrappers.<DragonTigerItem>lambdaQuery()
                        .apply("trade_date = (SELECT MAX(t1.trade_date) FROM dragon_tiger_item t1 WHERE t1.deleted = 0)")
                        .orderByDesc(DragonTigerItem::getNetBuyAmount)
                        .last("LIMIT " + size));
        List<DragonTigerItemResp> dragonTigerItems = new ArrayList<>();
        LocalDate dragonTigerTradeDate = null;
        LocalDateTime dragonTigerSyncedAt = null;
        if (CollUtil.isNotEmpty(dragonTigerRows)) {
            dragonTigerTradeDate = dragonTigerRows.get(0).getTradeDate();
            dragonTigerSyncedAt = dragonTigerRows.get(0).getSyncedAt();
            for (DragonTigerItem dragonTigerRow : dragonTigerRows) {
                dragonTigerItems.add(DragonTigerItemResp.builder()
                        .code(dragonTigerRow.getCode())
                        .name(dragonTigerRow.getName())
                        .tradeDate(dragonTigerRow.getTradeDate())
                        .reason(dragonTigerRow.getReason())
                        .closePrice(dragonTigerRow.getClosePrice())
                        .pctChg(dragonTigerRow.getPctChg())
                        .turnoverRate(dragonTigerRow.getTurnoverRate())
                        .netBuyAmount(dragonTigerRow.getNetBuyAmount())
                        .buyAmount(dragonTigerRow.getBuyAmount())
                        .sellAmount(dragonTigerRow.getSellAmount())
                        .amount(dragonTigerRow.getAmount())
                        .syncedAt(dragonTigerRow.getSyncedAt())
                        .build());
            }
        }

        return CapitalFlowOverviewResp.builder()
                .northboundFlow(northboundFlowResp)
                .stockTradeDate(stockTradeDate)
                .stockSyncedAt(stockSyncedAt)
                .stockFlows(stockFlows)
                .industryFlows(sectorBoardService.board("INDUSTRY", "netInflow", "desc", size, null))
                .conceptFlows(sectorBoardService.board("CONCEPT", "netInflow", "desc", size, null))
                .dragonTigerTradeDate(dragonTigerTradeDate)
                .dragonTigerSyncedAt(dragonTigerSyncedAt)
                .dragonTigerItems(dragonTigerItems)
                .build();
    }

    /**
     * 同步指定资金面数据并返回最新总览。
     *
     * @param mode 同步模式flow、lhb或all
     * @param limit 每类榜单条数
     * @return 最新资金面总览
     */
    @Override
    public CapitalFlowOverviewResp refresh(String mode, Integer limit) {
        String refreshMode = StringUtils.isBlank(mode) ? "all" : mode.trim().toLowerCase(Locale.ROOT);
        if (!List.of("flow", "lhb", "all").contains(refreshMode)) {
            throw new BusinessException("资金面刷新模式仅支持 flow、lhb 或 all");
        }

        SyncStartReq request = new SyncStartReq();
        request.setTaskType("lhb".equals(refreshMode) ? "DRAGON_TIGER" : "CAPITAL_FLOW");
        request.setMode(refreshMode);
        if ("all".equals(refreshMode) && dataSyncJobService.isTaskRunning("DRAGON_TIGER")) {
            throw new BusinessException("龙虎榜正在同步，请等待完成后再执行全部刷新");
        }
        waitForSuccess(dataSyncJobService.startSystemTask(request), "资金面");

        if (!"lhb".equals(refreshMode)) {
            SyncStartReq sectorRequest = new SyncStartReq();
            sectorRequest.setTaskType("SECTOR_QUOTE");
            sectorRequest.setTypes("INDUSTRY,CONCEPT,THEME");
            waitForSuccess(dataSyncJobService.startSystemTask(sectorRequest), "板块资金");
        }
        return overview(limit);
    }

    private void waitForSuccess(SyncJobResp initialJob, String taskName) {
        SyncJobResp job = initialJob;
        long deadline = System.nanoTime() + REFRESH_TIMEOUT_SECONDS * 1_000_000_000L;
        while ("PENDING".equals(job.getStatus()) || "RUNNING".equals(job.getStatus())) {
            if (System.nanoTime() >= deadline) {
                throw new BusinessException(taskName + "同步超时，请在数据同步页面查看任务状态");
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException(taskName + "同步等待已中断");
            }
            job = dataSyncJobService.getJob(job.getId());
        }
        if (!"SUCCESS".equals(job.getStatus())) {
            throw new BusinessException(taskName + "同步失败: " + job.getMessage());
        }
    }
}
