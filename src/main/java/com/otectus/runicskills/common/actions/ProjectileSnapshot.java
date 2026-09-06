package com.otectus.runicskills.common.actions;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * What was true at the moment a projectile was launched, carried by the projectile.
 *
 * <p>Spec §9.2 is a list of things that must survive flight, and every one of them is a reason not
 * to look the answer up later: the shooter can swap their bow, drop it, die, or log out before the
 * arrow lands, and an arrow that asked "what is this player holding?" on impact would be answering
 * a different question than the one that was true when it was fired. So the launch seam records the
 * facts once and the projectile carries them.
 *
 * <p><b>Where it lives.</b> Forge's per-entity persistent data, under one namespaced compound. That
 * survives a chunk reload and a server restart, which the spec requires, and it is not a
 * capability: a capability would have to be attached to every projectile in the game to be readable
 * on some of them.
 *
 * <p><b>What it deliberately is not.</b> No copied inventory, no full tool NBT, and no random UUID
 * written back onto the ammunition — §4.3 rejects that outright, because a per-item UUID makes
 * arrows stop stacking. The identity lives on the entity; the item is left alone.
 *
 * <p><b>Bounds.</b> At most {@link #MAX_CLAIMS} recorded proc claims and {@link #MAX_BYTES} of
 * serialized data per entity. Both are checked before the write lands, and an over-budget write is
 * refused rather than truncated: half a snapshot would be read back as a whole one.
 */
public final class ProjectileSnapshot {

    /** The one compound this mod writes on a projectile. */
    public static final String KEY = RunicSkills.MOD_ID + ":projectile";

    /** Section 9.2: at most sixteen recorded Runic proc claims per projectile. */
    public static final int MAX_CLAIMS = 16;

    /** Section 9.2: at most 2 KiB of serialized Runic metadata per entity. */
    public static final int MAX_BYTES = 2048;

    private static final String OWNER = "Owner";
    private static final String ROOT = "Root";
    private static final String HAND = "Hand";
    private static final String LAUNCHER = "Launcher";
    private static final String BONUS = "Bonus";
    private static final String CLAIMS = "Claims";
    private static final String RETURNED = "Returned";

    private ProjectileSnapshot() {
    }

    /**
     * The recorded facts of one launch.
     *
     * @param owner    the player who fired; the only actor whose perks this projectile may use
     * @param rootId   the launch's root action id — shared by every projectile of one multishot,
     *                 which is what lets a one-shot counter consume once per launch while damage
     *                 still applies to each real hit
     * @param offHand  which hand fired it
     * @param launcher the registry id of the launcher, recorded rather than the stack: a bow swap
     *                 after firing cannot strengthen an old arrow if the arrow never looks
     * @param bonus    the scalar Runic bonus that was eligible at launch time
     * @param returned whether this projectile has been observed genuinely returning to its owner
     */
    public record Snapshot(UUID owner, long rootId, boolean offHand, ResourceLocation launcher,
                           double bonus, boolean returned) {
    }

    /**
     * Records a launch on {@code projectile}, replacing anything already there.
     *
     * @return whether the snapshot was written; {@code false} when it would exceed {@link
     *         #MAX_BYTES}, in which case the projectile is left with no Runic data at all and is
     *         therefore ineligible rather than half-described
     */
    public static boolean write(Entity projectile, UUID owner, long rootId, InteractionHand hand,
                                ItemStack launcher, double bonus) {
        if (projectile == null || owner == null) return false;

        CompoundTag snapshot = new CompoundTag();
        snapshot.putUUID(OWNER, owner);
        snapshot.putLong(ROOT, rootId);
        snapshot.putBoolean(HAND, hand == InteractionHand.OFF_HAND);
        ResourceLocation id = launcher == null || launcher.isEmpty()
                ? null : ForgeRegistries.ITEMS.getKey(launcher.getItem());
        if (id != null) snapshot.putString(LAUNCHER, id.toString());
        snapshot.putDouble(BONUS, bonus);
        snapshot.put(CLAIMS, new ListTag());

        if (serializedSize(snapshot) > MAX_BYTES) return false;
        projectile.getPersistentData().put(KEY, snapshot);
        return true;
    }

    /** The launch recorded on {@code projectile}, or empty when it carries none. */
    public static Optional<Snapshot> read(Entity projectile) {
        CompoundTag snapshot = tagOf(projectile);
        if (snapshot == null || !snapshot.hasUUID(OWNER)) return Optional.empty();
        ResourceLocation launcher = snapshot.contains(LAUNCHER, Tag.TAG_STRING)
                ? ResourceLocation.tryParse(snapshot.getString(LAUNCHER)) : null;
        return Optional.of(new Snapshot(
                snapshot.getUUID(OWNER),
                snapshot.getLong(ROOT),
                snapshot.getBoolean(HAND),
                launcher,
                snapshot.getDouble(BONUS),
                snapshot.getBoolean(RETURNED)));
    }

    /**
     * Whether {@code actor} is the player this projectile belongs to.
     *
     * <p>The question every consumer asks first. A projectile with no snapshot — spawned by a
     * dispenser, by a command, or by a mod that does not go through the launch seam — belongs to
     * nobody and answers {@code false} for everyone, which is section 9.2's "dispensers and
     * null-owner projectiles do not receive player perks" with no extra check at each call site.
     */
    public static boolean isOwnedBy(Entity projectile, UUID actor) {
        if (actor == null) return false;
        return read(projectile).map(snapshot -> actor.equals(snapshot.owner())).orElse(false);
    }

    /**
     * Claims {@code effect} for this projectile, reporting whether this call was the first.
     *
     * <p>Separate from {@link RunicActionContext#claim} because the two answer different questions:
     * that one is "once per swing", this one is "once per arrow", and an arrow outlives the action
     * that fired it by however long it stays in the air.
     */
    public static boolean claim(Entity projectile, String effect) {
        CompoundTag snapshot = tagOf(projectile);
        if (snapshot == null || effect == null) return false;
        ListTag claims = snapshot.getList(CLAIMS, Tag.TAG_STRING);
        if (claims.size() >= MAX_CLAIMS) return false;
        for (int index = 0; index < claims.size(); index++) {
            if (effect.equals(claims.getString(index))) return false;
        }
        claims.add(StringTag.valueOf(effect));
        snapshot.put(CLAIMS, claims);
        return serializedSize(snapshot) <= MAX_BYTES;
    }

    /**
     * Records that this projectile genuinely returned to {@code owner}.
     *
     * <p>"Genuine" is the whole point of C04. A tool that flew back to its owner under its own
     * native return behaviour returned; the same tool lying on the ground and walked over was
     * picked up, and a stack put into an inventory by a command is neither. Only the seam that can
     * tell them apart calls this, and it still has to name the right owner to be believed.
     *
     * @return whether the mark was applied — {@code false} for a projectile with no snapshot, a
     *         mismatched owner, or one already marked
     */
    public static boolean markReturned(Entity projectile, UUID owner) {
        CompoundTag snapshot = tagOf(projectile);
        if (snapshot == null || owner == null) return false;
        if (!snapshot.hasUUID(OWNER) || !owner.equals(snapshot.getUUID(OWNER))) return false;
        if (snapshot.getBoolean(RETURNED)) return false;
        snapshot.putBoolean(RETURNED, true);
        return true;
    }

    /** Removes this mod's data from {@code projectile}. */
    public static void clear(Entity projectile) {
        if (projectile != null) projectile.getPersistentData().remove(KEY);
    }

    private static CompoundTag tagOf(Entity projectile) {
        if (projectile == null) return null;
        CompoundTag data = projectile.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY) : null;
    }

    /**
     * How many bytes this compound occupies once written, measured rather than estimated.
     *
     * <p>An estimate would have to model NBT's own framing, and the budget exists precisely so a
     * field added later cannot quietly blow it. A failure to serialize reports as over budget: a
     * compound that cannot be written is not one to store.
     */
    private static int serializedSize(CompoundTag tag) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write(tag, out);
        } catch (IOException e) {
            return Integer.MAX_VALUE;
        }
        return bytes.size();
    }
}
