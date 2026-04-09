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

    public CompoundTag serializeNBT() {
        return createPlayerAbility().serializeNBT();
    }

    public void deserializeNBT(CompoundTag nbt) {
        createPlayerAbility().deserializeNBT(nbt);
    }
}


