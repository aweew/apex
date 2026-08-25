package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.MarketOpinionRadarResp;
import com.awe.apex.quant.domain.entity.MarketActor;
import com.awe.apex.quant.domain.entity.MarketOpinion;
import com.awe.apex.quant.mapper.MarketActorMapper;
import com.awe.apex.quant.mapper.MarketOpinionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketOpinionServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MarketOpinion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MarketActor.class);
    }

    @Test
    void buildsConsensusAndDivergenceFromTraceableInstitutionViews() {
        MarketOpinionMapper marketOpinionMapper = mock(MarketOpinionMapper.class);
        MarketActorMapper marketActorMapper = mock(MarketActorMapper.class);
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of(
                opinion("INSTITUTION", "中信证券", "买入", "000001", "平安银行", "银行", "买入"),
                opinion("INSTITUTION", "华泰证券", "增持", "000001", "平安银行", "银行", "看多"),
                opinion("INSTITUTION", "国泰海通", "减持", "600000", "浦发银行", "银行", "减持"),
                opinion("ACTIVE_SEAT", "国盛证券宁波桑田路证券营业部", "活跃席位", null, null, null, "净买入 2.01 亿元")
        ));
        when(marketActorMapper.selectList(any())).thenReturn(List.of(
                MarketActor.builder()
                        .actorName("边风炜")
                        .actorType("KOL")
                        .platform("RSS")
                        .sourceStatus("PENDING_VERIFICATION")
                        .sourceNote("待核验官方公开源")
                        .build()
        ));
        MarketOpinionServiceImpl service = new MarketOpinionServiceImpl();
        ReflectionTestUtils.setField(service, "marketOpinionMapper", marketOpinionMapper);
        ReflectionTestUtils.setField(service, "marketActorMapper", marketActorMapper);

        MarketOpinionRadarResp response = service.radar();

        assertEquals(3, response.getInstitutionViews().size());
        assertEquals(1, response.getTraderSeatViews().size());
        assertEquals("宁波桑田路", response.getTraderSeatViews().get(0).getActorName());
        assertEquals(1, response.getKolSources().size());
        assertTrue(response.getKolSourceStatus().contains("待核验"));
        assertTrue(response.getConsensus().contains("偏积极"));
        assertTrue(response.getDivergence().contains("银行"));
    }

    private MarketOpinion opinion(String opinionType, String subjectName, String direction,
                                  String relatedCode, String relatedName, String topic, String summary) {
        return MarketOpinion.builder()
                .opinionType(opinionType)
                .source("EASTMONEY")
                .externalId(subjectName + summary)
                .subjectName(subjectName)
                .title(summary)
                .summary(summary)
                .direction(direction)
                .actorName("ACTIVE_SEAT".equals(opinionType) ? "宁波桑田路" : null)
                .relatedCode(relatedCode)
                .relatedName(relatedName)
                .topic(topic)
                .netAmount(new BigDecimal("201000000"))
                .publishedAt(LocalDateTime.now())
                .snapshotTime(LocalDateTime.now())
                .url("https://example.test/source")
                .build();
    }
}
