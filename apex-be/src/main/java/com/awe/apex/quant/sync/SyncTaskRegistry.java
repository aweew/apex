package com.awe.apex.quant.sync;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.market.TradingCalendar;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 同步任务注册表
 */
@Component
public class SyncTaskRegistry {

    private final Map<String, SyncTaskSpec> specs = new LinkedHashMap<>();

    public SyncTaskRegistry() {
        register(SyncTaskSpec.builder()
                .taskType("DECISION")
                .name("智能决策")
                .groupName("决策任务")
                .description("共享扫描全市场一次，再按用户组合生成交易动作")
                .defaultParamsHint("工作日 06:50、11:40、15:40 运行；收盘同步完成后自动补算")
                .timeoutSec(1800)
                .build());
        // 置顶：收盘后日常一键同步（不含全A日线，那类任务太重）
        register(SyncTaskSpec.builder()
                .taskType("CLOSE_BUNDLE")
                .name("一键收盘同步")
                .groupName("每日收盘")
                .description("顺序执行：大盘指数 → 板块行情 → 涨停池 → 热点 → 资讯")
                .scriptFile("sync_close_bundle.py")
                .defaultParamsHint("日常增量；指数默认近60日")
                .timeoutSec(3600)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("NIGHTLY_REPAIR")
                .name("凌晨数据补缺")
                .groupName("每日收盘")
                .description("依次补齐全A日线、公司资料和财务基本面")
                .scriptFile("sync_nightly_repair.py")
                .defaultParamsHint("每天 02:10；日线持续补齐最多 150 分钟、公司资料 300 只、财务 60 只")
                .timeoutSec(21600)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("A_SHARE_LIST")
                .name("全A股票列表")
                .groupName("行情基础")
                .description("同步 stock_basic 全市场代码与名称")
                .scriptFile("sync_a_share.py")
                .defaultParamsHint("mode=list")
                .timeoutSec(600)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("A_SHARE_BARS")
                .name("全A日线")
                .groupName("行情基础")
                .description("同步 bar_daily，支持断点续传（耗时长）")
                .scriptFile("sync_a_share.py")
                .defaultParamsHint("mode=bars start=20240101 limit=可选")
                .timeoutSec(86400)
                .progressFile(".progress/bars_progress.json")
                .build());
        register(SyncTaskSpec.builder()
                .taskType("A_SHARE_MISSING")
                .name("日线缺口补齐")
                .groupName("行情基础")
                .description("扫描缺口并分批补日线")
                .scriptFile("sync_missing_bars.py")
                .defaultParamsHint("batch=80 rounds=3 start=20240101")
                .timeoutSec(86400)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("TURNOVER")
                .name("换手率回补")
                .groupName("行情基础")
                .description("回补 bar_daily.turnover_rate")
                .scriptFile("backfill_turnover.py")
                .defaultParamsHint("limit=50 或 all")
                .timeoutSec(86400)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("COMPANY_PROFILE")
                .name("公司概况 F10")
                .groupName("基本面")
                .description("同步 stock_company_profile（行业/概念等）")
                .scriptFile("sync_company_profile.py")
                .defaultParamsHint("limit=50 或 all")
                .timeoutSec(86400)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("FUNDAMENTALS")
                .name("财务基本面")
                .groupName("基本面")
                .description("财报摘要/指标/三大报表")
                .scriptFile("sync_fundamentals.py")
                .defaultParamsHint("mode=all limit=20")
                .timeoutSec(86400)
                .progressFile(".progress/fund_progress.json")
                .build());
        register(SyncTaskSpec.builder()
                .taskType("INDEX")
                .name("大盘指数")
                .groupName("市场看板")
                .description("同步主流市场指数日线 index_bar")
                .scriptFile("sync_index.py")
                .defaultParamsHint("start=20180101")
                .timeoutSec(1800)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("HOT")
                .name("热点榜")
                .groupName("市场看板")
                .description("东财/雪球/百度热度快照")
                .scriptFile("sync_hot.py")
                .defaultParamsHint("sources=eastmoney,baidu limit=50")
                .timeoutSec(600)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("NEWS")
                .name("资讯")
                .groupName("市场看板")
                .description("多源财经资讯")
                .scriptFile("sync_news.py")
                .defaultParamsHint("sources=eastmoney,cls,ths,sina limit=80")
                .timeoutSec(600)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("SECTOR_QUOTE")
                .name("板块行情+资金")
                .groupName("板块")
                .description("行业/概念/题材涨跌幅与净流入（含3日/5日/涨跌原因）")
                .scriptFile("sync_sector.py")
                .defaultParamsHint("mode=quote types=INDUSTRY,CONCEPT,THEME")
                .timeoutSec(900)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("CAPITAL_FLOW")
                .name("北向与个股资金流")
                .groupName("市场看板")
                .description("同步北向资金和个股主力资金流，支持收盘后一并补刷龙虎榜")
                .scriptFile("sync_capital_flow.py")
                .defaultParamsHint("mode=flow；18:20 补刷使用 mode=all")
                .timeoutSec(900)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("DRAGON_TIGER")
                .name("龙虎榜")
                .groupName("市场看板")
                .description("同步最近交易日龙虎榜明细")
                .scriptFile("sync_capital_flow.py")
                .defaultParamsHint("mode=lhb；交易日 17:30 首刷")
                .timeoutSec(900)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("SECTOR_CONS")
                .name("板块成分股")
                .groupName("板块")
                .description("按类型批量同步成分（可用 limit 限流）")
                .scriptFile("sync_sector.py")
                .defaultParamsHint("mode=cons types=INDUSTRY limit=10")
                .timeoutSec(86400)
                .build());
        register(SyncTaskSpec.builder()
                .taskType("LIMIT_UP")
                .name("涨停池/连板天梯")
                .groupName("市场看板")
                .description("东财涨停池落库，供涨停复盘连板天梯")
                .scriptFile("sync_limit_up.py")
                .defaultParamsHint("with-prev=true")
                .timeoutSec(300)
                .build());
    }

