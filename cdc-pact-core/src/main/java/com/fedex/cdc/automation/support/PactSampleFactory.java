package com.fedex.cdc.automation.support;

import com.fedex.cdc.automation.annotations.PactExample;
import com.fedex.cdc.automation.annotations.PactSecret;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PactSampleFactory {
    public Object sample(Class<?> type) {
        return sample(type, "root", 0);
    }

    private Object sample(Class<?> type, String path, int depth) {
        if (type == null || type == Void.class || type == Void.TYPE) return null;
        if (type == String.class) return path + "-value";
        if (type == UUID.class) return UUID.nameUUIDFromBytes(path.getBytes());
        if (type == int.class || type == Integer.class) return 101;
        if (type == long.class || type == Long.class) return 1001L;
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == BigDecimal.class) return new BigDecimal("1299.95");
        if (type == LocalDate.class) return LocalDate.of(2026, 4, 6);
        if (type == LocalDateTime.class) return LocalDateTime.of(2026, 4, 6, 10, 30);
        if (type == Instant.class) return Instant.parse("2026-04-06T10:30:00Z");
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.isArray()) return List.of(sample(type.getComponentType(), path + "[0]", depth + 1));
        if (List.class.isAssignableFrom(type)) return List.of(path + "-item");
        if (Map.class.isAssignableFrom(type)) return Map.of("key", path + "-value");
        if (type.getName().startsWith("java.")) return null;
        if (depth > 3) return null;
        return pojo(type, path, depth);
    }

    private Map<String, Object> pojo(Class<?> type, String path, int depth) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, RecordComponent> records = recordComponents(type);
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            RecordComponent record = records.get(field.getName());
            PactSecret secret = annotation(field, record, PactSecret.class);
            if (secret != null) {
                values.put(field.getName(), secret.mask());
                continue;
            }
            PactExample example = annotation(field, record, PactExample.class);
            if (example != null) {
                values.put(field.getName(), coerce(example.value(), field.getType()));
                continue;
            }
            Object value = sample(field.getType(), path + "." + field.getName(), depth + 1);
            if (value != null) {
                values.put(field.getName(), value);
            }
        }
        return values;
    }

    private Object coerce(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == BigDecimal.class) return new BigDecimal(value);
        if (type == UUID.class) return UUID.fromString(value);
        return value;
    }

    private Map<String, RecordComponent> recordComponents(Class<?> type) {
        if (!type.isRecord()) return Map.of();
        Map<String, RecordComponent> components = new LinkedHashMap<>();
        for (RecordComponent component : type.getRecordComponents()) {
            components.put(component.getName(), component);
        }
        return components;
    }

    private <T extends java.lang.annotation.Annotation> T annotation(Field field, RecordComponent record, Class<T> annotationType) {
        T annotation = field.getAnnotation(annotationType);
        if (annotation == null && record != null) {
            annotation = record.getAnnotation(annotationType);
        }
        return annotation;
    }
}

