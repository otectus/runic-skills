package com.otectus.runicskills.integration.tide;

import java.util.HashSet;
import java.util.Set;

/** Bounded five-minute collection window. Only committed native fish enter this sequence. */
public final class KeeperSequence {
    public static final int TICKS = 5 * 60 * 20;
    private final long started;
    private final Set<String> species = new HashSet<>();
    private int habitats;

    public KeeperSequence(long started) { this.started = started; }
    public boolean expired(long now) { return now < started || now - started >= TICKS; }

    public boolean caught(long now, Set<String> caught, int habitat) {
        if (expired(now) || habitat < 0 || habitat >= 16 || caught.isEmpty()) return false;
        boolean fish = false;
        for (String id : caught) {
            if (id == null || id.isBlank() || id.length() > 256) continue;
            fish = true;
            if (species.size() < 3) species.add(id);
        }
        if (fish) habitats |= 1 << habitat;
        return species.size() == 3 && Integer.bitCount(habitats) >= 2;
    }
}