    /**
     * 全部任务规格
     *
     * @return 列表
     */
    public List<SyncTaskSpec> all() {
        return new ArrayList<>(specs.values());
    }

    /**
     * 按类型获取
     *
     * @param taskType 类型
     * @return 规格
     */
    public SyncTaskSpec require(String taskType) {
        if (StringUtils.isBlank(taskType)) {
            throw new BusinessException("任务类型不能为空");
        }
        SyncTaskSpec spec = specs.get(taskType.trim().toUpperCase(Locale.ROOT));
        if (Objects.isNull(spec)) {
            throw new BusinessException("未知同步任务类型: " + taskType);
        }
        return spec;
    }

    /**
     * 构建脚本命令参数（不含 python / -u / script）
     *
     * @param spec 规格
     * @param req  请求
     * @return 参数列表
     */
    public List<String> buildArgs(SyncTaskSpec spec, SyncStartReq req) {
        SyncStartReq safe = Objects.isNull(req) ? new SyncStartReq() : req;
        String type = spec.getTaskType();
        List<String> args = new ArrayList<>();
        switch (type) {
            case "A_SHARE_LIST" -> {
                args.add("--mode");
                args.add("list");
            }
            case "A_SHARE_BARS" -> {
                args.add("--mode");
                args.add("bars");
                args.add("--start");
                args.add(StringUtils.isNotBlank(safe.getStart()) ? safe.getStart().trim() : "20240101");
                if (Objects.nonNull(safe.getLimit()) && safe.getLimit() > 0) {
                    args.add("--limit");
                    args.add(String.valueOf(safe.getLimit()));
                }
                if (StringUtils.isNotBlank(safe.getCodes())) {
                    args.add("--codes");
                    args.add(safe.getCodes().trim());
                }
                args.add("--sleep");
                args.add(String.valueOf(Objects.nonNull(safe.getSleep()) ? safe.getSleep() : 0.35));
            }
            case "A_SHARE_MISSING" -> {
                args.add("--batch");
                args.add(String.valueOf(Objects.nonNull(safe.getBatch()) && safe.getBatch() > 0 ? safe.getBatch() : 80));
                args.add("--rounds");
                args.add(String.valueOf(Objects.nonNull(safe.getRounds()) ? safe.getRounds() : 3));
                args.add("--start");
                args.add(StringUtils.isNotBlank(safe.getStart()) ? safe.getStart().trim() : "20240101");
                args.add("--sleep");
                args.add(String.valueOf(Objects.nonNull(safe.getSleep()) ? safe.getSleep() : 0.18));
            }
            case "TURNOVER" -> {
                if (StringUtils.isNotBlank(safe.getCodes())) {
                    args.add("--codes");
                    args.add(safe.getCodes().trim());
                } else if (Objects.nonNull(safe.getLimit()) && safe.getLimit() > 0) {
                    args.add("--limit");
                    args.add(String.valueOf(safe.getLimit()));
                } else {
                    args.add("--limit");
                    args.add("50");
                }
                args.add("--sleep");
                args.add(String.valueOf(Objects.nonNull(safe.getSleep()) ? safe.getSleep() : 0.2));
            }
            case "COMPANY_PROFILE" -> {
                if (StringUtils.isNotBlank(safe.getCodes())) {
                    args.add("--codes");
                    args.add(safe.getCodes().trim());
                } else if (Objects.nonNull(safe.getLimit()) && safe.getLimit() > 0) {
                    args.add("--limit");
                    args.add(String.valueOf(safe.getLimit()));
                } else {
                    args.add("--all");
                }
                args.add("--sleep");
                args.add(String.valueOf(Objects.nonNull(safe.getSleep()) ? safe.getSleep() : 0.25));
            }
            case "FUNDAMENTALS" -> {
                args.add("--mode");
                args.add(StringUtils.isNotBlank(safe.getMode()) ? safe.getMode().trim() : "all");
                if (StringUtils.isNotBlank(safe.getCodes())) {
                    args.add("--codes");
                    args.add(safe.getCodes().trim());
                } else {
                    args.add("--limit");
                    args.add(String.valueOf(Objects.nonNull(safe.getLimit()) && safe.getLimit() > 0 ? safe.getLimit() : 20));
                }
                args.add("--sleep");
                args.add(String.valueOf(Objects.nonNull(safe.getSleep()) ? safe.getSleep() : 0.8));
            }
            case "INDEX" -> {
                args.add("--start");
                args.add(StringUtils.isNotBlank(safe.getStart()) ? safe.getStart().trim() : "20180101");
                args.add("--sleep");
                args.add(String.valueOf(Objects.nonNull(safe.getSleep()) ? safe.getSleep() : 0.25));
            }
            case "HOT" -> {
                args.add("--sources");
                args.add(StringUtils.isNotBlank(safe.getSources()) ? safe.getSources().trim() : "eastmoney,baidu");
                args.add("--limit");
                args.add(String.valueOf(Objects.nonNull(safe.getLimit()) && safe.getLimit() > 0 ? safe.getLimit() : 50));
            }
            case "NEWS" -> {
                args.add("--sources");
                args.add(StringUtils.isNotBlank(safe.getSources()) ? safe.getSources().trim() : "eastmoney,cls,ths,sina");
                args.add("--limit");
                args.add(String.valueOf(Objects.nonNull(safe.getLimit()) && safe.getLimit() > 0 ? safe.getLimit() : 80));
            }
            case "SECTOR_QUOTE" -> {
                args.add("--mode");
                args.add("quote");
                args.add("--types");
                args.add(StringUtils.isNotBlank(safe.getTypes()) ? safe.getTypes().trim() : "INDUSTRY,CONCEPT,THEME");
                args.add("--sleep");
                args.add(String.valueOf(Objects.nonNull(safe.getSleep()) ? safe.getSleep() : 0.35));
            }
            case "SECTOR_CONS" -> {
                args.add("--mode");
                args.add("cons");
                args.add("--types");
                args.add(StringUtils.isNotBlank(safe.getTypes()) ? safe.getTypes().trim() : "INDUSTRY");
                if (StringUtils.isNotBlank(safe.getCodes())) {
                    args.add("--codes");
                    args.add(safe.getCodes().trim());
                } else {
                    args.add("--limit");
                    args.add(String.valueOf(Objects.nonNull(safe.getLimit()) && safe.getLimit() > 0 ? safe.getLimit() : 10));
                }
                args.add("--sleep");
                args.add(String.valueOf(Objects.nonNull(safe.getSleep()) ? safe.getSleep() : 0.3));
            }
            case "CAPITAL_FLOW" -> {
                args.add("--mode");
                if ("stock".equalsIgnoreCase(safe.getMode())) {
                    args.add("stock");
                } else if ("all".equalsIgnoreCase(safe.getMode())) {
                    args.add("all");
                } else {
                    args.add("flow");
                }
            }
            case "DRAGON_TIGER" -> {
                args.add("--mode");
                args.add("lhb");
            }
            case "LIMIT_UP" -> {
                if (StringUtils.isNotBlank(safe.getStart())) {
                    args.add("--date");
                    args.add(safe.getStart().trim().replace("-", ""));
                }
                args.add("--with-prev");
            }
            case "CLOSE_BUNDLE" -> {
                // 不传 start 时脚本默认近 60 日增量，避免日常一键全量扫指数
                if (StringUtils.isNotBlank(safe.getStart())) {
                    args.add("--start");
                    args.add(safe.getStart().trim());
                }
                args.add("--types");
                args.add(StringUtils.isNotBlank(safe.getTypes()) ? safe.getTypes().trim() : "INDUSTRY,CONCEPT,THEME");
                if (StringUtils.isNotBlank(safe.getMode())) {
                    // mode 可复用为涨停日期 yyyyMMdd
                    args.add("--date");
                    args.add(safe.getMode().trim().replace("-", ""));
                }
                if (Objects.nonNull(safe.getLimit()) && safe.getLimit() > 0) {
                    args.add("--hot-limit");
                    args.add(String.valueOf(safe.getLimit()));
                    args.add("--news-limit");
                    args.add(String.valueOf(Math.max(safe.getLimit(), 80)));
                }
            }
            case "NIGHTLY_REPAIR" -> {
                String expectedDate = StringUtils.isNotBlank(safe.getExpectedDate())
                        ? safe.getExpectedDate().trim()
                        : TradingCalendar.latestTradingDayOnOrBefore(LocalDate.now().minusDays(1)).toString();
                args.add("--expected-date");
                args.add(expectedDate);
                args.add("--start");
                args.add(StringUtils.isNotBlank(safe.getStart()) ? safe.getStart().trim() : "20240101");
                args.add("--bars-batch");
                args.add(String.valueOf(Objects.nonNull(safe.getBatch()) && safe.getBatch() > 0
                        ? safe.getBatch() : 80));
                args.add("--bars-rounds");
                args.add(String.valueOf(Objects.nonNull(safe.getRounds()) && safe.getRounds() >= 0
                        ? safe.getRounds() : 0));
                args.add("--bars-max-minutes");
                args.add("150");
            }
            case "DECISION" -> {
                // Java 内部任务，无脚本参数。
            }
            default -> throw new BusinessException("未实现参数构建: " + type);
        }
        return args;
    }

    private void register(SyncTaskSpec spec) {
        specs.put(spec.getTaskType(), spec);
    }
}
