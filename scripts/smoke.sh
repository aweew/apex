#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin:/usr/local/bin:$PATH"
export NO_PROXY='*'
BASE="${APEX_BASE:-http://127.0.0.1:8080/apex}"
fail=0

check_json() {
  local name="$1"
  local expect="$2"
  local file="$3"
  if ! python3 - "$name" "$expect" "$file" <<'PY'
import json,sys
name,expect,path=sys.argv[1],sys.argv[2],sys.argv[3]
raw=open(path).read()
try:
    d=json.loads(raw)
except Exception as e:
    print(f'[失败] {name}：JSON 无效（{e}）{raw[:180]}')
    sys.exit(1)
code=str(d.get('code'))
ok=code==expect
print(f"[{'成功' if ok else '失败'}] {name}：状态码={code}")
if not ok:
    print("响应内容：", raw[:320])
    sys.exit(1)
PY
  then
    fail=1
  fi
}

echo "== Apex 冒烟检查，服务地址：$BASE =="
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

curl --noproxy '*' -sS -m 10 -o "$TMP/health.json" "$BASE/api/health" || true
check_json health 0 "$TMP/health.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/watch.json" "$BASE/api/watchlist" || true
check_json watchlist 0 "$TMP/watch.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/dash.json" "$BASE/api/dashboard/overview" || true
check_json dashboard 0 "$TMP/dash.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/risk.json" "$BASE/api/risk/overview" || true
check_json risk 0 "$TMP/risk.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/check.json" "$BASE/api/daily/checklist" || true
check_json checklist 0 "$TMP/check.json"

python3 - "$BASE" "$TMP/search.json" <<'PY'
import json,sys,urllib.parse,urllib.request
base,path=sys.argv[1],sys.argv[2]
url=base+'/api/stock/search?'+urllib.parse.urlencode({'q':'茅台'})
try:
    with urllib.request.urlopen(url, timeout=20) as r:
        open(path,'wb').write(r.read())
except Exception as e:
    open(path,'w').write(str(e))
PY
check_json search 0 "$TMP/search.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/jobs.json" "$BASE/api/backtest/jobs?limit=5" || true
check_json jobs 0 "$TMP/jobs.json"

curl --noproxy '*' -sS -m 40 -o "$TMP/stock.json" "$BASE/api/stock/600519?barLimit=60&refresh=true" || true
check_json stock 0 "$TMP/stock.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/acc.json" "$BASE/api/paper/account" || true
check_json paper_account 0 "$TMP/acc.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/suggest.json" "$BASE/api/paper/suggest?code=600519" || true
check_json suggest 0 "$TMP/suggest.json"

curl --noproxy '*' -sS -m 40 -X POST -o "$TMP/sync.json" "$BASE/api/data/bars/sync" \
  -H 'Content-Type: application/json' \
  -d '{"codes":["000001"],"beginDate":"2025-01-01","endDate":"2026-08-01"}' || true
check_json sync 0 "$TMP/sync.json"

curl --noproxy '*' -sS -m 40 -X POST -o "$TMP/bt.json" "$BASE/api/backtest/run" \
  -H 'Content-Type: application/json' \
  -d '{"code":"000001","strategyId":"S1","beginDate":"2025-01-01","endDate":"2026-08-01"}' || true
check_json backtest 0 "$TMP/bt.json"

ACC_ID="$(python3 -c "import json;print(json.load(open('$TMP/acc.json'))['data']['id'])")"
curl --noproxy '*' -sS -m 20 -X POST -o "$TMP/order.json" "$BASE/api/paper/order" \
  -H 'Content-Type: application/json' \
  -d "{\"accountId\":$ACC_ID,\"code\":\"000001\",\"side\":\"BUY\",\"quantity\":100}" || true
check_json paper_order 0 "$TMP/order.json"

curl --noproxy '*' -sS -m 30 -X POST -o "$TMP/screener.json" "$BASE/api/screener/run" \
  -H 'Content-Type: application/json' \
  -d '{"groupName":"我的自选","peMax":40,"pbMax":10,"minBars":60,"excludeSt":true,"limit":10}' || true
check_json screener 0 "$TMP/screener.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/cal.json" "$BASE/api/market/calendar" || true
check_json calendar 0 "$TMP/cal.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/board.json" "$BASE/api/market/board?groupName=%E6%88%91%E7%9A%84%E8%87%AA%E9%80%89&limit=5" || true
check_json board 0 "$TMP/board.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/sig.json" "$BASE/api/signal/latest?limit=10&dedupeByCode=true" || true
check_json signals 0 "$TMP/sig.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/sigstats.json" "$BASE/api/signal/stats?days=5" || true
check_json signal_stats 0 "$TMP/sigstats.json"

curl --noproxy '*' -sS -m 90 -X POST -o "$TMP/port.json" "$BASE/api/backtest/portfolio" \
  -H 'Content-Type: application/json' \
  -d '{"strategyId":"S1","beginDate":"2025-01-01","endDate":"2026-08-01","limit":4}' || true
check_json portfolio 0 "$TMP/port.json"

