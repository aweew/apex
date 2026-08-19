package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.ai.dto.HoldingImageRecognitionResp;
import com.awe.apex.quant.ai.dto.HoldingImageRecognitionRow;
import com.awe.apex.quant.domain.dto.PortfolioImageImportPreviewResp;
import com.awe.apex.quant.domain.dto.PortfolioImageImportRowResp;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IPortfolioImageImportService;
import com.awe.apex.quant.service.IPortfolioService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 持仓截图识别服务实现
 */
@Service
public class PortfolioImageImportServiceImpl implements IPortfolioImageImportService {

    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.80");
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final String SYSTEM_PROMPT = """
            你是券商持仓截图识别器。只读取截图中明确可见的持仓表，不推测、不补全证券代码。
            严格返回一个 JSON 对象，不要 Markdown。字段为 holdings、totalMarketValue、warnings。
            holdings 每项包含 code、name、quantity、costPrice、marketValue、confidence；看不清的字段返回空字符串。
            confidence 为 0 到 1。warnings 只描述截图裁切、遮挡、表头歧义或疑似漏行等整体问题。
            数字不要带千分位、货币符号或单位。不要返回账户姓名、账号、总资产或其他隐私字段。
            """;
    private static final String USER_PROMPT = "识别截图中每一条股票持仓。数量必须取持仓数量，不要误取可用数量；成本价必须取持仓成本或摊薄成本，不要误取现价。";

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private KimiChatClient kimiChatClient;

    /**
     * 识别持仓截图并返回可编辑预览，不保存持仓
     *
     * @param portfolioId 组合ID
     * @param image       持仓截图
     * @return 识别预览
     */
    @Override
    public PortfolioImageImportPreviewResp preview(Long portfolioId, MultipartFile image) {
        PortfolioSummaryResp portfolio = portfolioService.detail(portfolioId);
        if (Objects.isNull(portfolio) || !Boolean.TRUE.equals(portfolio.getEditable())) {
            throw new BusinessException("无权修改该组合");
        }
        validateImage(image);
        if (!kimiChatClient.available()) {
            throw new BusinessException("未配置截图识别模型，请联系管理员");
        }

        String rawResult;
        try {
            rawResult = kimiChatClient.chatImage(
                    SYSTEM_PROMPT, USER_PROMPT, image.getContentType(), image.getBytes(), 1800);
        } catch (IOException exception) {
            throw new BusinessException("读取截图失败，请重新选择文件", exception);
        }
        HoldingImageRecognitionResp recognition = parseRecognition(rawResult);
        List<PortfolioImageImportRowResp> rows = new ArrayList<>();
        for (HoldingImageRecognitionRow holding : recognition.getHoldings()) {
            rows.add(normalizeRow(holding));
        }
        markDuplicates(rows);
        return PortfolioImageImportPreviewResp.builder()
                .rows(rows)
                .totalMarketValue(recognition.getTotalMarketValue())
                .warnings(Objects.nonNull(recognition.getWarnings()) ? recognition.getWarnings() : new ArrayList<>())
                .build();
    }

    private void validateImage(MultipartFile image) {
        if (Objects.isNull(image) || image.isEmpty()) {
            throw new BusinessException("请选择持仓截图");
        }
        String contentType = StringUtils.trim(image.getContentType());
        if (StringUtils.isBlank(contentType) || !SUPPORTED_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("仅支持 PNG、JPEG 或 WebP 截图");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("截图不能超过 8 MB");
        }
        try {
            byte[] bytes = image.getBytes();
            boolean png = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4E && bytes[3] == 0x47;
            boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8
                    && bytes[2] == (byte) 0xFF;
            boolean webp = bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49
                    && bytes[2] == 0x46 && bytes[3] == 0x46 && bytes[8] == 0x57
                    && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
            String imageContentType = image.getContentType().toLowerCase();
            boolean contentMatches = ("image/png".equals(imageContentType) && png)
                    || ("image/jpeg".equals(imageContentType) && jpeg)
                    || ("image/webp".equals(imageContentType) && webp);
            if (!contentMatches) {
                throw new BusinessException("截图文件内容与图片格式不一致");
            }
        } catch (IOException exception) {
            throw new BusinessException("读取截图失败，请重新选择文件", exception);
        }
    }

