package net.neoforged.neoforge.common;

import net.neoforged.fml.config.IConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Minimal Paper-side subset of NeoForge's config API used by shared config definitions.
 */
public class ModConfigSpec implements IConfigSpec {
    private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();

    public Map<String, ConfigValue<?>> getValues() {
        return values;
    }

    public static final class Builder {
        private final ModConfigSpec spec = new ModConfigSpec();

        public Builder comment(String... ignored) {
            return this;
        }

        public Builder translation(String ignored) {
            return this;
        }

        public <T> ConfigValue<T> define(String key, T defaultValue) {
            ConfigValue<T> value = new ConfigValue<>(key, defaultValue, validatorForDefaultValue(defaultValue));
            spec.values.put(key, value);
            return value;
        }

        public <T extends Number & Comparable<T>> ConfigValue<T> defineInRange(String key, T defaultValue, T min, T max) {
            ConfigValue<T> value = new ConfigValue<>(
                    key,
                    defaultValue,
                    candidate -> candidate instanceof Number &&
                            asDouble(candidate) >= asDouble(min) &&
                            asDouble(candidate) <= asDouble(max)
            );
            spec.values.put(key, value);
            return value;
        }

        public ConfigValue<Double> defineInRange(String key, double defaultValue, double min, double max) {
            return defineInRange(key, Double.valueOf(defaultValue), Double.valueOf(min), Double.valueOf(max));
        }

        public <T> Pair<T, ModConfigSpec> configure(Function<Builder, T> factory) {
            return Pair.of(factory.apply(this), spec);
        }

        private static double asDouble(Object value) {
            return ((Number) value).doubleValue();
        }

        private static Predicate<Object> validatorForDefaultValue(Object defaultValue) {
            if (defaultValue instanceof Number) {
                return candidate -> candidate instanceof Number;
            }
            if (defaultValue instanceof String) {
                return candidate -> candidate instanceof String;
            }
            if (defaultValue instanceof Boolean) {
                return candidate -> candidate instanceof Boolean;
            }
            if (defaultValue == null) {
                return candidate -> true;
            }
            Class<?> expectedType = defaultValue.getClass();
            return expectedType::isInstance;
        }
    }

    public static class ConfigValue<T> {
        private final String key;
        private final T defaultValue;
        private final Predicate<Object> validator;
        private T value;

        private ConfigValue(String key, T defaultValue, Predicate<Object> validator) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.validator = validator;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        public T getDefaultValue() {
            return defaultValue;
        }

        public String getKey() {
            return key;
        }

        public void set(T value) {
            this.value = Objects.requireNonNullElse(value, defaultValue);
        }

        public void save() {
        }

        @SuppressWarnings("unchecked")
        public void load(Object rawValue) {
            if (rawValue == null || !validator.test(rawValue)) {
                value = defaultValue;
                return;
            }
            try {
                if (defaultValue instanceof Double) {
                    value = rawValue instanceof Number ? (T) Double.valueOf(((Number) rawValue).doubleValue()) : defaultValue;
                } else if (defaultValue instanceof Float) {
                    value = rawValue instanceof Number ? (T) Float.valueOf(((Number) rawValue).floatValue()) : defaultValue;
                } else if (defaultValue instanceof Integer) {
                    value = rawValue instanceof Number ? (T) Integer.valueOf(((Number) rawValue).intValue()) : defaultValue;
                } else if (defaultValue instanceof Long) {
                    value = rawValue instanceof Number ? (T) Long.valueOf(((Number) rawValue).longValue()) : defaultValue;
                } else if (defaultValue instanceof Boolean) {
                    value = rawValue instanceof Boolean ? (T) rawValue : defaultValue;
                } else if (defaultValue instanceof String) {
                    value = (T) rawValue.toString();
                } else if (defaultValue.getClass().isInstance(rawValue)) {
                    value = (T) rawValue;
                } else {
                    value = defaultValue;
                }
            } catch (RuntimeException ignored) {
                value = defaultValue;
            }
        }
    }
}