curl --noproxy '*' -sS -m 90 -X POST -o "$TMP/bench.json" \
  "$BASE/api/backtest/benchmark?benchmarkCode=000300" \
  -H 'Content-Type: application/json' \
  -d '{"code":"600519","strategyId":"S1","beginDate":"2025-01-01","endDate":"2026-08-01"}' || true
check_json benchmark 0 "$TMP/bench.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/lb.json" "$BASE/api/backtest/leaderboard?limit=50" || true
check_json leaderboard 0 "$TMP/lb.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/quality.json" "$BASE/api/data/quality?groupName=%E6%88%91%E7%9A%84%E8%87%AA%E9%80%89" || true
check_json data_quality 0 "$TMP/quality.json"

curl --noproxy '*' -sS -m 40 -X POST -o "$TMP/marks.json" "$BASE/api/paper/refresh-marks" || true
check_json refresh_marks 0 "$TMP/marks.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/perf.json" "$BASE/api/paper/performance?benchmarkCode=000300" || true
check_json paper_perf 0 "$TMP/perf.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/exp.json" "$BASE/api/paper/exposure" || true
check_json paper_exposure 0 "$TMP/exp.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/movers.json" "$BASE/api/watchlist/movers?groupName=%E6%88%91%E7%9A%84%E8%87%AA%E9%80%89&threshold=3&limit=5" || true
check_json movers 0 "$TMP/movers.json"

curl --noproxy '*' -sS -m 60 -X POST -o "$TMP/sweep.json" "$BASE/api/backtest/sweep" \
  -H 'Content-Type: application/json' \
  -d '{"code":"600519","beginDate":"2025-01-01","endDate":"2026-08-01","fastPeriods":"5,10","slowPeriods":"20,60"}' || true
check_json sweep 0 "$TMP/sweep.json"

curl --noproxy '*' -sS -m 60 -X POST -o "$TMP/wf.json" "$BASE/api/backtest/walk-forward?inSampleRatio=0.7" \
  -H 'Content-Type: application/json' \
  -d '{"code":"600519","strategyId":"S1","beginDate":"2024-01-01","endDate":"2026-08-01"}' || true
check_json walk_forward 0 "$TMP/wf.json"

JOB_ID="$(python3 -c "import json;print(json.load(open('$TMP/bt.json'))['data']['id'])")"
curl --noproxy '*' -sS -m 20 -o "$TMP/monthly.json" "$BASE/api/backtest/${JOB_ID}/monthly" || true
check_json monthly 0 "$TMP/monthly.json"

curl --noproxy '*' -sS -m 40 -o "$TMP/corr.json" "$BASE/api/watchlist/correlation?groupName=%E6%88%91%E7%9A%84%E8%87%AA%E9%80%89&limit=5&lookback=40" || true
check_json correlation 0 "$TMP/corr.json"

curl --noproxy '*' -sS -m 30 -X POST -o "$TMP/trig.json" "$BASE/api/paper/close-triggered?type=BOTH" || true
check_json close_triggered 0 "$TMP/trig.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/reb.json" "$BASE/api/paper/rebalance-suggest?limit=6" || true
check_json rebalance_suggest 0 "$TMP/reb.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/sbs.json" "$BASE/api/paper/signal-buy-suggest?limit=3&minScore=60" || true
check_json signal_buy_suggest 0 "$TMP/sbs.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/sigf.json" "$BASE/api/signal/latest?limit=5&minScore=60&side=BUY&dedupeByCode=true" || true
check_json signal_filter 0 "$TMP/sigf.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/focus.json" "$BASE/api/focus/today?groupName=%E6%88%91%E7%9A%84%E8%87%AA%E9%80%89" || true
check_json today_focus 0 "$TMP/focus.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/rules.json" "$BASE/api/risk/rules" || true
check_json risk_rules 0 "$TMP/rules.json"

curl --noproxy '*' -sS -m 20 -X POST -o "$TMP/preset.json" "$BASE/api/risk/rules/preset?preset=balanced" || true
check_json risk_preset 0 "$TMP/preset.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/pmonthly.json" "$BASE/api/paper/monthly" || true
check_json paper_monthly 0 "$TMP/pmonthly.json"

curl --noproxy '*' -sS -m 40 -o "$TMP/forward.json" "$BASE/api/signal/forward?lookbackDays=60&horizonDays=5" || true
check_json signal_forward 0 "$TMP/forward.json"

curl --noproxy '*' -sS -m 40 -X POST -o "$TMP/screen2.json" "$BASE/api/screener/run" \
  -H 'Content-Type: application/json' \
  -d '{"groupName":"我的自选","peMax":50,"minBars":60,"excludeSt":true,"minVolumeRatio":1.0,"limit":8}' || true
check_json screener_vol 0 "$TMP/screen2.json"

curl --noproxy '*' -sS -m 40 -X POST -o "$TMP/screen3.json" "$BASE/api/screener/run" \
  -H 'Content-Type: application/json' \
  -d '{"groupName":"我的自选","minBars":60,"excludeSt":true,"maxAtrPct":8,"limit":8}' || true
