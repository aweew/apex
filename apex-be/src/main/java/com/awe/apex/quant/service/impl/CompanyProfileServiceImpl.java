package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.CompanyProfileResp;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockCompanyProfile;
import com.awe.apex.quant.mapper.StockBasicMapper;
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
    private StockBasicMapper stockBasicMapper;

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
        // ETF/场内基金无上市公司 F10
        if (MarketCodeUtils.isFundOrEtf(pureCode)) {
            StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getCode, pureCode)
                    .last("LIMIT 1"));
            String name = Objects.nonNull(basic) ? basic.getName() : pureCode;
            syncBasicIndustry(pureCode, "ETF");
            return CompanyProfileResp.builder()
                    .code(pureCode)
                    .aCode(pureCode)
                    .aName(name)
                    .orgName(name)
                    .industryL2("ETF")
                    .industryEm("ETF")
                    .mainBusiness("场内基金/ETF")
                    .orgProfile("场内基金/ETF，无上市公司 F10 概况")
                    .conceptList(List.of("ETF"))
                    .source("fund-skip")
                    .note("ETF/场内基金跳过公司概况")
                    .build();
        }
        StockCompanyProfile existing = stockCompanyProfileMapper.selectOne(Wrappers.<StockCompanyProfile>lambdaQuery()
                .eq(StockCompanyProfile::getCode, pureCode)
                .last("limit 1"));
        if (!forceRefresh && Objects.nonNull(existing) && StringUtils.isNotBlank(existing.getOrgName())) {
            CompanyProfileResp cached = toResp(existing, "本地公司概况");
            syncBasicIndustry(pureCode, cached.getIndustryL2());
            return cached;
        }
        StockCompanyProfile fetched = companyProfileClient.fetch(pureCode);
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(existing)) {
            fetched.setCreateTime(now);
            fetched.setUpdateTime(now);
            stockCompanyProfileMapper.insert(fetched);
            CompanyProfileResp created = toResp(fetched, "已从东财 F10 拉取并落库");
            syncBasicIndustry(pureCode, created.getIndustryL2());
            return created;
        }
        fetched.setId(existing.getId());
        fetched.setCreateTime(existing.getCreateTime());
        fetched.setUpdateTime(now);
        stockCompanyProfileMapper.updateById(fetched);
        CompanyProfileResp refreshed = toResp(fetched, "已刷新东财 F10 公司概况");
        syncBasicIndustry(pureCode, refreshed.getIndustryL2());
        return refreshed;
    }

    private CompanyProfileResp toResp(StockCompanyProfile profile, String note) {
        String industryL2 = resolveIndustryL2(profile.getBoardPath(), profile.getIndustryEm());
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
                .industryL2(industryL2)
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

    /**
     * 从行业路径解析东财二级行业（一级-二级-三级）
     *
     * @param boardPath  行业路径
     * @param industryEm 东财行业兜底
     * @return 二级行业
     */
    private String resolveIndustryL2(String boardPath, String industryEm) {
        if (StringUtils.isNotBlank(boardPath)) {
            String[] parts = boardPath.split("-");
            String level1 = parts.length >= 1 ? parts[0].trim() : "";
            String level2 = parts.length >= 2 ? parts[1].trim() : "";
            // 基础化工下二级过细（塑料/树脂等），口语与持仓题材用「化工」更贴切
            if ("基础化工".equals(level1) || "化工".equals(level1)) {
                return "化工";
            }
            if (StringUtils.isNotBlank(level2)) {
                return level2;
            }
            if (StringUtils.isNotBlank(level1)) {
                return level1;
            }
        }
        return industryEm;
    }

    /**
     * 回写个股基础信息行业为东财二级
     *
     * @param code       证券代码
     * @param industryL2 二级行业
     */
    private void syncBasicIndustry(String code, String industryL2) {
        if (StringUtils.isBlank(code) || StringUtils.isBlank(industryL2)) {
            return;
        }
        StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("limit 1"));
        if (Objects.isNull(basic)) {
            return;
        }
        if (industryL2.equals(basic.getIndustry())) {
            return;
        }
        basic.setIndustry(industryL2);
        basic.setUpdateTime(LocalDateTime.now());
        stockBasicMapper.updateById(basic);
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
