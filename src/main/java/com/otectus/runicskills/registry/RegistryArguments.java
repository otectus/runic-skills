package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.command.arguments.SkillArgument;
import com.otectus.runicskills.common.command.arguments.TitleArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;


public class RegistryArguments {
    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> REGISTER = DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, RunicSkills.MOD_ID);

    public static final RegistryObject<SingletonArgumentInfo<SkillArgument>> SKILL_ARGUMENT = REGISTER.register("skill", () -> ArgumentTypeInfos.registerByClass(SkillArgument.class, SingletonArgumentInfo.contextFree(SkillArgument::getArgument)));

    public static final RegistryObject<SingletonArgumentInfo<TitleArgument>> TITLE_ARGUMENT = REGISTER.register("title", () -> ArgumentTypeInfos.registerByClass(TitleArgument.class, SingletonArgumentInfo.contextFree(TitleArgument::getArgument)));

    public static void load(IEventBus eventBus) {
        REGISTER.register(eventBus);
    }
}


