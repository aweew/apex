import akshare as ak

code = "000001"
tests = []

def run(name, fn):
    try:
        df = fn()
        cols = list(df.columns) if df is not None else []
        n = 0 if df is None else len(df)
        sample = None
        if df is not None and n:
            sample = {str(k): str(v) for k, v in df.iloc[0].head(8).to_dict().items()}
        tests.append((name, "OK", n, cols[:25], sample))
    except Exception as e:
        tests.append((name, "FAIL", 0, str(e)[:200], None))

run("indicator", lambda: ak.stock_financial_analysis_indicator(symbol=code))
run("indicator_em", lambda: ak.stock_financial_analysis_indicator_em(symbol=code))
run("abstract", lambda: ak.stock_financial_abstract(symbol=code))
run("abstract_ths", lambda: ak.stock_financial_abstract_ths(symbol=code))
run("sina_profit", lambda: ak.stock_financial_report_sina(stock=code, symbol="利润表"))
run("sina_balance", lambda: ak.stock_financial_report_sina(stock=code, symbol="资产负债表"))
run("sina_cash", lambda: ak.stock_financial_report_sina(stock=code, symbol="现金流量表"))
run("em_profit", lambda: ak.stock_profit_sheet_by_report_em(symbol="SZ000001"))

for name, status, n, cols, sample in tests:
    print("=" * 60)
    print(name, status, "rows=", n)
    print("cols=", cols)
    print("sample=", sample)