    private HoldingImageRecognitionResp parseRecognition(String rawResult) {
        if (StringUtils.isBlank(rawResult)) {
            throw new BusinessException("截图识别失败，请稍后重试");
        }
        int start = rawResult.indexOf('{');
        int end = rawResult.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException("截图识别结果无法解析，请换一张更清晰的截图");
        }
        try {
            HoldingImageRecognitionResp recognition = JsonUtils.parseObject(
                    rawResult.substring(start, end + 1), HoldingImageRecognitionResp.class);
            if (Objects.isNull(recognition) || CollUtil.isEmpty(recognition.getHoldings())) {
                throw new BusinessException("截图中未识别到持仓，请确认截图包含完整持仓表");
            }
            return recognition;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("截图识别结果无法解析，请换一张更清晰的截图", exception);
        }
    }

    private PortfolioImageImportRowResp normalizeRow(HoldingImageRecognitionRow holding) {
        String rawCode = StringUtils.trim(holding.getCode());
        String rawName = StringUtils.trim(holding.getName());
        String security = StringUtils.isNotBlank(rawCode) ? rawCode : rawName;
        PortfolioImageImportRowResp row = PortfolioImageImportRowResp.builder()
                .security(security)
                .quantity(cleanNumber(holding.getQuantity()))
                .costPrice(cleanNumber(holding.getCostPrice()))
                .marketValue(parseDecimal(holding.getMarketValue()))
                .confidence(holding.getConfidence())
                .valid(true)
                .warning("")
                .build();

        List<StockBasic> matchedStocks;
        String normalizedCode = MarketCodeUtils.normalizeHoldingCode(rawCode);
        if (StringUtils.isNotBlank(rawCode) && StringUtils.isNotBlank(normalizedCode)
                && normalizedCode.matches("\\d{6}")) {
            matchedStocks = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getCode, normalizedCode)
                    .last("LIMIT 2"));
        } else if (StringUtils.isNotBlank(rawName)) {
            matchedStocks = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getName, rawName)
                    .last("LIMIT 2"));
        } else {
            matchedStocks = new ArrayList<>();
        }
        if (matchedStocks.size() != 1 || StringUtils.isBlank(matchedStocks.get(0).getCode())) {
            addWarning(row, "无法精确匹配证券，请补充正确代码或完整名称", true);
        } else {
            StockBasic stock = matchedStocks.get(0);
            row.setSecurity(stock.getCode());
            row.setCode(stock.getCode());
            row.setName(stock.getName());
            if (StringUtils.isNotBlank(rawName) && !Objects.equals(rawName, stock.getName())) {
                addWarning(row, "证券代码与名称不一致，请核对", true);
            }
        }

        try {
            int quantity = Integer.parseInt(row.getQuantity());
            if (quantity <= 0) {
                addWarning(row, "数量必须为正整数", true);
            }
        } catch (Exception exception) {
            addWarning(row, "数量必须为正整数", true);
        }
        if (StringUtils.isNotBlank(row.getCostPrice())) {
            BigDecimal costPrice = parseDecimal(row.getCostPrice());
            if (Objects.isNull(costPrice) || costPrice.signum() <= 0) {
                addWarning(row, "成本价必须大于 0", true);
            }
        } else {
            addWarning(row, "未识别到成本价，请确认是否留空", false);
        }
        if (Objects.nonNull(row.getConfidence()) && row.getConfidence().compareTo(LOW_CONFIDENCE) < 0) {
            addWarning(row, "置信度较低，请重点复核", false);
        }
        return row;
    }

    private void markDuplicates(List<PortfolioImageImportRowResp> rows) {
        Set<String> seenCodes = new HashSet<>();
        Set<String> duplicateCodes = new HashSet<>();
        for (PortfolioImageImportRowResp row : rows) {
            if (StringUtils.isNotBlank(row.getCode()) && !seenCodes.add(row.getCode())) {
                duplicateCodes.add(row.getCode());
            }
        }
        for (PortfolioImageImportRowResp row : rows) {
            if (StringUtils.isNotBlank(row.getCode()) && duplicateCodes.contains(row.getCode())) {
                addWarning(row, "存在重复证券", true);
            }
        }
    }

    private String cleanNumber(String value) {
        return StringUtils.isBlank(value) ? "" : value.trim().replace(",", "");
    }

    private BigDecimal parseDecimal(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(cleanNumber(value));
        } catch (Exception exception) {
            return null;
        }
    }

    private void addWarning(PortfolioImageImportRowResp row, String warning, boolean blocking) {
        row.setWarning(StringUtils.isBlank(row.getWarning()) ? warning : row.getWarning() + "；" + warning);
        if (blocking) {
            row.setValid(false);
        }
    }
}
