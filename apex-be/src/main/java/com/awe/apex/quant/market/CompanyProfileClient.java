package com.awe.apex.quant.market;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.entity.StockCompanyProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
/**
 * 东财 F10 公司概况客户端
 */
@Slf4j
@Component
public class CompanyProfileClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 拉取公司概况
     *
     * @param code 证券代码
     * @return 概况实体
     */
    public StockCompanyProfile fetch(String code) {
        String pureCode = MarketCodeUtils.normalizeCode(code);
        if (StringUtils.isBlank(pureCode)) {
            throw new BusinessException("证券代码为空");
        }
        String url = "https://datacenter.eastmoney.com/securities/api/data/v1/get"
                + "?reportName=RPT_F10_ORG_BASICINFO"
                + "&columns=ALL"
                + "&quoteColumns="
                + "&filter=(SECURITY_CODE%3D%22" + pureCode + "%22)"
                + "&pageNumber=1&pageSize=1&sortTypes=&sortColumns=&source=HSF10&client=PC";
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(15000)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://emweb.securities.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                throw new BusinessException("公司概况接口无响应");
            }
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                throw new BusinessException("公司概况拉取失败: " + root.path("message").asText("unknown"));
            }
            JsonNode rows = root.path("result").path("data");
            if (!rows.isArray() || rows.isEmpty()) {
                throw new BusinessException("未找到公司概况: " + pureCode);
            }
            JsonNode row = rows.get(0);
            LocalDateTime now = LocalDateTime.now();
            String boardPath = joinPath(
                    text(row, "BOARD_NAME_1LEVEL"),
                    text(row, "BOARD_NAME_2LEVEL"),
                    text(row, "BOARD_NAME_3LEVEL")
            );
            return StockCompanyProfile.builder()
                    .code(pureCode)
                    .orgName(text(row, "ORG_NAME"))
                    .orgNameEn(text(row, "ORG_NAME_EN"))
                    .formerName(normalizeFormer(text(row, "FORMERNAME")))
                    .aCode(pureCode)
                    .aName(firstNonBlank(text(row, "STR_NAMEA"), text(row, "SECURITY_NAME_ABBR")))
                    .region(firstNonBlank(text(row, "PROVINCE"), text(row, "REGIONBK")))
                    .areaBoard(text(row, "AREA_BOARD_NAME"))
                    .industryEm(text(row, "EM2016"))
                    .industryCsrc(text(row, "CSRC_INDUSTRY_NAME"))
                    .boardPath(boardPath)
                    .concepts(text(row, "BLGAINIAN"))
                    .chairman(text(row, "CHAIRMAN"))
                    .legalPerson(text(row, "LEGAL_PERSON"))
                    .president(text(row, "PRESIDENT"))
                    .secretary(text(row, "SECRETARY"))
                    .controlHolder(text(row, "CONTROL_HOLDER"))
                    .controlRatio(text(row, "CONTROL_DIRECT_RATIO"))
                    .realController(text(row, "REAL_CONTROLER"))
                    .realControllerRatio(text(row, "REAL_DIRECT_RATIO"))
                    .orgForm(text(row, "ORG_FORM"))
                    .foundDate(parseDate(text(row, "FOUND_DATE")))
                    .listDate(parseDate(text(row, "LISTING_DATE")))
                    .regCapital(toDecimal(text(row, "REG_CAPITAL")))
                    .issuePrice(toDecimal(text(row, "ISSUE_PRICE")))
                    .employeeNum(toInt(text(row, "TOTAL_NUM")))
                    .managerNum(toInt(firstNonBlank(text(row, "TATOLNUMBER"), text(row, "TOTALNUMBER"))))
                    .mainBusiness(text(row, "MAIN_BUSINESS"))
                    .orgProfile(trimProfile(text(row, "ORG_PROFIE")))
                    .orgHighlight(text(row, "ORG_PROFILE"))
                    .businessScope(text(row, "BUSINESS_SCOPE"))
                    .website(text(row, "ORG_WEB"))
                    .email(text(row, "ORG_EMAIL"))
                    .phone(text(row, "ORG_TEL"))
                    .fax(text(row, "ORG_FAX"))
                    .officeAddress(text(row, "ADDRESS"))
                    .regAddress(text(row, "REG_ADDRESS"))
                    .regNum(text(row, "REG_NUM"))
                    .tradeMarket(text(row, "TRADE_MARKET"))
                    .payload(row.toString())
                    .source("eastmoney-f10")
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("拉取公司概况失败，code={}, err={}", pureCode, ex.getMessage());
            throw new BusinessException("拉取公司概况失败: " + ex.getMessage(), ex);
        }
    }

    private String text(JsonNode row, String field) {
        JsonNode node = row.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (StringUtils.isBlank(value) || "--".equals(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String a, String b) {
        if (StringUtils.isNotBlank(a)) {
            return a;
        }
        return b;
    }

    private String joinPath(String a, String b, String c) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(a)) {
            sb.append(a);
        }
        if (StringUtils.isNotBlank(b)) {
            if (!sb.isEmpty()) {
                sb.append('-');
            }
            sb.append(b);
        }
        if (StringUtils.isNotBlank(c)) {
            if (!sb.isEmpty()) {
                sb.append('-');
            }
            sb.append(c);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String normalizeFormer(String former) {
        if (StringUtils.isBlank(former)) {
            return null;
        }
        return former.replace('→', '>').replace("->", ">");
    }

    private String trimProfile(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return text.replace('\u00a0', ' ').trim();
    }

    private LocalDate parseDate(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String day = text.length() >= 10 ? text.substring(0, 10) : text;
        try {
            return LocalDate.parse(day, DAY);
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal toDecimal(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", "").trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer toInt(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", "").trim()).intValue();
        } catch (Exception ex) {
            return null;
        }
    }
}
