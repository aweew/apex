package com.awe.apex.common.util;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ArrayUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Json工具类
 *
 * @author Awe
 * @since 2025/12/8 16:04
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = SpringUtils.getBean(ObjectMapper.class);

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * 对象转json字符串
     *
     * @param object 任意对象
     * @return json字符串
     */
    public static String toJsonString(Object object) {
        if (Objects.isNull(object)) {
            return null;
        }

        // // 过滤不需要ToString的字段
        // Set<String> exceptFieldNames = new HashSet<>();
        // Field[] fields = object.getClass().getDeclaredFields();
        // for (Field field : fields) {
        //     ToJsonStringExclude annotation = field.getAnnotation(ToJsonStringExclude.class);
        //     if (Objects.nonNull(annotation)) {
        //         exceptFieldNames.add(field.getName());
        //     }
        // }
        // if (!CollectionUtils.isEmpty(exceptFieldNames)) {
        //     SimpleFilterProvider filterProvider = new SimpleFilterProvider()
        //             .addFilter("JsonExcludeFilter", SimpleBeanPropertyFilter.serializeAllExcept(exceptFieldNames));
        //     OBJECT_MAPPER.setFilterProvider(filterProvider);
        // }

        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 对象转json字符串
     *
     * @param object 任意对象
     * @return json字符串
     */
    public static String toRawJsonString(Object object) {
        if (Objects.isNull(object)) {
            return null;
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonFactory jsonFactory = new JsonFactory(objectMapper);
            StringWriter writer = new StringWriter();
            JsonGenerator generator = jsonFactory.createGenerator(writer);
            generator.writeRawValue(OBJECT_MAPPER.writeValueAsString(object));
            generator.close();
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 字符串转指定对象
     *
     * @param text  字符串文本
     * @param clazz 要转的对象CLASS类
     * @param <T>   指定对象
     * @return 指定对象
     */
    public static <T> T parseObject(String text, Class<T> clazz) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 字节流转指定对象
     *
     * @param bytes 字节流
     * @param clazz 要转的对象CLASS类
     * @param <T>   指定对象
     * @return 指定对象
     */
    public static <T> T parseObject(byte[] bytes, Class<T> clazz) {
        if (ArrayUtil.isEmpty(bytes)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 字符串转指定对象
     *
     * @param text          字符串
     * @param typeReference 要转的对象类型
     * @param <T>           指定对象
     * @return 指定对象
     */
    public static <T> T parseObject(String text, TypeReference<T> typeReference) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, typeReference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 字符串转指定对象数组
     *
     * @param text  字符串
     * @param clazz 要转的对象类型
     * @param <T>   指定对象
     * @return 指定对象数组
     */
    public static <T> List<T> parseArray(String text, Class<T> clazz) {
        if (StringUtils.isEmpty(text)) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(text, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Dict parseMap(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, OBJECT_MAPPER.getTypeFactory().constructType(Dict.class));
        } catch (MismatchedInputException e) {
            // 类型不匹配说明不是json
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Dict> parseArrayMap(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Dict.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
