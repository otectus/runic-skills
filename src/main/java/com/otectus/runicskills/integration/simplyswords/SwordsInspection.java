package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.common.IntegrationModule;
import com.otectus.runicskills.integration.common.IntegrationRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.lang.reflect.Method;
import java.util.Optional;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Only native getters inspected in the pinned Forge jar; no initialization or component setters. */
public final class SwordsInspection {
    public record Snapshot(boolean progression, int level, int unlockLevel, boolean abilityUnlocked,
                           boolean gemsActive, boolean socketData, boolean runicSlot, boolean netherSlot,
                           ResourceLocation runicPower, ResourceLocation netherPower,
                           ResourceLocation implicit, ResourceLocation weaponType, Integer implicitValue,
                           boolean runicRecognized, boolean netherRecognized) {}
    private record Readers(ResourceLocation emptyPower, Method progression, Method level, Method unlockLevel, Method ability, Method active,
                           Object gemKey, Method contains, Method component, Method runicSlot, Method netherSlot,
                           Method runicFilled, Method netherFilled, Method runicPower, Method netherPower,
                           Method implicit, Method implicitId, Method weaponType, Method implicitValue) {}
    private static volatile Readers readers;
    private SwordsInspection() {}
    public static boolean available() {
        return IntegrationRuntime.check(IntegrationModule.SIMPLY_SWORDS, Feature.PERKS, Capability.READ_ONLY_TOOLTIP).available();
    }
    public static void probe() {
        readers = null;
        var module = IntegrationModule.SIMPLY_SWORDS;
        var evidence = IntegrationRuntime.localEvidence().get(module);
        if (evidence == null || !module.version.equals(evidence.version()) || !module.sha256.equals(evidence.sha256())) return;
        try {
            ClassLoader loader = SwordsInspection.class.getClassLoader();
            Class<?> awakening = Class.forName("net.sweenus.simplyswords.api.AwakeningApi", false, loader);
            Class<?> keys = Class.forName("net.sweenus.simplyswords.registry.ComponentTypeRegistry", false, loader);
            Class<?> key = Class.forName("net.sweenus.simplyswords.item.component.StackComponentKey", false, loader);
            Class<?> gems = Class.forName("net.sweenus.simplyswords.power.GemPowerComponent", false, loader);
            Class<?> implicits = Class.forName("net.sweenus.simplyswords.api.WeaponImplicitRegistry", false, loader);
            Class<?> implicit = Class.forName("net.sweenus.simplyswords.item.component.WeaponImplicitComponent", false, loader);
            readers = new Readers(
                    (ResourceLocation) Class.forName("net.sweenus.simplyswords.power.GemPower", false, loader).getField("EMPTY_ID").get(null),
                    method(awakening, "usesAwakeningProgression", boolean.class, ItemStack.class),
                    method(awakening, "getLevel", int.class, ItemStack.class),
                    method(awakening, "getAbilityUnlockLevel", int.class, ItemStack.class),
                    method(awakening, "isAbilityUnlocked", boolean.class, ItemStack.class),
                    method(awakening, "areGemPowersActive", boolean.class, ItemStack.class),
                    keys.getField("GEM_POWER").get(null), method(key, "contains", boolean.class, ItemStack.class),
                    method(key, "get", Object.class, ItemStack.class),
                    method(gems, "hasRunicPower", boolean.class), method(gems, "hasNetherPower", boolean.class),
                    method(gems, "hasRunicSlotFilled", boolean.class), method(gems, "hasNetherSlotFilled", boolean.class),
                    method(gems, "runicPower", ResourceLocation.class), method(gems, "netherPower", ResourceLocation.class),
                    method(implicits, "peekWeaponImplicit", Optional.class, ItemStack.class),
                    method(implicit, "implicitId", ResourceLocation.class), method(implicit, "weaponType", ResourceLocation.class),
                    method(implicit, "value", int.class));
            IntegrationRuntime.capability(module, Capability.READ_ONLY_TOOLTIP, "");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) { fail(failure); }
    }
    private static Method method(Class<?> type, String name, Class<?> result, Class<?>... parameters) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameters);
        if (method.getReturnType() != result) throw new NoSuchMethodException(type.getName() + "." + name + " return type");
        return method;
    }
    public static Optional<Snapshot> read(ItemStack stack) {
        Readers r = readers;
        if (r == null || !available() || stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof net.minecraft.world.item.SwordItem)) return Optional.empty();
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        // More owns its inherited weapons; this perk must not run through their SS superclass.
        if (id == null || !IntegrationModule.SIMPLY_SWORDS.modId.equals(id.getNamespace())) return Optional.empty();
        try {
            boolean sockets = (boolean) r.contains.invoke(r.gemKey, stack);
            Object gems = sockets ? r.component.invoke(r.gemKey, stack) : null;
            sockets = gems != null; // Malformed or absent data is unknown, not an initialized empty socket.
            Object implicit = ((Optional<?>) r.implicit.invoke(null, stack)).orElse(null);
            return Optional.of(new Snapshot((boolean) r.progression.invoke(null, stack), (int) r.level.invoke(null, stack),
                    (int) r.unlockLevel.invoke(null, stack), (boolean) r.ability.invoke(null, stack),
                    (boolean) r.active.invoke(null, stack), sockets,
                    sockets && (boolean) r.runicSlot.invoke(gems), sockets && (boolean) r.netherSlot.invoke(gems),
                    sockets ? power(r, r.runicPower, gems) : null,
                    sockets ? power(r, r.netherPower, gems) : null,
                    implicit == null ? null : (ResourceLocation) r.implicitId.invoke(implicit),
                    implicit == null ? null : (ResourceLocation) r.weaponType.invoke(implicit),
                    implicit == null ? null : (Integer) r.implicitValue.invoke(implicit),
                    sockets && (boolean) r.runicFilled.invoke(gems), sockets && (boolean) r.netherFilled.invoke(gems)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) { fail(failure); return Optional.empty(); }
    }
    private static ResourceLocation power(Readers r, Method method, Object component) throws ReflectiveOperationException {
        ResourceLocation id = (ResourceLocation) method.invoke(component);
        return r.emptyPower.equals(id) ? null : id;
    }
    private static void fail(Throwable failure) {
        readers = null;
        IntegrationRuntime.capability(IntegrationModule.SIMPLY_SWORDS, Capability.READ_ONLY_TOOLTIP,
                "Native read-only inspection failed; unavailable until restart.");
        RunicSkills.getLOGGER().warn("Simply Swords read-only inspection unavailable", failure);
    }
}
