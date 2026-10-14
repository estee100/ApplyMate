package com.applymate.ai;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a Gemini response schema (an OpenAPI subset) from a record type, so the
 * schema the model is held to can never drift from the Java type it is parsed into.
 * Supports String, int/long, double, boolean, nested records and List of any of those.
 */
public final class ResponseSchema {

    private ResponseSchema() {}

    public static Map<String, Object> of(Class<?> type) {
        return schemaFor(type, null);
    }

    private static Map<String, Object> schemaFor(Type type, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (type instanceof ParameterizedType p && p.getRawType() == List.class) {
            schema.put("type", "ARRAY");
            schema.put("items", schemaFor(p.getActualTypeArguments()[0], null));
        } else if (type == String.class) {
            schema.put("type", "STRING");
        } else if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            schema.put("type", "INTEGER");
        } else if (type == double.class || type == Double.class || type == float.class) {
            schema.put("type", "NUMBER");
        } else if (type == boolean.class || type == Boolean.class) {
            schema.put("type", "BOOLEAN");
        } else if (type instanceof Class<?> c && c.isRecord()) {
            schema.put("type", "OBJECT");
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            for (RecordComponent component : c.getRecordComponents()) {
                Desc desc = component.getAnnotation(Desc.class);
                properties.put(component.getName(), schemaFor(component.getGenericType(), desc == null ? null : desc.value()));
                names.add(component.getName());
            }
            schema.put("properties", properties);
            schema.put("required", names);
            // Keeps the model writing fields in declaration order, which reads better and parses the same.
            schema.put("propertyOrdering", names);
        } else {
            throw new IllegalArgumentException("Unsupported schema type: " + type);
        }
        if (description != null) schema.put("description", description);
        return schema;
    }
}
