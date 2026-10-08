package com.hypex.electriplan.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deep, immutable, sorted copies of rule parameters as YAML gives them. */
final class Parameters {

    private Parameters() {
    }

    static Map<String, Object> copy(Map<String, Object> values, String where) {
        if (values == null) {
            return Map.of();
        }
        TreeMap<String, Object> sorted = new TreeMap<>();
        values.forEach((name, value) -> sorted.put(name, value(value, where + ", " + name)));
        return Collections.unmodifiableSortedMap(sorted);
    }

    @SuppressWarnings("unchecked")
    private static Object value(Object value, String where) {
        if (value == null) {
            throw new IllegalArgumentException(where + ": a parameter may not be null");
        }
        if (value instanceof Map<?, ?> map) {
            return copy((Map<String, Object>) map, where);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                out.add(value(list.get(i), where + "[" + i + "]"));
            }
            return Collections.unmodifiableList(out);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        throw new IllegalArgumentException(where + ": not a text, number, true/false, list or map");
    }

    /** Same JSON type: a company may change a number to a number, not to text. */
    static boolean sameType(Object a, Object b) {
        return (a instanceof Number && b instanceof Number)
                || (a instanceof String && b instanceof String)
                || (a instanceof Boolean && b instanceof Boolean)
                || (a instanceof List && b instanceof List)
                || (a instanceof Map && b instanceof Map);
    }

    static String typeName(Object value) {
        return value instanceof Number ? "a number" : value instanceof String ? "text"
                : value instanceof Boolean ? "true/false" : value instanceof List ? "a list" : "a map";
    }
}
