package com.otectus.runicskills.common.inventory;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Quarantines unsupported saved counts before vanilla can discard an invalid stack. */
public final class StackDataRecovery {
    private StackDataRecovery() {}
    /** Exceptional overflow is an explicit operator-recovery export, never invisible perk storage. */
    public static Path exportCanceled(net.minecraft.world.entity.player.Player owner, net.minecraft.world.item.ItemStack stack) {
        CompoundTag record = new CompoundTag(); record.putUUID("Owner", owner.getUUID());
        record.put("Stack", stack.save(new CompoundTag())); record.putString("Reason", "canceled drop recovery queue full");
        Path root = owner.level().getServer().getServerDirectory().toPath();
        Path file = root.resolve("debug/runicskills-stack-recovery/canceled-" + java.util.UUID.randomUUID() + ".nbt");
        try { Files.createDirectories(file.getParent()); NbtIo.writeCompressed(record, file.toFile()); }
        catch (IOException failure) { throw new IllegalStateException("Unable to export canceled item ownership", failure); }
        RunicSkills.getLOGGER().error("Canceled item ownership exported for {} to {}", owner.getUUID(), file);
        owner.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.runicskills.pack_mule.exported"));
        return file;
    }
    /**
     * Quarantines a saved count this mod's own representation cannot carry, before vanilla's
     * catch-and-return-EMPTY can discard the record.
     *
     * <p>Only while Runic Skills owns the NBT representation. A count written by a foreign provider
     * is that provider's data in that provider's format: it is not corrupt because it is larger than
     * a Runic limit (reference document §4.4), and exporting a copy of every large stack a storage
     * mod legitimately wrote would be noise, not recovery. When the owner is unknown this mod also
     * stays out of the way — it neither validates nor rewrites, so nothing is lost either way.
     */
    public static Path preserveInvalid(CompoundTag tag) {
        StackRepresentationProvider provider = StackRepresentationProvider.selected();
        if (!provider.ownsNbtCount()) return null;
        if (!tag.contains("Count", Tag.TAG_INT) || tag.getString("id").isBlank()
                || tag.getString("id").equals("minecraft:air")) return null;
        int count = tag.getInt("Count");
        if (StackCapacityMath.representable(count, provider)) return null;
        try {
            byte[] bytes = tag.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            Path root = RunicSkills.server == null ? net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    : RunicSkills.server.getServerDirectory().toPath();
            Path path = root.resolve("debug/runicskills-stack-recovery/" + hash + ".nbt");
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent()); NbtIo.writeCompressed(tag, path.toFile());
                RunicSkills.getLOGGER().error("Unsupported saved item count {} for {}; original NBT preserved at {}. Restore it with a compatible count format before deleting this recovery file.", count, tag.getString("id"), path);
            }
            return path;
        } catch (IOException | java.security.NoSuchAlgorithmException failure) {
            // Called at ItemStack.of HEAD, outside vanilla's catch-and-return-EMPTY block.
            throw new IllegalStateException("Cannot preserve unsupported saved item data; refusing to discard it", failure);
        }
    }
}
