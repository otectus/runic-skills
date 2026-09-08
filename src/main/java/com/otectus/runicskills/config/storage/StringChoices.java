package com.otectus.runicskills.config.storage;

import java.lang.annotation.*;

/** Finite string configuration choices, validated on disk load and server snapshot application. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface StringChoices {
    String[] value();
    String fallback();
}
