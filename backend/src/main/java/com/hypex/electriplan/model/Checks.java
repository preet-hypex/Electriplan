package com.hypex.electriplan.model;

import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The invariant checks the model's records run in their constructors. Each one
 * mirrors a rule in the JSON Schemas, so a record that exists is schema-valid.
 * A failed check throws {@link IllegalArgumentException} naming the field.
 */
public final class Checks {

    /** The schemas' {@code id}: a letter, then up to 63 letters, digits, _ or -. */
    public static final Pattern ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");
    /** Catalogue item codes: DL-IC4-10W-800LM. */
    public static final Pattern ITEM_CODE = Pattern.compile("^[A-Z0-9][A-Z0-9.-]*$");

    private Checks() {
    }

    public static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public static String text(@Nullable String value, String field) {
        if (required(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public static String id(@Nullable String value, String field) {
        return matches(value, ID, field);
    }

    public static @Nullable String optionalId(@Nullable String value, String field) {
        return value == null ? null : id(value, field);
    }

    public static String matches(@Nullable String value, Pattern pattern, String field) {
        if (!pattern.matcher(required(value, field)).matches()) {
            throw new IllegalArgumentException(field + " '" + value + "' does not match " + pattern.pattern());
        }
        return value;
    }

    public static @Nullable String optionalMatches(@Nullable String value, Pattern pattern, String field) {
        return value == null ? null : matches(value, pattern, field);
    }

    public static double finite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be a finite number, not " + value);
        }
        return value;
    }

    public static double nonNegative(double value, String field) {
        if (finite(value, field) < 0) {
            throw new IllegalArgumentException(field + " must not be negative, was " + value);
        }
        return value;
    }

    public static double positive(double value, String field) {
        if (finite(value, field) <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero, was " + value);
        }
        return value;
    }

    public static double between(double value, double min, double max, String field) {
        if (finite(value, field) < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + ", was " + value);
        }
        return value;
    }

    public static int between(@Nullable Integer value, int min, int max, String field) {
        int v = required(value, field);
        if (v < min || v > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + ", was " + v);
        }
        return v;
    }

    public static int oneOf(@Nullable Integer value, String field, int... allowed) {
        int v = required(value, field);
        for (int a : allowed) {
            if (a == v) {
                return v;
            }
        }
        throw new IllegalArgumentException(field + " must be one of " + java.util.Arrays.toString(allowed) + ", was " + v);
    }

    /** A required list: immutable, with no null elements. */
    public static <T> List<T> list(@Nullable List<T> values, String field) {
        try {
            return List.copyOf(required(values, field));
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(field + " must not contain null", e);
        }
    }

    /** An optional list: absent means empty. */
    public static <T> List<T> optionalList(@Nullable List<T> values, String field) {
        return values == null ? List.of() : list(values, field);
    }

    public static List<String> ids(@Nullable List<String> values, String field) {
        List<String> copy = list(values, field);
        copy.forEach(v -> id(v, field + "[]"));
        return copy;
    }

    public static List<String> optionalIds(@Nullable List<String> values, String field) {
        return values == null ? List.of() : ids(values, field);
    }

    public static <T> List<T> sized(@Nullable List<T> values, int min, int max, String field) {
        List<T> copy = list(values, field);
        if (copy.size() < min || copy.size() > max) {
            throw new IllegalArgumentException(field + " must have " + min + " to " + max + " items, has " + copy.size());
        }
        return copy;
    }
}
