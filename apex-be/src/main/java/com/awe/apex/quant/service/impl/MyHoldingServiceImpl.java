package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.MyHoldingSaveReq;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.MyHoldingMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.market.StockQuoteClient;
import com.awe.apex.quant.service.IMyHoldingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 我的持仓服务实现
 */
@Slf4j
@Service
public class MyHoldingServiceImpl implements IMyHoldingService {

    @Resource
    private MyHoldingMapper myHoldingMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private StockQuoteClient stockQuoteClient;

    /**
     * 持仓列表（附带现价/浮盈亏）
     *
     * @return 列表
     */
    @Override
    public List<MyHolding> listHoldings() {
        List<MyHolding> list = myHoldingMapper.selectList(Wrappers.<MyHolding>lambdaQuery()
                .orderByDesc(MyHolding::getUpdateTime)
                .orderByAsc(MyHolding::getCode));
        if (CollUtil.isEmpty(list)) {
            return list;
        }
        Map<String, StockBasic> basicMap = loadBasics(list);
        for (MyHolding holding : list) {
            StockBasic basic = basicMap.get(holding.getCode());
            if (Objects.nonNull(basic)) {
                if (StringUtils.isBlank(holding.getName()) && StringUtils.isNotBlank(basic.getName())) {
                    holding.setName(basic.getName());
                }
                holding.setMarketPrice(basic.getLatestPrice());
            }
            fillPnl(holding);
        }
        return list;
    }

