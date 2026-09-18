package com.otectus.runicskills.gametest.irons;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerLockItemsConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.lock.GateSource;
import com.otectus.runicskills.integration.lock.IronsSpellbooksLockProvider;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.registry.RegistrySkills;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The 2.2.1 Iron's gates, run against the real mod.
 *
 * <p>Three separate claims, each of which was either untrue or untested before:
 *
 * <ul>
 *   <li>A book's equipment requirement and a spell's cast requirement are different rules about
 *       different things. An allowed book containing one spell the player cannot cast still casts
 *       everything else in it.</li>
 *   <li>A refused cast costs nothing. Iron's posts {@code SpellPreCastEvent} before it deducts
 *       mana, starts a cooldown or consumes a scroll, so cancelling there has to leave all three
 *       untouched — and the book's contents with them.</li>
 *   <li>An explicit rule for a spell beats the generated formula. It used to lose to it: the
 *       formula ran first, so a pack that deliberately permitted a spell had it refused anyway.</li>
 * </ul>
 *
 * <p>Registered by {@code IronsGameTests} only when {@code irons_spellbooks} is loaded, so the
 * absence profile never sees these classes.
 */
@PrefixGameTestTemplate(false)
public final class SpellGateGameTest {

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void aRefusedCastSpendsNothingAndLeavesTheBookIntact(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean spellLocks = cfg.enableSpellLocks;
        boolean schoolGating = cfg.ironsEnableSchoolGating;
        String model = cfg.ironsSpellGateModel;
        try {
            cfg.enableSpellLocks = true;
            cfg.ironsEnableSchoolGating = true;
            cfg.ironsSpellGateModel = "METADATA";

            ServerPlayer player = player(helper, 1);
            AbstractSpell spell = SpellRegistry.FIREBALL_SPELL.get();
            ItemStack book = inscribedBook(spell);
            List<?> contentsBefore = ISpellContainer.get(book).getActiveSpells();

            MagicData magic = MagicData.getPlayerMagicData(player);
            magic.getPlayerCooldowns().clearCooldowns();
            magic.setMana(1000);
            float manaBefore = magic.getMana();

            boolean started = spell.attemptInitiateCast(book, 1, helper.getLevel(), player,
                    CastSource.SPELLBOOK, true, "mainhand");

            helper.assertTrue(!started, "a level 1 Magic player cast a gated spell");
            helper.assertTrue(magic.getMana() == manaBefore,
                    "the refused cast spent mana: " + manaBefore + " -> " + magic.getMana());
            helper.assertTrue(!magic.getPlayerCooldowns().isOnCooldown(spell),
                    "the refused cast started a cooldown");
            helper.assertTrue(!magic.isCasting(), "the refused cast left the player casting");
            helper.assertTrue(ISpellContainer.get(book).getActiveSpells().size() == contentsBefore.size(),
                    "the refused cast changed the book's contents");
            helper.assertTrue(book.getCount() == 1, "the refused cast consumed the casting item");
        } finally {
            cfg.enableSpellLocks = spellLocks;
            cfg.ironsEnableSchoolGating = schoolGating;
            cfg.ironsSpellGateModel = model;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void aRefusedScrollCastConsumesNoScroll(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean spellLocks = cfg.enableSpellLocks;
        try {
            cfg.enableSpellLocks = true;
            ServerPlayer player = player(helper, 1);
            AbstractSpell spell = SpellRegistry.FIREBALL_SPELL.get();
            ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get(), 3);
            ISpellContainer.createScrollContainer(spell, 1, scroll);

            MagicData magic = MagicData.getPlayerMagicData(player);
            magic.setMana(1000);
            boolean started = spell.attemptInitiateCast(scroll, 1, helper.getLevel(), player,
                    CastSource.SCROLL, true, "mainhand");

            helper.assertTrue(!started, "a gated scroll cast was allowed to start");
            helper.assertTrue(scroll.getCount() == 3,
                    "the refused scroll cast consumed a scroll: 3 -> " + scroll.getCount());
        } finally {
            cfg.enableSpellLocks = spellLocks;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void theSpellMasterSwitchWaivesCastsButNotBookEquipment(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean spellLocks = cfg.enableSpellLocks;
        boolean itemLocks = cfg.enableItemLocks;
        var holder = HandlerLockItemsConfig.HANDLER.instance();
        var saved = holder.lockItemList;
        try {
            cfg.enableItemLocks = true;
            // A book the player cannot equip, and a spell the formula would refuse.
            ItemStack book = new ItemStack(ItemRegistry.ICE_SPELL_BOOK.get());
            holder.lockItemList = new ArrayList<>(List.of(
                    new LockItem(id(book), new LockItem.Skill("magic", 30))));
            HandlerSkill.getSkill();

            ServerPlayer player = player(helper, 1);
            AbstractSpell spell = SpellRegistry.FIREBALL_SPELL.get();

            cfg.enableSpellLocks = false;
            helper.assertTrue(!SkillCapability.get(player).canUseItem(player, book, LockAction.EQUIP),
                    "enableSpellLocks=false waived the book's separate equipment requirement");

            MagicData magic = MagicData.getPlayerMagicData(player);
            magic.getPlayerCooldowns().clearCooldowns();
            magic.setMana(1000);
            boolean started = spell.attemptInitiateCast(book, 1, helper.getLevel(), player,
                    CastSource.SPELLBOOK, true, "mainhand");
            helper.assertTrue(started, "enableSpellLocks=false still refused the cast");
            magic.getPlayerCooldowns().clearCooldowns();
            io.redspace.ironsspellbooks.api.util.Utils.serverSideCancelCast(player);
        } finally {
            cfg.enableSpellLocks = spellLocks;
            cfg.enableItemLocks = itemLocks;
            holder.lockItemList = saved;
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void anExplicitSpellAllowBeatsTheGeneratedFormula(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean spellLocks = cfg.enableSpellLocks;
        boolean schoolGating = cfg.ironsEnableSchoolGating;
        boolean itemLocks = cfg.enableItemLocks;
        var holder = HandlerLockItemsConfig.HANDLER.instance();
        var saved = holder.lockItemList;
        try {
            cfg.enableSpellLocks = true;
            cfg.enableItemLocks = true;
            cfg.ironsEnableSchoolGating = true;

            AbstractSpell spell = SpellRegistry.FIREBALL_SPELL.get();
            String spellId = spell.getSpellId();
            holder.lockItemList = new ArrayList<>(List.of(LockItem.unrestricted(spellId)));
            HandlerSkill.getSkill();

            helper.assertTrue(HandlerSkill.provenanceOf(spellId) == GateSource.EXPLICIT_RULE,
                    "an authored spell rule was not recorded as explicit: "
                            + HandlerSkill.provenanceOf(spellId));

            ServerPlayer player = player(helper, 1);
            ItemStack book = inscribedBook(spell);
            MagicData magic = MagicData.getPlayerMagicData(player);
            magic.getPlayerCooldowns().clearCooldowns();
            magic.setMana(1000);

            boolean started = spell.attemptInitiateCast(book, 1, helper.getLevel(), player,
                    CastSource.SPELLBOOK, true, "mainhand");
            helper.assertTrue(started,
                    "the generated formula overrode an explicit Allow for the spell");
            io.redspace.ironsspellbooks.api.util.Utils.serverSideCancelCast(player);
        } finally {
            cfg.enableSpellLocks = spellLocks;
            cfg.enableItemLocks = itemLocks;
            cfg.ironsEnableSchoolGating = schoolGating;
            holder.lockItemList = saved;
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void bookGatesComeFromCapacityRatherThanFromTheBooksName(GameTestHelper helper) {
        // The whole point of the 2.2.1 book work, checked against the real registry: ice must
        // outrank copper, a sidegrade must tie, and the flat-8 fallback must be gone.
        var provider = new IronsSpellbooksLockProvider();
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean lockItems = cfg.enableIronsSpellbooksLockItems;
        float multiplier = cfg.ironsLevelMultiplier;
        try {
            cfg.enableIronsSpellbooksLockItems = true;
            cfg.ironsLevelMultiplier = 1.0f;
            List<LockItem> generated = provider.generateLockItems();

            int copper = magic(generated, "irons_spellbooks:copper_spell_book");
            int iron = magic(generated, "irons_spellbooks:iron_spell_book");
            int ice = magic(generated, "irons_spellbooks:ice_spell_book");
            int dragonskin = magic(generated, "irons_spellbooks:dragonskin_spell_book");
            int legendary = magic(generated, "irons_spellbooks:legendary_spell_book");

            helper.assertTrue(copper > 0 && iron > copper, "copper/iron ordering lost: " + copper + "/" + iron);
            helper.assertTrue(ice > iron, "ice did not outrank iron: " + ice + " vs " + iron);
            helper.assertTrue(dragonskin == ice,
                    "ice and dragonskin are the same chassis but resolved differently: "
                            + ice + " vs " + dragonskin);
            helper.assertTrue(legendary < ice,
                    "legendary_spell_book still outranks ice on its name: " + legendary + " vs " + ice);

            // wimpy_spell_book has no capacity and no modifiers, so it must carry no rule at all.
            helper.assertTrue(generated.stream().noneMatch(r -> "irons_spellbooks:wimpy_spell_book".equals(r.Item)),
                    "an inert spellbook was gated");

            // Every generated book rule records why it is the number it is.
            var reasons = IronsSpellbooksLockProvider.reasons();
            helper.assertTrue(reasons.containsKey("irons_spellbooks:ice_spell_book")
                            && reasons.containsKey("irons_spellbooks:dragonskin_spell_book"),
                    "generated book gates shipped without recorded evidence");
        } finally {
            cfg.enableIronsSpellbooksLockItems = lockItems;
            cfg.ironsLevelMultiplier = multiplier;
        }
        helper.succeed();
    }

    private static int magic(List<LockItem> generated, String itemId) {
        return generated.stream().filter(r -> itemId.equals(r.Item)).findFirst()
                .map(r -> r.Skills.stream().filter(s -> "Magic".equalsIgnoreCase(s.Skill.toString()))
                        .mapToInt(s -> s.Level).max().orElse(0))
                .orElse(0);
    }

    private static String id(ItemStack stack) {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    /** A copper spellbook with one spell written in it, built without touching a player's book. */
    private static ItemStack inscribedBook(AbstractSpell spell) {
        ItemStack book = new ItemStack(ItemRegistry.COPPER_SPELL_BOOK.get());
        ISpellContainer.getOrCreate(book).mutableCopy();
        ((io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer) book.getItem())
                .initializeSpellContainer(book);
        ISpellContainer.get(book).addSpell(spell, 1, false, book);
        return book;
    }

    /** A connected player at one Magic, which no generated spell gate lets through. */
    private static ServerPlayer player(GameTestHelper helper, int magicLevel) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper,
                "gate_" + UUID.randomUUID().toString().substring(0, 8));
        SkillCapability.get(player).setSkillLevel(RegistrySkills.MAGIC.get(), magicLevel);
        // Iron's builds its spell configuration during native login/datapack synchronization;
        // without it every spell reports the default school and its rarity is not yet resolved.
        MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(
                helper.getLevel().getServer().getPlayerList(), player));
        return player;
    }
}
