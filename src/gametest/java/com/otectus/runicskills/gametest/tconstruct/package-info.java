/**
 * Gametests that require Tinker's Construct on the runtime classpath
 * ({@code ./gradlew runGameTestServer -PtinkersProfile=stable}).
 *
 * <p>Classes here must not carry {@code @GameTestHolder} and must be listed in
 * {@link com.otectus.runicskills.gametest.TConstructGameTests}, which registers them only when
 * Tinkers' is loaded; see that class for why the annotation scan cannot be used.
 */
package com.otectus.runicskills.gametest.tconstruct;