    /**
     * 新增或更新持仓（同代码合并更新）
     *
     * @param req 请求
     * @return 持仓
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MyHolding save(MyHoldingSaveReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("证券代码不能为空");
        }
        String code = MarketCodeUtils.normalizeHoldingCode(req.getCode());
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("证券代码无效");
        }
        Integer quantity = Objects.nonNull(req.getQuantity()) ? req.getQuantity() : 0;
        if (quantity < 0) {
            throw new BusinessException("持仓数量不能为负");
        }

        String name = StringUtils.trim(req.getName());
        StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("LIMIT 1"));
        if (StringUtils.isBlank(name) && Objects.nonNull(basic)) {
            name = basic.getName();
        }

        LocalDateTime now = LocalDateTime.now();
        MyHolding exist = null;
        if (Objects.nonNull(req.getId())) {
            exist = myHoldingMapper.selectById(req.getId());
        }
        if (Objects.isNull(exist)) {
            exist = myHoldingMapper.selectOne(Wrappers.<MyHolding>lambdaQuery()
                    .eq(MyHolding::getCode, code)
                    .last("LIMIT 1"));
        }

        if (Objects.nonNull(exist)) {
            exist.setCode(code);
            exist.setName(name);
            exist.setQuantity(quantity);
            exist.setCostPrice(req.getCostPrice());
            exist.setStopLoss(req.getStopLoss());
            exist.setTakeProfit(req.getTakeProfit());
            exist.setNote(StringUtils.trim(req.getNote()));
            exist.setUpdateTime(now);
            myHoldingMapper.updateById(exist);
            fillPnlFromBasic(exist, basic);
            return exist;
        }

        MyHolding created = MyHolding.builder()
                .code(code)
                .name(name)
                .quantity(quantity)
                .costPrice(req.getCostPrice())
                .stopLoss(req.getStopLoss())
                .takeProfit(req.getTakeProfit())
                .note(StringUtils.trim(req.getNote()))
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        myHoldingMapper.insert(created);
        fillPnlFromBasic(created, basic);
        return created;
    }

    /**
     * 删除持仓
     *
     * @param id 主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (Objects.isNull(id)) {
            throw new BusinessException("持仓ID不能为空");
        }
        myHoldingMapper.deleteById(id);
    }

    /**
     * 刷新持仓行情（缺报价优先），并返回最新列表
     *
     * @param onlyMissing 是否只刷本地无现价的
     * @return 结果（含 holdings）
     */
    @Override
    public Map<String, Object> refreshQuotes(Boolean onlyMissing) {
        boolean missingOnly = !Boolean.FALSE.equals(onlyMissing);
        List<MyHolding> list = myHoldingMapper.selectList(Wrappers.<MyHolding>lambdaQuery()
                .orderByAsc(MyHolding::getCode));
        int success = 0;
        int fail = 0;
        for (MyHolding holding : list) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (StringUtils.isBlank(code)) {
                continue;
            }
            if (!code.equals(holding.getCode())) {
                holding.setCode(code);
                myHoldingMapper.updateById(holding);
            }
            StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getCode, code)
                    .last("LIMIT 1"));
            if (missingOnly && Objects.nonNull(basic) && Objects.nonNull(basic.getLatestPrice())) {
                continue;
            }
            try {
                StockBasic synced = upsertQuote(code);
                if (Objects.nonNull(synced) && Objects.nonNull(synced.getLatestPrice())) {
                    success++;
                    if (StringUtils.isBlank(holding.getName()) && StringUtils.isNotBlank(synced.getName())) {
                        holding.setName(synced.getName());
                        myHoldingMapper.updateById(holding);
                    }
                } else {
                    fail++;
                }
            } catch (Exception ex) {
                fail++;
                log.warn("持仓刷新行情失败 code={}, err={}", code, ex.getMessage());
            }
            try {
                Thread.sleep(120L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("fail", fail);
        result.put("holdings", listHoldings());
        result.put("message", "行情刷新完成：成功 " + success + " / 失败 " + fail);
        return result;
    }

    /**
     * 拉取行情并写入 stock_basic
     */
    private StockBasic upsertQuote(String code) {
        StockBasic fetched = stockQuoteClient.fetchBasic(code);
        LocalDateTime now = LocalDateTime.now();
        StockBasic existing = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("LIMIT 1"));
        if (Objects.isNull(existing)) {
            fetched.setCreateTime(now);
            fetched.setUpdateTime(now);
            stockBasicMapper.insert(fetched);
            return fetched;
        }
        existing.setName(fetched.getName());
        existing.setMarket(fetched.getMarket());
        existing.setStFlag(fetched.getStFlag());
        existing.setLatestPrice(fetched.getLatestPrice());
        existing.setPctChg(fetched.getPctChg());
        existing.setPeTtm(fetched.getPeTtm());
        existing.setPb(fetched.getPb());
        existing.setTotalMv(fetched.getTotalMv());
        existing.setCircMv(fetched.getCircMv());
        existing.setIndustry(fetched.getIndustry());
        existing.setSource(fetched.getSource());
        existing.setQuoteTime(fetched.getQuoteTime());
        existing.setUpdateTime(now);
        stockBasicMapper.updateById(existing);
        return existing;
    }

    private Map<String, StockBasic> loadBasics(List<MyHolding> list) {
        Map<String, StockBasic> map = new HashMap<>();
        List<String> codes = new java.util.ArrayList<>();
        for (MyHolding holding : list) {
            if (StringUtils.isNotBlank(holding.getCode()) && !codes.contains(holding.getCode())) {
                codes.add(holding.getCode());
            }
        }
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, codes));
        for (StockBasic basic : basics) {
            map.put(basic.getCode(), basic);
        }
        return map;
    }

    private void fillPnlFromBasic(MyHolding holding, StockBasic basic) {
        if (Objects.nonNull(basic)) {
            holding.setMarketPrice(basic.getLatestPrice());
        }
        fillPnl(holding);
    }

    private void fillPnl(MyHolding holding) {
        BigDecimal price = holding.getMarketPrice();
        Integer qty = holding.getQuantity();
        if (Objects.isNull(price) || Objects.isNull(qty) || qty <= 0) {
            holding.setMarketValue(null);
            holding.setPnl(null);
            holding.setPnlPct(null);
            return;
        }
        BigDecimal marketValue = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        holding.setMarketValue(marketValue);
        if (Objects.isNull(holding.getCostPrice()) || holding.getCostPrice().signum() <= 0) {
            holding.setPnl(null);
            holding.setPnlPct(null);
            return;
        }
        BigDecimal cost = holding.getCostPrice().multiply(BigDecimal.valueOf(qty));
        BigDecimal pnl = marketValue.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        holding.setPnl(pnl);
        holding.setPnlPct(pnl.divide(cost, 4, RoundingMode.HALF_UP));
    }
}
