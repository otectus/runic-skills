package com.otectus.runicskills.config.snapshot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a configuration field's reload behaviour. Absent, a field is
 * {@link ConfigScope#LIVE_SERVER} — see that enum for why that is the right default and when to
 * override it.
 *
 * <p>Deliberately not defaulted to something harmless like "unknown": a field with no honest answer
 * to "does changing this on a live server do anything?" is a field an operator cannot use, and the
 * point of the classification is to remove that ambiguity rather than record it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Scope {
    ConfigScope value();
}
