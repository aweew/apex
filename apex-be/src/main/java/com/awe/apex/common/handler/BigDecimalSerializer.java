package com.awe.apex.common.handler;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * BigDecimal序列化
 *
 * @author Awe
 * @date 2023/7/23 15:36
 */
@NoArgsConstructor
public class BigDecimalSerializer extends JsonSerializer<BigDecimal> implements ContextualSerializer {

    private int scale = 2;

    private RoundingMode roundingMode = RoundingMode.HALF_UP;

    public final static BigDecimalSerializer INSTANCE = new BigDecimalSerializer();

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeString("0");
        } else {
            // gen.writeString(value.stripTrailingZeros().toPlainString());
            gen.writeString(value.setScale(scale, roundingMode).stripTrailingZeros().toPlainString());
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider serializerProvider, BeanProperty beanProperty) throws JsonMappingException {
        if (beanProperty != null) {
            if (Objects.equals(beanProperty.getType().getRawClass(), BigDecimal.class)) {
                BigDecimalFormat bigDecimalFormat = beanProperty.getAnnotation((BigDecimalFormat.class));
                if (bigDecimalFormat == null) {
                    bigDecimalFormat = beanProperty.getContextAnnotation(BigDecimalFormat.class);
                }
                BigDecimalSerializer bigDecimalSerializer = new BigDecimalSerializer();
                if (bigDecimalFormat != null) {
                    bigDecimalSerializer.scale = bigDecimalFormat.scale();
                    bigDecimalSerializer.roundingMode = bigDecimalFormat.roundingMode();
                }
                return bigDecimalSerializer;
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(beanProperty);
    }

}
