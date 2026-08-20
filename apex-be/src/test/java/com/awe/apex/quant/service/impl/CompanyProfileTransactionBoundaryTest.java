package com.awe.apex.quant.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompanyProfileTransactionBoundaryTest {

    @Test
    void companyProfileSyncUsesAnIndependentTransaction() throws NoSuchMethodException {
        Method queryMethod = CompanyProfileServiceImpl.class.getMethod("query", String.class, boolean.class);

        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(queryMethod, Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertArrayEquals(new Class<?>[]{Exception.class}, transactional.rollbackFor());
    }
}
