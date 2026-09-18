package com.otectus.runicskills.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.*;
import com.otectus.runicskills.integration.lock.*;
import com.otectus.runicskills.integration.lock.auto.AutoGateEngine;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;

public final class LocksCommand {
    private LocksCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("locks")
                .then(Commands.literal("inspect").executes(c -> inspect(c.getSource(), LockAction.USE))
                        // Literal children are matched before argument children, so "inspect block"
                        // and "inspect spell" cannot be swallowed by the action word below -- and
                        // neither word is a LockAction, so nothing that used to parse stops parsing.
                        .then(Commands.literal("block")
                                .executes(c -> inspectBlock(c.getSource(), null))
                                .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(c -> inspectBlock(c.getSource(),
                                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos"))))
                                .then(Commands.argument("id", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKeys(), b))
                                        .executes(c -> inspectBlockId(c.getSource(),
                                                net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "id")))))
                        .then(Commands.literal("spell")
                                .then(Commands.argument("id", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .executes(c -> inspectSpell(c.getSource(),
                                                net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "id"), 1))
                                        .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                                .executes(c -> inspectSpell(c.getSource(),
                                                        net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "id"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "level"))))))
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(LockAction.values())
                                        .map(a -> a.name().toLowerCase(java.util.Locale.ROOT)), b))
                                .executes(c -> {
                                    try { return inspect(c.getSource(), LockAction.valueOf(StringArgumentType.getString(c, "action").toUpperCase(java.util.Locale.ROOT))); }
                                    catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.literal("Unknown lock action.")); return 0; }
                                })))
                .then(Commands.literal("explain")
                        .requires(source -> source.hasPermission(2))
                        // greedyString, not string(): an unquoted Brigadier word stops at the
                        // colon, so "explain item:minecraft:iron_sword" would not parse and an
                        // operator would have to quote every id they paste out of their config.
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(
                                                ForgeRegistries.ITEMS.getKeys().stream()
                                                        .map(id -> "item:" + id),
                                                ForgeRegistries.BLOCKS.getKeys().stream()
                                                        .map(id -> "block:" + id)), b))
                                .executes(c -> explain(c.getSource(),
                                        StringArgumentType.getString(c, "target").trim()))))
                .then(Commands.literal("preview").requires(source -> source.hasPermission(2))
                        .executes(c -> preview(c.getSource())))
                .then(Commands.literal("coverage").requires(source -> source.hasPermission(2))
                        .executes(c -> coverage(c.getSource())))
                .then(Commands.literal("apply-preview").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("token", StringArgumentType.word())
                                .executes(c -> applyPreview(c.getSource(),
                                        StringArgumentType.getString(c, "token")))))
                .then(Commands.literal("representation").executes(c -> representation(c.getSource())))
                .then(Commands.literal("audit").requires(s -> s.hasPermission(2)).executes(c -> {
                    try { LockAudit.write(c.getSource().getServer()); }
                    catch (java.io.IOException e) { c.getSource().sendFailure(Component.literal("Could not write lock audit: " + e.getMessage())); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("Wrote debug/runicskills-locks.json (loaded registry and recipes, no player data)."), false);
                    return 1;
                }));
    }
    /**
     * Who owns the stack-count representation, which of this mod's count hooks are live, what the
     * format can carry, and whether Pack Mule capacity is available or deferred.
     *
     * <p>Answers with plain literals like the rest of this command, and works from the console: the
     * held-item comparison is added only when a player ran it. The first three lines are startup
     * facts, so they are the same for every caller and identical to the startup log line.
     */
    private static int representation(CommandSourceStack source) {
        var provider = com.otectus.runicskills.common.inventory.StackRepresentationProvider.selected();
        source.sendSuccess(() -> Component.literal("Stack representation: " + provider + " — "
                + com.otectus.runicskills.common.inventory.StackRepresentationProvider.selectionDetail()), false);
        source.sendSuccess(() -> Component.literal("Runic count hooks: MixItemStackCount "
                + (provider.ownsNbtCount() ? "applied" : "not applied") + ", MixFriendlyByteBuf "
                + (provider.ownsNetworkCount() ? "applied" : "not applied")), false);
        source.sendSuccess(() -> Component.literal("Bounds: representable up to " + provider.maxRepresentableCount()
                + " (the Runic format's own limit is "
                + com.otectus.runicskills.common.inventory.StackCapacityMath.MAX_SERIALIZED_COUNT + ")"), false);
        String deferral = com.otectus.runicskills.common.inventory.PlayerStackPolicy.deferralReason();
        source.sendSuccess(() -> Component.literal("Pack Mule capacity: "
                + (deferral.isEmpty() ? "available" : "deferred — " + deferral)), false);
        var player = source.getPlayer();
        if (player != null) {
            var stack = player.getMainHandItem();
            String id = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
            int nativeMax = stack.getMaxStackSize();
            int granted = com.otectus.runicskills.common.inventory.PlayerStackPolicy.capacity(player, stack);
            source.sendSuccess(() -> Component.literal("Held " + id + ": count " + stack.getCount()
                    + ", native maximum " + nativeMax + ", capacity in a player slot " + granted
                    + (granted > nativeMax ? " (Pack Mule rank "
                            + com.otectus.runicskills.common.inventory.PlayerStackPolicy.rank(player) + ")" : "")), false);
        }
        return 1;
    }
    private static int inspect(CommandSourceStack source, LockAction action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException(); var stack = player.getMainHandItem();
        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        var snapshot = HandlerSkill.snapshot();
        source.sendSuccess(() -> Component.literal(id + " / " + action + ": locks "
                + (HandlerCommonConfig.HANDLER.instance().enableItemLocks ? "enabled" : "disabled")
                + ", revision " + snapshot.revision() + ", source " + snapshot.sources().getOrDefault(id, "unhandled")
                + ", requirements " + LockAudit.requirements(id)), false);
        var decision = LockProviderRegistry.resolveStack(player, stack, action);
        decision.ifPresent(d -> source.sendSuccess(() -> Component.literal("Stack rules " + d.matchedRuleIds()
                + ": " + d.requirements() + "; uncertainty: " + d.unsupportedFacts()), false));
        var cap = SkillCapability.get(player);
        source.sendSuccess(() -> Component.literal("Action allowed by Runic Skills: " + (cap == null || cap.canUseItemSilent(player, stack, action))), false);
        return 1;
    }

    /**
     * Why one target has the requirement it has: the winning layer, the candidates it suppressed,
     * the native evidence behind an inferred one, and the uncertainty in it.
     *
     * <p>Accepts a typed target ({@code block:minecraft:anvil}) or a bare id, which is read as the
     * untyped key the shipped rule table has always used. Both spellings resolve, because an
     * operator who reads a bare id out of their config should be able to paste it here.
     */
    private static int explain(CommandSourceStack source, String rawTarget) {
        GateTarget target = GateTarget.parse(rawTarget);
        if (target == null) {
            source.sendFailure(Component.literal("Not a resource id: " + rawTarget));
            return 0;
        }
        var snapshot = HandlerSkill.snapshot();
        source.sendSuccess(() -> Component.literal(target + ": winning layer "
                + snapshot.sourceOf(target).key() + ", source "
                + snapshot.sources().getOrDefault(target.legacyKey(), "unhandled")
                + ", requirements " + LockAudit.requirements(target.legacyKey())), false);
        for (var rule : snapshot.typedRulesFor(target)) {
            source.sendSuccess(() -> Component.literal("  typed rule " + rule), false);
        }
        var catalog = AutoGateEngine.catalog();
        // Both spellings are looked up: a bare id parses as UNTYPED, and the engine records its
        // evidence under the domain it actually examined.
        var evidence = catalog.evidenceFor(target.toString())
                .or(() -> catalog.evidenceFor("item:" + target.legacyKey()))
                .or(() -> catalog.evidenceFor("block:" + target.legacyKey()));
        if (evidence.isPresent()) {
            source.sendSuccess(() -> Component.literal("  inference: " + evidence.get().summary()), false);
            if (!evidence.get().rejectedAlternatives().isEmpty()) {
                source.sendSuccess(() -> Component.literal("  rejected alternatives: "
                        + evidence.get().rejectedAlternatives()), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("  inference: not considered ("
                    + AutoGateEngine.status() + ")"), false);
        }
        for (var candidate : snapshot.audit()) {
            if (!candidate.item().equals(target.legacyKey()) && !candidate.item().equals(target.toString())) continue;
            source.sendSuccess(() -> Component.literal("  candidate " + candidate.provider() + "/"
                    + candidate.source() + ": " + candidate.outcome() + " " + candidate.requirements()
                    + " scaling " + candidate.scaling()
                    + (candidate.selected() ? " [selected]" : " [suppressed]")), false);
        }
        return 1;
    }

    /** Builds a candidate catalog and reports the difference, without publishing anything. */
    private static int preview(CommandSourceStack source) {
        var server = source.getServer();
        var cfg = HandlerCommonConfig.HANDLER.instance();
        var prior = HandlerSkill.priorDecisions();
        var snapshot = HandlerSkill.snapshot();
        var candidate = AutoGateEngine.buildPreview(server, cfg, prior, snapshot.rules(), snapshot.sources());
        var live = AutoGateEngine.catalog();
        var before = live.byTarget();
        var after = candidate.byTarget();
        long added = after.keySet().stream().filter(t -> !before.containsKey(t)).count();
        long removed = before.keySet().stream().filter(t -> !after.containsKey(t)).count();
        long changed = after.entrySet().stream().filter(e -> before.containsKey(e.getKey())
                && !before.get(e.getKey()).requirements().equals(e.getValue().requirements())).count();
        source.sendSuccess(() -> Component.literal("Preview " + AutoGateEngine.shortDigest(candidate.digest())
                + ": " + candidate.rules().size() + " inferred rule(s) — " + added + " added, "
                + removed + " removed, " + changed + " changed against the published catalog."), false);
        source.sendSuccess(() -> Component.literal("  " + candidate.diagnostics()), false);
        source.sendSuccess(() -> Component.literal("  outcomes " + candidate.outcomeCounts()), false);
        source.sendSuccess(() -> Component.literal("  Nothing was published. Run /skills locks "
                + "apply-preview " + AutoGateEngine.shortDigest(candidate.digest()) + " to accept it."), false);
        return 1;
    }

    /** Publishes an unchanged, validated preview and writes it as the accepted catalog. */
    private static int applyPreview(CommandSourceStack source, String token) {
        var cfg = HandlerCommonConfig.HANDLER.instance();
        var result = AutoGateEngine.applyPreview(source.getServer(), token,
                AutoGateEngine.currentFingerprints(cfg, HandlerSkill.priorDecisions()));
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        // Republish the rule table so the accepted catalog is the one clients are told about.
        HandlerSkill.getSkill();
        com.otectus.runicskills.network.packet.client.ConfigSyncCP.sendToAllPlayers();
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    /**
     * Which gate sources are active, and what the engine did with everything it looked at.
     *
     * <p>The first block answers the question §13.2 asks for a command to answer: "which setting
     * controls this restriction?" Disabling universal inference is not the same as disabling locks,
     * and an operator has to be able to see the difference without reading the config file.
     */
    private static int coverage(CommandSourceStack source) {
        var cfg = HandlerCommonConfig.HANDLER.instance();
        var catalog = AutoGateEngine.catalog();
        source.sendSuccess(() -> Component.literal("Active gate sources: item locks "
                + (cfg.enableItemLocks ? "on" : "off") + " (enableItemLocks), spell locks "
                + (cfg.enableSpellLocks ? "on" : "off") + " (enableSpellLocks), automatic gates "
                + (cfg.enableAutoGates ? "on" : "off") + " (enableAutoGates), mode " + cfg.autoGateMode
                + ", threshold " + cfg.autoGateMinimumConfidence), false);
        source.sendSuccess(() -> Component.literal("  domains: items " + cfg.autoGateItems
                + ", blocks " + cfg.autoGateBlocks + ", spells " + cfg.autoGateSpells
                + "; actions: crafting " + cfg.autoGateCrafting + ", placement " + cfg.autoGatePlacement
                + ", harvest " + cfg.autoGateHarvestBlocks), false);
        source.sendSuccess(() -> Component.literal("  authored gate rules: "
                + GateRuleIndex.get().size() + " (revision " + GateRuleIndex.get().revision()
                + "), inference exclusions " + GateRulesLoader.inferenceExclusions().size()), false);
        source.sendSuccess(() -> Component.literal("Generated catalog: " + AutoGateEngine.status()), false);
        source.sendSuccess(() -> Component.literal("  outcomes " + catalog.outcomeCounts()), false);
        java.util.Map<String, Integer> byNamespace = new java.util.TreeMap<>();
        java.util.Map<String, Integer> byRole = new java.util.TreeMap<>();
        for (var row : catalog.evidence()) {
            if (!row.outcome().producedRule()) continue;
            String id = row.target().substring(row.target().indexOf(':') + 1);
            byNamespace.merge(id.substring(0, Math.max(0, id.indexOf(':'))), 1, Integer::sum);
            byRole.merge(row.role().key(), 1, Integer::sum);
        }
        source.sendSuccess(() -> Component.literal("  inferred rules by namespace " + byNamespace), false);
        source.sendSuccess(() -> Component.literal("  inferred rules by role " + byRole), false);
        return 1;
    }

    /** The targeted block, a block at a position, or a block id — separately from the held item. */
    private static int inspectBlock(CommandSourceStack source, net.minecraft.core.BlockPos pos)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (pos == null) {
            var player = source.getPlayerOrException();
            var hit = player.pick(8, 0, false);
            if (!(hit instanceof net.minecraft.world.phys.BlockHitResult block)) {
                source.sendFailure(Component.literal("You are not looking at a block."));
                return 0;
            }
            pos = block.getBlockPos();
        }
        var state = source.getLevel().getBlockState(pos);
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            source.sendFailure(Component.literal("That block is not registered."));
            return 0;
        }
        return reportBlock(source, id);
    }

    private static int inspectBlockId(CommandSourceStack source, net.minecraft.resources.ResourceLocation id) {
        if (!net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(id)) {
            source.sendFailure(Component.literal("No such block: " + id));
            return 0;
        }
        return reportBlock(source, id);
    }

    private static int reportBlock(CommandSourceStack source, net.minecraft.resources.ResourceLocation id) {
        GateTarget target = GateTarget.block(id);
        var snapshot = HandlerSkill.snapshot();
        source.sendSuccess(() -> Component.literal(target + ": layer " + snapshot.sourceOf(target).key()
                + ", id-table requirements " + LockAudit.requirements(id.toString())), false);
        var typed = snapshot.typedRulesFor(target);
        if (typed.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  no typed block rule; the action-blind id "
                    + "table decides, as it always has."), false);
        } else {
            for (var rule : typed) source.sendSuccess(() -> Component.literal("  " + rule), false);
        }
        var player = source.getPlayer();
        if (player != null) {
            var cap = SkillCapability.get(player);
            if (cap != null) {
                for (LockAction action : new LockAction[]{LockAction.INTERACT_BLOCK,
                        LockAction.PLACE_BLOCK, LockAction.MINE_BLOCK}) {
                    boolean allowed = cap.canUseBlock(player, net.minecraftforge.registries
                            .ForgeRegistries.BLOCKS.getValue(id), action);
                    source.sendSuccess(() -> Component.literal("  " + action + ": "
                            + (allowed ? "allowed" : "denied")), false);
                }
            }
        }
        AutoGateEngine.catalog().evidenceFor("block:" + id).ifPresent(row ->
                source.sendSuccess(() -> Component.literal("  inference: " + row.summary()), false));
        return 1;
    }

    /** One spell's requirement, the model that produced it, and whether this player qualifies. */
    private static int inspectSpell(CommandSourceStack source,
                                    net.minecraft.resources.ResourceLocation id, int level) {
        GateTarget target = GateTarget.spell(id);
        var cfg = HandlerCommonConfig.HANDLER.instance();
        var snapshot = HandlerSkill.snapshot();
        source.sendSuccess(() -> Component.literal(target + " at native level " + level
                + ": spell locks " + (cfg.enableSpellLocks ? "on" : "off")
                + ", model " + cfg.ironsSpellGateModel + ", generator "
                + (cfg.ironsEnableSchoolGating ? "on" : "off")), false);
        for (var rule : snapshot.typedRulesFor(target)) {
            source.sendSuccess(() -> Component.literal("  typed rule " + rule), false);
        }
        var legacy = LockAudit.requirements(id.toString());
        source.sendSuccess(() -> Component.literal("  id-table requirements " + legacy
                + ", layer " + snapshot.sourceOf(target).key()), false);
        // Presence-checked before the class is named. IronsSpellGate catches the LinkageError and
        // degrades correctly, but resolving it on a server with no Iron's still prints a
        // NoClassDefFoundError stack trace into the log, and an absence profile whose log contains
        // a missing-class error is indistinguishable from one that is actually broken.
        if (!net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            source.sendSuccess(() -> Component.literal(
                    "  metadata model: unavailable (Iron's Spells is not installed)"), false);
            return 1;
        }
        var metadata = com.otectus.runicskills.integration.irons.IronsSpellGate.requirement(id.toString(), level);
        source.sendSuccess(() -> Component.literal("  metadata model: "
                + (metadata.isPresent() ? "Magic " + metadata.getAsInt()
                        : "abstained (no native metadata for this spell)")), false);
        return 1;
    }
}
