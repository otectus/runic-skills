package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.simplyswords.*;
import com.otectus.runicskills.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.UUID;

@GameTestHolder(RunicSkills.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public class SwordsInspectionGameTest {
    @GameTest(template = "empty")
    public static void resonantReadingNeedsItsNativeCapability(GameTestHelper helper) {
        if (!SwordsInspection.available()) helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.SS_RESONANT_READING.get()),
                "The paid information perk cannot be available without its read capability");
        helper.assertTrue(SwordsInspection.read(new ItemStack(Items.IRON_SWORD)).isEmpty(), "Vanilla weapons remain outside SS ownership");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeSwordsInspectionPreservesState(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("simplyswords")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeSwordsInspectionPreservesState: Simply Swords absent");
            helper.succeed(); return;
        }
        helper.assertTrue(SwordsInspection.available(), "Pinned native readers must be available");
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "swords_reading"));
        var cap = SkillCapability.get(player); var perk = RegistryPerks.SS_RESONANT_READING.get();
        var cfg = HandlerCommonConfig.HANDLER.instance();
        String oldMode = cfg.simplySwordsIntegrationMode; boolean oldPerks = cfg.simplySwordsPerks;
        try {
            cfg.simplySwordsIntegrationMode = "auto"; cfg.simplySwordsPerks = true;
            for (var skill : RegistrySkills.getCachedValues()) cap.setSkillLevel(skill, cfg.skillMaxLevel);
            var weapons = ForgeRegistries.ITEMS.getValues().stream().filter(item -> item instanceof SwordItem
                    && "simplyswords".equals(ForgeRegistries.ITEMS.getKey(item).getNamespace())).toList();
            helper.assertTrue(!weapons.isEmpty(), "Native weapon catalog is present");
            ItemStack awakeningWeapon = null;
            for (var item : weapons) {
                ItemStack stack = new ItemStack(item);
                ItemStack pristine = stack.copy();
                var initial = SwordsInspection.read(stack).orElseThrow();
                helper.assertTrue(ItemStack.matches(stack, pristine), "Pristine inspection never initializes " + ForgeRegistries.ITEMS.getKey(item));
                helper.assertTrue(ResonantReading.details(player, stack).isEmpty(), "Unpurchased information stays unavailable");
                stack.getOrCreateTag().putString("othermod:opaque", "preserve exactly");
                ItemStack saved = stack.copy();
                cap.setPerkRank(perk, 1);
                for (int read = 0; read < 3; read++) helper.assertTrue(!ResonantReading.details(player, stack).isEmpty(), "Purchased perk supplies native facts");
                helper.assertTrue(ItemStack.matches(stack, saved), "Repeated inspection preserves all NBT " + ForgeRegistries.ITEMS.getKey(item));
                cap.setPerkRank(perk, 0);
                if (awakeningWeapon == null && initial.progression()) awakeningWeapon = stack;
            }
            helper.assertTrue(awakeningWeapon != null, "At least one weapon uses the native awakening progression");
            cap.setPerkRank(perk, 1);
            var awakening = Class.forName("net.sweenus.simplyswords.api.AwakeningApi");
            awakening.getMethod("setLevel", ItemStack.class, int.class).invoke(null, awakeningWeapon, 0);
            var dormant = SwordsInspection.read(awakeningWeapon).orElseThrow();
            helper.assertTrue(dormant.level() == 0 && (dormant.unlockLevel() == 0 || !dormant.abilityUnlocked()), "Dormant native ability stays locked");
            ItemStack saved = awakeningWeapon.copy();
            ResonantReading.details(player, awakeningWeapon);
            helper.assertTrue(ItemStack.matches(saved, awakeningWeapon), "Reading a dormant weapon never awakens it");
            awakening.getMethod("initializeFullyAwakened", ItemStack.class).invoke(null, awakeningWeapon);
            var awake = SwordsInspection.read(awakeningWeapon).orElseThrow();
            helper.assertTrue(awake.level() > 0 && awake.abilityUnlocked(), "Native fully awakened state is readable");

            var keyClass = Class.forName("net.sweenus.simplyswords.item.component.StackComponentKey");
            Object gemKey = Class.forName("net.sweenus.simplyswords.registry.ComponentTypeRegistry").getField("GEM_POWER").get(null);
            Object freeze = Class.forName("net.sweenus.simplyswords.registry.GemPowerRegistry").getField("FREEZE").get(null);
            var freezeId = (ResourceLocation) Class.forName("dev.architectury.registry.registries.DeferredSupplier").getMethod("getId").invoke(freeze);
            var unknownId = new ResourceLocation("runic_validation", "unresolved_gem");
            Object component = Class.forName("net.sweenus.simplyswords.power.GemPowerComponent")
                    .getConstructor(boolean.class, boolean.class, ResourceLocation.class, ResourceLocation.class)
                    .newInstance(true, true, freezeId, unknownId);
            keyClass.getMethod("set", ItemStack.class, Object.class).invoke(gemKey, awakeningWeapon, component);
            saved = awakeningWeapon.copy();
            var gems = SwordsInspection.read(awakeningWeapon).orElseThrow();
            helper.assertTrue(gems.socketData() && gems.runicRecognized() && freezeId.equals(gems.runicPower()), "Known occupied socket uses its real native ID");
            helper.assertTrue(unknownId.equals(gems.netherPower()) && !gems.netherRecognized(), "An unresolved stored power is not reported as empty or active");
            ResonantReading.details(player, awakeningWeapon);
            helper.assertTrue(ItemStack.matches(saved, awakeningWeapon), "Known and unknown gem components are never rewritten");
            var keyId = (ResourceLocation) keyClass.getMethod("id").invoke(gemKey);
            awakeningWeapon.getOrCreateTag().getCompound("SimplySwordsComponents").putString(keyId.toString(), "malformed native data");
            saved = awakeningWeapon.copy();
            helper.assertTrue(!SwordsInspection.read(awakeningWeapon).orElseThrow().socketData(), "Malformed component is unavailable, not initialized");
            helper.assertTrue(ItemStack.matches(saved, awakeningWeapon), "Malformed component is preserved");

            for (String mode : new String[]{"off", "observe"}) {
                cfg.simplySwordsIntegrationMode = mode;
                helper.assertTrue(RegistryPerks.isDisabled(perk) && ResonantReading.details(player, awakeningWeapon).isEmpty(), "Mode " + mode + " disables purchase and effect");
                helper.assertTrue(ItemStack.matches(saved, awakeningWeapon), "Mode changes do not rewrite native data");
            }
            cfg.simplySwordsIntegrationMode = "auto"; cfg.simplySwordsPerks = false;
            helper.assertTrue(ResonantReading.details(player, awakeningWeapon).isEmpty(), "Feature switch disables the effect");
            cfg.simplySwordsPerks = true;
            for (var item : ForgeRegistries.ITEMS.getValues()) {
                var id = ForgeRegistries.ITEMS.getKey(item);
                if ("simplymore".equals(id.getNamespace()) || ("simplyswords".equals(id.getNamespace()) && !(item instanceof SwordItem))) {
                    helper.assertTrue(ResonantReading.details(player, new ItemStack(item)).isEmpty(), "SS cannot claim " + id);
                }
            }
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Resonant Reading: {} native weapons preserve pristine and decorated NBT; awakening, gems, malformed data, toggles and ownership passed", weapons.size());
        } finally { cfg.simplySwordsIntegrationMode = oldMode; cfg.simplySwordsPerks = oldPerks; }
        helper.succeed();
    }
}
