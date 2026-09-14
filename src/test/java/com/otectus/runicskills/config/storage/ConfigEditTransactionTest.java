package com.otectus.runicskills.config.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import static org.junit.jupiter.api.Assertions.*;

class ConfigEditTransactionTest {
    public static class Settings { @Clamp(min=2,max=1000) public int cap=32; public String[] excluded={}; }
    @Test void openingAndCancelDoNotWriteOrPublish(@TempDir Path dir) throws Exception {
        Path file=dir.resolve("common.json5");
        String bytes="// keep formatting\n{\"cap\":64,\"future\":42}";
        Files.writeString(file,bytes);
        FileTime time=Files.getLastModifiedTime(file);
        var holder=new ConfigHolder<>(Settings.class,file,Settings::new);
        var draft=holder.beginEdit(); draft.draft().cap=100;
        assertEquals(bytes,Files.readString(file)); assertEquals(time,Files.getLastModifiedTime(file));
        assertEquals(64,holder.beginEdit().draft().cap);
    }
    @Test void commitIsDetachedPreservesUnknownsAndDetectsConflicts(@TempDir Path dir) throws Exception {
        Path file=dir.resolve("common.json5"); Files.writeString(file,"{\"cap\":32,\"future\":42}");
        var holder=new ConfigHolder<>(Settings.class,file,Settings::new);
        var draft=holder.beginEdit(); draft.draft().cap=512; draft.draft().excluded=new String[]{"a","b"};
        assertTrue(holder.commit(draft,draft.draft()).success());
        draft.draft().cap=999;
        assertEquals(512,holder.local().cap);
        assertTrue(Files.readString(file).contains("\"future\": 42"));
        var next=holder.beginEdit(); Files.writeString(file,"{\"cap\":100}"); next.draft().cap=16;
        assertFalse(holder.commit(next,next.draft()).success()); assertEquals(512,holder.local().cap);
        assertEquals(100,holder.beginEdit().draft().cap);
    }
    @Test void failureDoesNotPublishAndMalformedOpenPreservesBytes(@TempDir Path dir) throws Exception {
        Path file=dir.resolve("common.json5"); Files.writeString(file,"{broken:");
        var holder=new ConfigHolder<>(Settings.class,file,Settings::new);
        assertThrows(IllegalStateException.class,holder::beginEdit);
        assertEquals("{broken:",Files.readString(file)); assertEquals(1,Files.list(dir).count());
        Files.writeString(file,"{\"cap\":32}"); holder.load();
        var draft=holder.beginEdit(); draft.draft().cap=100;
        Files.createDirectory(file.resolveSibling("common.json5.runicskills.tmp"));
        assertFalse(holder.commit(draft,draft.draft()).success()); assertEquals(32,holder.local().cap);
        assertEquals(32,holder.beginEdit().draft().cap);
    }
}
