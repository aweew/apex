USE apex;

ALTER TABLE stock_company_profile ADD COLUMN revenue_report_date DATE NULL;
ALTER TABLE stock_company_profile ADD COLUMN revenue_items TEXT NULL;
ALTER TABLE stock_company_profile ADD COLUMN top_profit_business VARCHAR(128) NULL;
ALTER TABLE stock_company_profile ADD COLUMN top_profit_ratio DECIMAL(10, 4) NULL;
ALTER TABLE stock_company_profile MODIFY COLUMN main_business TEXT NULL;
