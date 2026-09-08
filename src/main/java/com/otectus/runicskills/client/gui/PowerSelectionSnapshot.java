package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.common.util.PacketBounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/** Immutable render state: capability sync mutates its slot lists in place. */
public record PowerSelectionSnapshot(List<String> marks, List<String> seals, String crown,
                                     List<String> disabled, boolean hideDisabled) {
    public PowerSelectionSnapshot {
        marks = List.copyOf(marks);
        seals = List.copyOf(seals);
        // Configuration matchers tolerate null list entries; taking a UI snapshot must too.
        disabled = disabled == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(disabled));
    }

    public boolean matches(List<String> currentMarks, List<String> currentSeals, String currentCrown,
                           List<String> currentDisabled, boolean currentHideDisabled) {
        return marks.equals(currentMarks) && seals.equals(currentSeals) && crown.equals(currentCrown)
                && disabled.equals(currentDisabled == null ? List.of() : currentDisabled)
                && hideDisabled == currentHideDisabled;
    }

    /** Recovery follows slot order, omits duplicate ids, and never creates an invalid packet. */
    public List<String> missingIds(Predicate<String> registered) {
        LinkedHashSet<String> equipped = new LinkedHashSet<>(marks);
        equipped.addAll(seals);
        if (!crown.isEmpty()) equipped.add(crown);
        List<String> missing = new ArrayList<>();
        for (String id : equipped) {
            if (PacketBounds.isContentIdValid(id) && !registered.test(id)) missing.add(id);
        }
        return List.copyOf(missing);
    }
}
