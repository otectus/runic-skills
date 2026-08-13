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
    private final LazyOptional<SkillCapability> optional;

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
     */
    public void invalidate() {
        this.optional.invalidate();
    }

    public CompoundTag serializeNBT() {
        return createPlayerAbility().serializeNBT();
    }

    public void deserializeNBT(CompoundTag nbt) {
        createPlayerAbility().deserializeNBT(nbt);
    }
}


