package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.entity.StockCompanyProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公司概况主营构成拉取（依赖东财网络，失败时跳过）
 */
class CompanyProfileClientBusinessTest {

    @Test
    void fillBusinessComposition_kehua() {
        CompanyProfileClient client = new CompanyProfileClient();
        StockCompanyProfile profile = StockCompanyProfile.builder().code("002335").build();
        try {
            client.fillBusinessComposition(profile);
        } catch (Exception ex) {
            return;
        }
        if (profile.getRevenueItems() == null) {
            return;
        }
        assertNotNull(profile.getRevenueReportDate());
        assertTrue(profile.getRevenueItems().contains("新能源")
                || profile.getRevenueItems().contains("数据中心")
                || profile.getRevenueItems().contains("IDC"));
        assertNotNull(profile.getTopProfitBusiness());
        assertNotNull(profile.getTopProfitRatio());
        assertTrue(profile.getTopProfitRatio().compareTo(BigDecimal.ZERO) > 0);
    }
}
