package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.CompanyProfileResp;
import com.awe.apex.quant.domain.entity.StockCompanyProfile;
import com.awe.apex.quant.mapper.StockCompanyProfileMapper;
import com.awe.apex.quant.market.CompanyProfileClient;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.ICompanyProfileService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 公司概况服务实现
 */
@Service
public class CompanyProfileServiceImpl implements ICompanyProfileService {

    @Resource
    private StockCompanyProfileMapper stockCompanyProfileMapper;

    @Resource
    private CompanyProfileClient companyProfileClient;

    /**
     * 查询公司概况（本地优先；缺失或 forceRefresh 时拉取东财）
     *
     * @param code         证券代码
     * @param forceRefresh 是否强制刷新
     * @return 概况
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanyProfileResp query(String code, boolean forceRefresh) {
        String pureCode = MarketCodeUtils.normalizeCode(code);
        if (StringUtils.isBlank(pureCode)) {
            throw new BusinessException("证券代码为空");
        }
        StockCompanyProfile existing = stockCompanyProfileMapper.selectOne(Wrappers.<StockCompanyProfile>lambdaQuery()
                .eq(StockCompanyProfile::getCode, pureCode)
                .last("limit 1"));
        if (!forceRefresh && Objects.nonNull(existing) && StringUtils.isNotBlank(existing.getOrgName())) {
            return toResp(existing, "本地公司概况");
        }
        StockCompanyProfile fetched = companyProfileClient.fetch(pureCode);
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(existing)) {
            fetched.setCreateTime(now);
            fetched.setUpdateTime(now);
            stockCompanyProfileMapper.insert(fetched);
            return toResp(fetched, "已从东财 F10 拉取并落库");
        }
        fetched.setId(existing.getId());
        fetched.setCreateTime(existing.getCreateTime());
        fetched.setUpdateTime(now);
        stockCompanyProfileMapper.updateById(fetched);
        return toResp(fetched, "已刷新东财 F10 公司概况");
    }

    private CompanyProfileResp toResp(StockCompanyProfile profile, String note) {
        return CompanyProfileResp.builder()
                .code(profile.getCode())
                .orgName(profile.getOrgName())
                .orgNameEn(profile.getOrgNameEn())
                .formerName(profile.getFormerName())
                .aCode(profile.getACode())
                .aName(profile.getAName())
                .region(profile.getRegion())
                .areaBoard(profile.getAreaBoard())
                .industryEm(profile.getIndustryEm())
                .industryCsrc(profile.getIndustryCsrc())
                .boardPath(profile.getBoardPath())
                .conceptList(splitCsv(profile.getConcepts()))
                .businessTags(splitBusinessTags(profile.getMainBusiness()))
                .chairman(profile.getChairman())
                .legalPerson(profile.getLegalPerson())
                .president(profile.getPresident())
                .secretary(profile.getSecretary())
                .controlHolder(profile.getControlHolder())
                .controlRatio(profile.getControlRatio())
                .realController(profile.getRealController())
                .realControllerRatio(profile.getRealControllerRatio())
                .orgForm(profile.getOrgForm())
                .foundDate(profile.getFoundDate())
                .listDate(profile.getListDate())
                .regCapital(profile.getRegCapital())
                .regCapitalText(formatRegCapital(profile.getRegCapital()))
                .issuePrice(profile.getIssuePrice())
                .employeeNum(profile.getEmployeeNum())
                .managerNum(profile.getManagerNum())
                .mainBusiness(profile.getMainBusiness())
                .orgProfile(profile.getOrgProfile())
                .orgHighlight(profile.getOrgHighlight())
                .businessScope(profile.getBusinessScope())
                .website(profile.getWebsite())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .fax(profile.getFax())
                .officeAddress(profile.getOfficeAddress())
                .regAddress(profile.getRegAddress())
                .regNum(profile.getRegNum())
                .tradeMarket(profile.getTradeMarket())
                .source(profile.getSource())
                .updateTime(profile.getUpdateTime())
                .note(note)
                .build();
    }

    private List<String> splitCsv(String text) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return list;
        }
        String[] parts = text.split("[,，;；]");
        for (String part : parts) {
            String item = part == null ? null : part.trim();
            if (StringUtils.isNotBlank(item)) {
                list.add(item);
            }
        }
        return list;
    }

    private List<String> splitBusinessTags(String mainBusiness) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isBlank(mainBusiness)) {
            return list;
        }
        String[] parts = mainBusiness.split("[,，、/|]");
        for (String part : parts) {
            String item = part == null ? null : part.trim();
            if (StringUtils.isNotBlank(item) && list.size() < 8) {
                list.add(item);
            }
        }
        return list;
    }

    private String formatRegCapital(BigDecimal wan) {
        if (Objects.isNull(wan)) {
            return null;
        }
        // 东财 REG_CAPITAL 单位为万元
        BigDecimal yi = wan.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
        if (yi.compareTo(BigDecimal.ONE) >= 0) {
            return yi.stripTrailingZeros().toPlainString() + "亿";
        }
        return wan.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "万";
    }
}
