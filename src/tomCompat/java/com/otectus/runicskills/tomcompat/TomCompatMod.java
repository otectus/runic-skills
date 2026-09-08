package com.otectus.runicskills.tomcompat;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.common.IntegrationAvailability.Capability;
import com.otectus.runicskills.integration.common.IntegrationModule;
import com.otectus.runicskills.integration.common.IntegrationRuntime;
import com.otectus.runicskills.integration.tom.TomAquaAttunement;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

/** Separately packaged, optional companion. Reads native public fields without redistributing T.O. */
@Mod("runicskills_tom_compat")
public final class TomCompatMod {
    private static final String ISS_SHA256 = "92c046383b4960c655f840d8846732a481edcf7c5ed89028b3d7b2cc2910b224";
    public TomCompatMod() { FMLJavaModLoadingContext.get().getModEventBus().addListener(this::loaded); }
    private void loaded(FMLLoadCompleteEvent event) { event.enqueueWork(TomCompatMod::bind); }
    private static void bind() {
        var evidence = IntegrationRuntime.localEvidence().get(IntegrationModule.TOM);
        if (evidence == null || !IntegrationModule.TOM.version.equals(evidence.version())
                || !IntegrationModule.TOM.sha256.equals(evidence.sha256())) return;
        try {
            var iss = ModList.get().getModContainerById("irons_spellbooks").orElseThrow();
            if (!ISS_SHA256.equals(IntegrationRuntime.sha256(iss.getModInfo().getOwningFile().getFile().getFilePath())))
                throw new IllegalStateException("Aqua scaling is unverified for this Iron's Spellbooks artifact");
            Class<?> schools = Class.forName("com.gametechbc.traveloptics.api.init.TravelopticsSchools");
            Class<?> attributes = Class.forName("com.gametechbc.traveloptics.api.init.TravelopticsAttributes");
            var key = (ResourceLocation) schools.getField("AQUA_RESOURCE").get(null);
            var schoolObject = (RegistryObject<?>) schools.getField("AQUA").get(null);
            var powerObject = (RegistryObject<?>) attributes.getField("AQUA_SPELL_POWER").get(null);
            var resistObject = (RegistryObject<?>) attributes.getField("AQUA_MAGIC_RESIST").get(null);
            var school = (SchoolType) schoolObject.get();
            var power = (Attribute) powerObject.get();
            if (!key.equals(schoolObject.getId()) || !key.equals(school.getId())
                    || SchoolRegistry.getSchool(key) != school || power == resistObject.get())
                throw new IllegalStateException("Native Aqua school/attribute identities disagree");
            TomAquaAttunement.bind(key, power);
            if (ModList.get().isLoaded("curios")) {
                Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("isStackValid",
                        Class.forName("top.theillusivec4.curios.api.SlotContext"), net.minecraft.world.item.ItemStack.class);
                IntegrationRuntime.capability(IntegrationModule.TOM, Capability.TALENT_VALIDITY, "");
            }
            com.otectus.runicskills.integration.tom.TomSpellPolicy.probe();
            if (com.otectus.runicskills.integration.tom.TomHookVerification.probe()) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.otectus.runicskills.integration.tom.TomPaidCasts.class);
                com.otectus.runicskills.integration.tom.TomPaidCasts.bindCombat();
            }
            RunicSkills.getLOGGER().info("T.O. companion bound native Aqua power: {}", powerObject.getId());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            IntegrationRuntime.capability(IntegrationModule.TOM, Capability.AQUA_ATTRIBUTE,
                    "Companion could not verify the native Aqua power binding: " + e.getClass().getSimpleName());
            RunicSkills.getLOGGER().warn("T.O. Aqua binding unavailable", e);
        }
    }
}
