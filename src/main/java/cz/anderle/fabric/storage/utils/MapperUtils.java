package cz.anderle.fabric.storage.utils;

import org.openapitools.jackson.nullable.JsonNullable;

import java.util.function.Consumer;

public interface MapperUtils {

    static <T> void applyIfPresent(JsonNullable<T> value, Consumer<T> setter) {
        if (value != null && value.isPresent()) {
            setter.accept(value.orElse(null));
        }
    }
}
