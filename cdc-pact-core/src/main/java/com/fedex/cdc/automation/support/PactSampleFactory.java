package com.fedex.cdc.automation.support;

import com.fedex.cdc.automation.annotations.PactExample;
import com.fedex.cdc.automation.annotations.PactSecret;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class PactSampleFactory {
    public Object sample(Class<?> type) {
        return sample((Type) type);
    }

    public Object sample(Type type) {
        return sample((Type) type, "root", 0);
    }

    private Object sample(Type type, String path, int depth) {
        if (type == null || type == Void.class || type == Void.TYPE) {
            return null;
        }
        if (depth > 5) {
            return null;
        }

        if (type instanceof Class<?> clazz) {
            return sampleClass(clazz, path, depth);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return sampleParameterized(parameterizedType, path, depth);
        }
        if (type instanceof GenericArrayType arrayType) {
            Object item = sample(arrayType.getGenericComponentType(), path + "[0]", depth + 1);
            return List.of(item != null ? item : path + "-item");
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            Type[] bounds = typeVariable.getBounds();
            return sample(bounds.length > 0 ? bounds[0] : Object.class, path, depth + 1);
        }
        if (type instanceof WildcardType wildcardType) {
            Type[] bounds = wildcardType.getUpperBounds();
            return sample(bounds.length > 0 ? bounds[0] : Object.class, path, depth + 1);
        }
        return path + "-value";
    }

    private Object sampleClass(Class<?> type, String path, int depth) {
        if (type == null || type == Void.class || type == Void.TYPE) {
            return null;
        }
        if (type == String.class || type == Character.class || type == char.class) {
            return path + "-value";
        }
        if (type == UUID.class) {
            return UUID.nameUUIDFromBytes(path.getBytes());
        }
        if (type == int.class || type == Integer.class || type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            return 101;
        }
        if (type == long.class || type == Long.class) {
            return 1001L;
        }
        if (type == BigInteger.class) {
            return BigInteger.valueOf(1001L);
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return 199.95;
        }
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == BigDecimal.class) {
            return new BigDecimal("1299.95");
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2026, 4, 6);
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.of(2026, 4, 6, 10, 30);
        }
        if (type == LocalTime.class) {
            return LocalTime.of(10, 30);
        }
        if (type == Instant.class) {
            return Instant.parse("2026-04-06T10:30:00Z");
        }
        if (type == OffsetDateTime.class) {
            return OffsetDateTime.parse("2026-04-06T10:30:00Z");
        }
        if (type == URI.class) {
            return URI.create("https://example.fedex.cdc/path");
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        if (type.isArray()) {
            Object item = sample(type.getComponentType(), path + "[0]", depth + 1);
            return List.of(item != null ? item : path + "-item");
        }
        if (Iterable.class.isAssignableFrom(type)) {
            return List.of(path + "-item");
        }
        if (Map.class.isAssignableFrom(type)) {
            return Map.of("key", path + "-value");
        }
        if (type == Object.class) {
            return path + "-value";
        }
        if (type.getName().startsWith("java.")) {
            return null;
        }
        return pojo(type, path, depth);
    }

    private Object sampleParameterized(ParameterizedType type, String path, int depth) {
        if (!(type.getRawType() instanceof Class<?> rawClass)) {
            return path + "-value";
        }

        if (Iterable.class.isAssignableFrom(rawClass)) {
            Type elementType = type.getActualTypeArguments().length > 0 ? type.getActualTypeArguments()[0] : String.class;
            Object item = sample(elementType, path + "[0]", depth + 1);
            return List.of(item != null ? item : path + "-item");
        }

        if (Map.class.isAssignableFrom(rawClass)) {
            Type keyType = type.getActualTypeArguments().length > 0 ? type.getActualTypeArguments()[0] : String.class;
            Type valueType = type.getActualTypeArguments().length > 1 ? type.getActualTypeArguments()[1] : String.class;
            String key = sampleMapKey(keyType, path);
            Object value = sample(valueType, path + "." + key, depth + 1);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(key, value != null ? value : path + "-value");
            return map;
        }
        if (Optional.class.isAssignableFrom(rawClass)) {
            Type wrapped = type.getActualTypeArguments().length > 0 ? type.getActualTypeArguments()[0] : String.class;
            return sample(wrapped, path, depth + 1);
        }
        if ("org.openapitools.jackson.nullable.JsonNullable".equals(rawClass.getName())) {
            Type wrapped = type.getActualTypeArguments().length > 0 ? type.getActualTypeArguments()[0] : String.class;
            Object value = sample(wrapped, path, depth + 1);
            return value != null ? value : path + "-value";
        }

        return sampleClass(rawClass, path, depth);
    }

    private String sampleMapKey(Type keyType, String path) {
        if (keyType instanceof Class<?> keyClass && (keyClass == String.class || keyClass == Object.class)) {
            return "key";
        }
        Object key = sample(keyType, path + "-key", 1);
        return key == null ? "key" : String.valueOf(key);
    }

    private Map<String, Object> pojo(Class<?> type, String path, int depth) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, RecordComponent> records = recordComponents(type);
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
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
            String schemaExample = schemaExample(field, record);
            if (schemaExample != null && !schemaExample.isBlank()) {
                values.put(field.getName(), coerce(schemaExample, field.getType()));
                continue;
            }
            Type memberType = record != null ? record.getGenericType() : field.getGenericType();
            Object value = sample(memberType, path + "." + field.getName(), depth + 1);
            if (value != null) {
                values.put(field.getName(), value);
            }
        }
        return values;
    }

    private Object coerce(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class || type == short.class || type == Short.class || type == byte.class || type == Byte.class)
            return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == float.class || type == Float.class) return Float.parseFloat(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == BigDecimal.class) return new BigDecimal(value);
        if (type == UUID.class) return UUID.fromString(value);
        if (type.isEnum()) {
            Object[] values = type.getEnumConstants();
            for (Object enumValue : values) {
                if (enumValue.toString().equalsIgnoreCase(value)) {
                    return enumValue;
                }
            }
            return values.length > 0 ? values[0] : value;
        }
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

    private String schemaExample(Field field, RecordComponent record) {
        String fieldValue = schemaExampleFromAnnotations(field.getAnnotations());
        if (fieldValue != null && !fieldValue.isBlank()) {
            return fieldValue;
        }
        if (record == null) {
            return null;
        }
        return schemaExampleFromAnnotations(record.getAnnotations());
    }

    private String schemaExampleFromAnnotations(java.lang.annotation.Annotation[] annotations) {
        for (java.lang.annotation.Annotation annotation : annotations) {
            if (!"Schema".equals(annotation.annotationType().getSimpleName())) {
                continue;
            }
            try {
                Object value = annotation.annotationType().getMethod("example").invoke(annotation);
                if (value instanceof String text && !text.isBlank()) {
                    return text;
                }
            } catch (ReflectiveOperationException ignored) {
                // Best effort only: annotation might not expose "example".
            }
        }
        return null;
    }
}
