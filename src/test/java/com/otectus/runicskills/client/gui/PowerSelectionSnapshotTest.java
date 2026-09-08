package com.otectus.runicskills.client.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerSelectionSnapshotTest {
    @Test
    void serverReplyMutatingTheSameCapabilityListsInvalidatesOldButtons() {
        List<String> marks = new ArrayList<>();
        PowerSelectionSnapshot displayed = new PowerSelectionSnapshot(marks, List.of(), "", List.of(), false);
        assertTrue(displayed.matches(marks, List.of(), "", List.of(), false));
        marks.add("ember_trail"); // SyncSkillCapabilityCP deserializes into existing lists.
        assertFalse(displayed.matches(marks, List.of(), "", List.of(), false));
        assertTrue(displayed.marks().isEmpty(), "the displayed state must not alias capability state");
        displayed = new PowerSelectionSnapshot(marks, List.of(), "", List.of(), false);
        marks.clear(); // A later unequip acknowledgement must refresh the label again.
        assertFalse(displayed.matches(marks, List.of(), "", List.of(), false));
    }

    @Test
    void serverVisibilityChangesInvalidatePoolsWithoutAnEquipmentChange() {
        List<String> disabled = new ArrayList<>();
        PowerSelectionSnapshot displayed = new PowerSelectionSnapshot(List.of(), List.of(), "", disabled, false);
        disabled.add("kindle");
        assertFalse(displayed.matches(List.of(), List.of(), "", disabled, false));
        assertFalse(displayed.matches(List.of(), List.of(), "", List.of(), true));
        assertTrue(displayed.disabled().isEmpty());
    }

    @Test
    void invalidConfigEntriesCannotCrashOrContinuouslyRebuildTheScreen() {
        List<String> disabled = new ArrayList<>();
        disabled.add(null);
        PowerSelectionSnapshot displayed = new PowerSelectionSnapshot(List.of(), List.of(), "", disabled, true);
        assertTrue(displayed.matches(List.of(), List.of(), "", disabled, true));
        displayed = new PowerSelectionSnapshot(List.of(), List.of(), "", null, false);
        assertTrue(displayed.matches(List.of(), List.of(), "", null, false));
    }

    @Test
    void missingAddonSlotsCanBeRecoveredInOrderWithoutSendingMalformedIds() {
        PowerSelectionSnapshot displayed = new PowerSelectionSnapshot(
                List.of("kindle", "removed_mark", "invalid id"),
                List.of("removed_seal", "removed_mark"), "addon:removed_crown", List.of(), false);
        assertEquals(List.of("removed_mark", "removed_seal", "addon:removed_crown"),
                displayed.missingIds("kindle"::equals));
        assertEquals(List.of("kindle", "removed_mark", "invalid id"), displayed.marks(),
                "rendering recovery choices cannot mutate saved slots");
    }
}