check_json screener_atr 0 "$TMP/screen3.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/pcorr.json" "$BASE/api/paper/correlation?lookback=40" || true
check_json paper_corr 0 "$TMP/pcorr.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/pcost.json" "$BASE/api/paper/cost" || true
check_json paper_cost 0 "$TMP/pcost.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/kelly.json" "$BASE/api/paper/kelly" || true
check_json paper_kelly 0 "$TMP/kelly.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/fillq.json" "$BASE/api/paper/fill-quality?limit=10" || true
check_json fill_quality 0 "$TMP/fillq.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/gap.json" "$BASE/api/paper/gap-risk" || true
check_json gap_risk 0 "$TMP/gap.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/holdb.json" "$BASE/api/paper/hold-buckets" || true
check_json hold_buckets 0 "$TMP/holdb.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/wdpnl.json" "$BASE/api/paper/weekday-pnl" || true
check_json weekday_pnl 0 "$TMP/wdpnl.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/mc.json" "$BASE/api/paper/monte-carlo?paths=200&horizonDays=20" || true
check_json monte_carlo 0 "$TMP/mc.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/fexp.json" "$BASE/api/paper/factor-exposure" || true
check_json factor_exposure 0 "$TMP/fexp.json"

JOB_ID=$(python3 -c "import json;d=json.load(open('$TMP/jobs.json'));items=d.get('data') or [];print(items[0]['id'] if items else '')" 2>/dev/null || true)
if [[ -n "${JOB_ID}" ]]; then
  curl --noproxy '*' -sS -m 40 -o "$TMP/bstress.json" "$BASE/api/backtest/${JOB_ID}/stress?paths=200&horizonDays=20" || true
  check_json backtest_stress 0 "$TMP/bstress.json"
fi

curl --noproxy '*' -sS -m 30 -o "$TMP/atr.json" "$BASE/api/paper/atr-stops" || true
check_json atr_stops 0 "$TMP/atr.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/rhist.json" "$BASE/api/paper/return-hist" || true
check_json return_hist 0 "$TMP/rhist.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/confl.json" "$BASE/api/signal/confluence?days=5&minStrategies=2" || true
check_json signal_confluence 0 "$TMP/confl.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/volt.json" "$BASE/api/paper/vol-target" || true
check_json vol_target 0 "$TMP/volt.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/tcal.json" "$BASE/api/paper/trade-calendar?days=60" || true
check_json trade_calendar 0 "$TMP/tcal.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/scov.json" "$BASE/api/paper/stop-coverage" || true
check_json stop_coverage 0 "$TMP/scov.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/btgt.json" "$BASE/api/paper/beta-target" || true
check_json beta_target 0 "$TMP/btgt.json"

curl --noproxy '*' -sS -m 30 -o "$TMP/phs.json" "$BASE/api/paper/health-score" || true
check_json paper_health 0 "$TMP/phs.json"

curl --noproxy '*' -sS -m 20 -o "$TMP/eqq.json" "$BASE/api/paper/equity-quality" || true
check_json equity_quality 0 "$TMP/eqq.json"

python3 - "$TMP/bench.json" "$TMP/quality.json" "$TMP/dash.json" <<'PY'
import json,sys
bench=json.load(open(sys.argv[1])).get('data') or {}
q=json.load(open(sys.argv[2])).get('data') or {}
dash=json.load(open(sys.argv[3])).get('data') or {}
ok=True
if not bench.get('benchmarkEquities'):
    print('[警告] 基准权益曲线为空')
else:
    print('[成功] 基准叠加曲线数量=', len(bench.get('benchmarkEquities') or []))
if q.get('slaLevel') not in ('GREEN','YELLOW','RED'):
    print('[失败] 数据服务等级缺失'); ok=False
else:
    print('[成功] 数据服务等级=', q.get('slaLevel'), '，行情覆盖率=', q.get('quoteCoverage'), '，日线就绪覆盖率=', q.get('barsReadyCoverage'))
m=(dash.get('paperMetrics') or {})
if 'maxDrawdown' not in m and 'sharpe' not in m:
    print('[警告] 模拟盘指标缺少最大回撤或夏普比率，可能需要重启')
else:
    print('[成功] 模拟盘盯市指标，最大回撤=', m.get('maxDrawdown'), '，夏普比率=', m.get('sharpe'))
sys.exit(0 if ok else 1)
PY

python3 - "$TMP/stock.json" <<'PY'
import json,sys
d=json.load(open(sys.argv[1]))
b=(d.get('data') or {}).get('basic') or {}
need=['name','peTtm','pb','industry','totalMv']
bad=[k for k in need if b.get(k) in (None,'')]
print('[成功] 估值字段完整' if not bad else f"[警告] 缺少字段 {bad}，数据源={b.get('source')}")
if bad:
    sys.exit(2)
print('名称=', b.get('name'), '，市盈率=', b.get('peTtm'), '，市净率=', b.get('pb'), '，行业=', b.get('industry'))
PY

if [[ $fail -ne 0 ]]; then
  echo "冒烟检查失败"
  exit 1
fi
echo "冒烟检查通过"
