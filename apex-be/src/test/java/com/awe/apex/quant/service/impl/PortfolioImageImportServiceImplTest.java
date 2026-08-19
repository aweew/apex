package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.SpringUtils;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.domain.dto.PortfolioImageImportPreviewResp;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.IPortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PortfolioImageImportServiceImplTest {

    private static ApplicationContext originalApplicationContext;

    private final IPortfolioService portfolioService = mock(IPortfolioService.class);
    private final StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
    private final KimiChatClient kimiChatClient = mock(KimiChatClient.class);
    private final PortfolioImageImportServiceImpl service = new PortfolioImageImportServiceImpl();

    @BeforeAll
    static void setUpJsonUtils() {
        originalApplicationContext = SpringUtils.getApplicationContext();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper().findAndRegisterModules());
        new SpringUtils().setApplicationContext(applicationContext);
    }

    @AfterAll
    static void restoreJsonUtils() {
        new SpringUtils().setApplicationContext(originalApplicationContext);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "kimiChatClient", kimiChatClient);
        when(portfolioService.detail(8L)).thenReturn(PortfolioSummaryResp.builder().editable(true).build());
        when(kimiChatClient.available()).thenReturn(true);
    }

    @Test
    void rejectsUnsupportedOrOversizedImages() {
        MockMultipartFile pdf = new MockMultipartFile("file", "holding.pdf", "application/pdf", "pdf".getBytes());
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "holding.png", "image/png", new byte[8 * 1024 * 1024 + 1]);
        MockMultipartFile mismatched = new MockMultipartFile(
                "file", "holding.jpg", "image/jpeg",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        BusinessException typeException = assertThrows(BusinessException.class,
                () -> service.preview(8L, pdf));
        BusinessException sizeException = assertThrows(BusinessException.class,
                () -> service.preview(8L, oversized));
        BusinessException contentException = assertThrows(BusinessException.class,
                () -> service.preview(8L, mismatched));

        assertEquals("仅支持 PNG、JPEG 或 WebP 截图", typeException.getMessage());
        assertEquals("截图不能超过 8 MB", sizeException.getMessage());
        assertEquals("截图文件内容与图片格式不一致", contentException.getMessage());
    }

    @Test
    void rejectsPortfolioWithoutEditPermissionBeforeCallingVisionModel() {
        when(portfolioService.detail(9L)).thenReturn(PortfolioSummaryResp.builder().editable(false).build());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.preview(9L, imageFile()));

        assertEquals("无权修改该组合", exception.getMessage());
        verify(portfolioService).detail(9L);
        verifyNoMoreInteractions(kimiChatClient);
    }

    @Test
    void parsesFencedJsonAndResolvesCodeAndExactNameWithoutPersistence() {
        when(kimiChatClient.chatImage(anyString(), anyString(), anyString(), any(), anyInt())).thenReturn("""
                ```json
                {
                  "holdings": [
                    {"code":"600519","name":"贵州茅台","quantity":"100","costPrice":"1488.50","marketValue":"160000","confidence":0.98},
                    {"name":"九洲药业","quantity":"300","costPrice":"38.20","confidence":0.91}
                  ],
                  "warnings": []
                }
                ```
                """);
        when(stockBasicMapper.selectList(any())).thenReturn(
                List.of(StockBasic.builder().code("600519").name("贵州茅台").build()),
                List.of(StockBasic.builder().code("603456").name("九洲药业").build()));

        PortfolioImageImportPreviewResp result = service.preview(8L, imageFile());

        assertEquals(2, result.getRows().size());
        assertEquals("600519", result.getRows().get(0).getSecurity());
        assertEquals("603456", result.getRows().get(1).getSecurity());
        assertEquals("38.20", result.getRows().get(1).getCostPrice());
        assertTrue(result.getRows().get(0).getValid());
        assertTrue(result.getRows().get(1).getValid());
        verify(portfolioService).detail(8L);
        verifyNoMoreInteractions(portfolioService);
    }

    @Test
    void returnsEditableErrorsForInvalidAndDuplicateRows() {
        when(kimiChatClient.chatImage(anyString(), anyString(), anyString(), any(), anyInt())).thenReturn("""
                {
                  "holdings": [
                    {"code":"600519","name":"贵州茅台","quantity":"100","costPrice":"1488.50","confidence":0.60},
                    {"code":"600519","name":"贵州茅台","quantity":"0","costPrice":"1488.50","confidence":0.99},
                    {"name":"模糊证券","quantity":"200","costPrice":"10","confidence":0.90}
                  ]
                }
                """);
        when(stockBasicMapper.selectList(any())).thenReturn(
                List.of(StockBasic.builder().code("600519").name("贵州茅台").build()),
                List.of(StockBasic.builder().code("600519").name("贵州茅台").build()),
                List.of());

        PortfolioImageImportPreviewResp result = service.preview(8L, imageFile());

        assertFalse(result.getRows().get(0).getValid());
        assertTrue(result.getRows().get(0).getWarning().contains("重复证券"));
        assertTrue(result.getRows().get(0).getWarning().contains("置信度较低"));
        assertFalse(result.getRows().get(1).getValid());
        assertTrue(result.getRows().get(1).getWarning().contains("数量必须为正整数"));
        assertFalse(result.getRows().get(2).getValid());
        assertTrue(result.getRows().get(2).getWarning().contains("无法精确匹配证券"));
    }

    @Test
    void rejectsUnusableModelResponse() {
        when(kimiChatClient.chatImage(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn("截图太模糊了");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.preview(8L, imageFile()));

        assertEquals("截图识别结果无法解析，请换一张更清晰的截图", exception.getMessage());
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "holding.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    }
}
