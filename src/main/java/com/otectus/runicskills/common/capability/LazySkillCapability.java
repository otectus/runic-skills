package com.otectus.runicskills.common.capability;

import com.otectus.runicskills.registry.RegistryCapabilities;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LazySkillCapability implements ICapabilitySerializable<CompoundTag> {
    private SkillCapability capability;
    /**
     * Not {@code final}: {@link #invalidate()} replaces this with a live instance. See the note
     * there — a permanently dead optional silently wiped every player's progression on death.
     */
    private LazyOptional<SkillCapability> optional;

    public LazySkillCapability(SkillCapability provider) {
        this.capability = provider;
        this.optional = LazyOptional.of(this::createPlayerAbility);
    }

    private SkillCapability createPlayerAbility() {
        if (this.capability == null) this.capability = new SkillCapability();
        return this.capability;
    }

    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == RegistryCapabilities.SKILL) return this.optional.cast();
        return LazyOptional.empty();
    }

    /**
     * Invalidates the handed-out {@link LazyOptional} so holders stop resolving it.
     *
     * <p>{@code Entity#invalidateCaps} stops <em>new</em> {@code getCapability} calls from
     * resolving, but a {@code LazyOptional} that was already handed to a caller keeps resolving to
     * the old {@link SkillCapability} forever. Across a death or dimension change — where Forge
     * builds a new player entity and {@code PlayerEvent.Clone} copies the data across — anything
     * still holding the previous optional silently read and wrote the dead player's state
     * (RS-007). Registered as an invalidation listener at attach time so Forge drives it.
     *
     * <p><b>The replacement is not optional.</b> {@code LazyOptional#invalidate} is irreversible —
     * nothing sets {@code isValid} back to {@code true} — and this provider hands out one shared
     * optional. Merely invalidating it therefore killed the capability permanently, and on the
     * respawn path that is fatal: {@code PlayerList#respawn} discards the old entity (→
     * {@code invalidateCaps} → this listener) <em>before</em> building the new one and firing
     * {@code PlayerEvent.Clone}. {@code reviveCaps()} only flips the provider's own {@code valid}
     * flag, so {@code getCapability} still returned the dead optional, {@code ifPresent} no-opped,
     * {@code copyFrom} never ran, and the player kept the blank capability from attach time — which
     * the next autosave then wrote over their real progression. Installing a fresh optional keeps
     * the RS-007 guarantee (every already-handed-out optional still goes dead) while leaving the
     * capability resolvable after {@code reviveCaps()}. The underlying {@link SkillCapability}
     * instance is untouched, so no data is recreated.
     */
    public void invalidate() {
        // Reassign BEFORE invalidating: invalidate() runs listener callbacks synchronously, and a
        // listener that re-queries this provider must not observe the dead instance.
        LazyOptional<SkillCapability> stale = this.optional;
        this.optional = LazyOptional.of(this::createPlayerAbility);
        stale.invalidate();
    }

    public CompoundTag serializeNBT() {
        return createPlayerAbility().serializeNBT();
    }

    public void deserializeNBT(CompoundTag nbt) {
        createPlayerAbility().deserializeNBT(nbt);
    }
}


