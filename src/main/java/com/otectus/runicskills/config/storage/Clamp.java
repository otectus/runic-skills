package com.otectus.runicskills.config.storage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Load-time range clamp for numeric config fields, enforced by {@link ConfigHolder} after a file
 * is parsed. The YACL {@code @IntField}/{@code @FloatField} ranges only constrain the client UI —
 * a hand-edited file bypassed them entirely, letting out-of-range values (e.g.
 * {@code skillMaxLevel = 999999}) flow to runtime math. Fields carrying this annotation are
 * clamped into range with a per-field WARN naming the file, the field, and both values.
 *
 * <p>Server-safe by design: lives in {@code config.storage} with no YACL dependency, so the
 * enforcement also runs on dedicated servers where YACL is absent. Keep the ranges in sync with
 * the YACL annotations on the same field.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Clamp {
    double min() default -Double.MAX_VALUE;

    double max() default Double.MAX_VALUE;
}
