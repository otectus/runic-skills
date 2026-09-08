package com.otectus.runicskills.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.integration.common.IntegrationAvailability.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;

/** Read-only self inspection; detailed adapter evidence and export require operator permission. */
public final class IntegrationsCommand {
    private IntegrationsCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("integrations")
                .then(Commands.literal("status").executes(c -> status(c.getSource(), false)))
                .then(Commands.literal("validate").requires(s -> s.hasPermission(2)).executes(c -> status(c.getSource(), true)))
                .then(Commands.literal("dump").requires(s -> s.hasPermission(2)).executes(c -> dump(c.getSource())))
                .then(Commands.literal("explain").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.argument("action", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c,b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(IntegrationRuleIndex.Action.values()).map(a -> a.name().toLowerCase(java.util.Locale.ROOT)),b))
                                        .executes(c -> explain(c.getSource(),net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(c,"action"))))))
                .then(Commands.literal("inspect").then(Commands.literal("hand").executes(c -> {
                    var stack = c.getSource().getPlayerOrException().getMainHandItem();
                    var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    var module = owner(stack);
                    c.getSource().sendSuccess(() -> Component.literal(module.map(m -> id + ": " + m.id)
                            .orElse("No four-mod integration owns this item.")), false);
                    return 1;
                })));
    }
    private static java.util.Optional<IntegrationModule> owner(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return java.util.Optional.empty();
        if (com.otectus.runicskills.integration.tide.TideFishingOrigin.isNativeRod(stack.getItem()))
            return java.util.Optional.of(IntegrationModule.TIDE);
        var id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id==null ? java.util.Optional.empty() : IntegrationModule.owner(id.getNamespace());
    }
    private static int explain(CommandSourceStack source,net.minecraft.server.level.ServerPlayer player,String rawAction) {
        final IntegrationRuleIndex.Action action;
        try { action=IntegrationRuleIndex.Action.valueOf(rawAction.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { source.sendFailure(Component.literal("Unknown integration action.")); return 0; }
        var stack=player.getMainHandItem();
        var cap=com.otectus.runicskills.common.capability.SkillCapability.get(player);
        boolean usable=cap!=null && cap.canUseItemSilent(player,stack);
        source.sendSuccess(() -> Component.literal("Existing held-item use requirements: "+(usable?"met":"not met")+". Native restrictions still apply."),false);
        var owner=owner(stack);
        if (owner.isEmpty()) { source.sendSuccess(() -> Component.literal("No four-mod integration owns this held item."),false); return 1; }
        var module=owner.get();
        var rules=IntegrationRules.current();
        var decision=rules.index().resolve(module,ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(),
                stack.getTags().map(tag->tag.location().toString()).collect(java.util.stream.Collectors.toSet()),action,
                com.otectus.runicskills.handler.HandlerCommonConfig.HANDLER.instance().skillMaxLevel,false);
        source.sendSuccess(() -> Component.literal("Rule revision "+rules.revision()+": "+decision.rules()+"; proposed requirements "+decision.requirements()
                +". Integration gate enforcement is not yet implemented."),false);
        if (!decision.conflicts().isEmpty()) source.sendSuccess(() -> Component.literal("Conflicting rules: "+decision.conflicts()),false);
        // Report gate evidence separately from benefit hooks. Preparation support is not cast authorization.
        var gate=IntegrationRuntime.check(module,action==IntegrationRuleIndex.Action.ABILITY?Feature.ABILITIES:Feature.GATES,
                action==IntegrationRuleIndex.Action.ATTACK?Capability.ATTACK_GATE:Capability.ABILITY_GATE);
        source.sendSuccess(() -> Component.literal(module.id+" "+rawAction+" gate: "+gate.state()+". "+gate.explanation()),false);
        for (var power : com.otectus.runicskills.registry.RegistryPowers.getCachedValues()) {
            if (module!=IntegrationModule.TIDE || !com.otectus.runicskills.integration.tide.TidePowers.owns(power)
                    || cap==null || !cap.isPowerEquipped(power)) continue;
            var result=com.otectus.runicskills.registry.powers.PowerEligibility.evaluateActive(player,power);
            source.sendSuccess(() -> Component.translatable(power.getKey()).append(": ").append(result.describe(power)),false);
        }
        return 1;
    }
    private static int dump(CommandSourceStack source) {
        var root=new com.google.gson.JsonObject();
        root.addProperty("schema_version",1);
        root.addProperty("runic_version",net.minecraftforge.fml.ModList.get().getModContainerById("runicskills")
                .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown"));
        root.addProperty("configuration_revision",IntegrationRuntime.configurationRevision());
        root.addProperty("rule_revision",IntegrationRules.current().revision());
        root.addProperty("active_rule_count",IntegrationRules.current().index().rules().size());
        root.addProperty("dormant_rule_resource_count",IntegrationRules.current().dormantResources().size());
        root.addProperty("last_rule_reload_failure",IntegrationRules.lastFailure());
        var modules=new com.google.gson.JsonObject();
        for (var module : IntegrationModule.values()) {
            var object=new com.google.gson.JsonObject();
            object.addProperty("profile",module.profile);
            var evidence=IntegrationRuntime.localEvidence().get(module);
            if (evidence!=null) { object.addProperty("version",evidence.version()); object.addProperty("sha256",evidence.sha256()); }
            var features=new com.google.gson.JsonObject();
            for (var feature : Feature.values()) {
                boolean applicable=switch(feature) {
                    case GATES,ABILITIES,PERKS,POWERS -> true;
                    case WORKSHOP -> module==IntegrationModule.SIMPLY_SWORDS;
                    case MIMICRY -> module==IntegrationModule.SIMPLY_MORE;
                    case AQUA,ACTIVATIONS -> module==IntegrationModule.TOM;
                    case JOURNAL,MINIGAME,WEIGHTING -> module==IntegrationModule.TIDE;
                };
                if (!applicable) continue;
                var requested=IntegrationRuntime.request(module,feature);
                var setting=new com.google.gson.JsonObject(); setting.addProperty("mode",requested.mode()); setting.addProperty("enabled",requested.enabled());
                features.add(feature.name().toLowerCase(java.util.Locale.ROOT),setting);
            }
            object.add("requested_features",features);
            var capabilities=new com.google.gson.JsonObject();
            for (var capability : Capability.values()) capabilities.addProperty(capability.name().toLowerCase(java.util.Locale.ROOT),
                    evidence==null?"Mod absent":evidence.capabilities().getOrDefault(capability,"Not implemented or verified"));
            object.add("capability_evidence",capabilities); modules.add(module.id,object);
        }
        root.add("modules",modules);
        root.addProperty("claim_exhaustions",com.otectus.runicskills.common.actions.RunicActionContext.exhaustedRoots());
        root.addProperty("full_specification_complete",false);
        // Fixed report location; no inventories, UUIDs, native item NBT or arbitrary output paths.
        var path=source.getServer().getServerDirectory().toPath().resolve("debug/runicskills-integrations.json");
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n",java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { source.sendFailure(Component.literal("Could not write the integration report: "+e.getMessage())); return 0; }
        source.sendSuccess(() -> Component.literal("Wrote debug/runicskills-integrations.json"),false);
        return 1;
    }
    private static int status(CommandSourceStack source, boolean detailed) {
        for (IntegrationModule module : IntegrationModule.values()) {
            var evidence = IntegrationRuntime.localEvidence().get(module);
            Result state = IntegrationRuntime.check(module, Feature.PERKS, Capability.CLASSIFICATION);
            source.sendSuccess(() -> Component.literal(module.id + " classification: " + state.state() + ". " + state.explanation()), false);
            if (detailed && evidence != null) {
                source.sendSuccess(() -> Component.literal("  " + evidence.version() + " / " + evidence.sha256()), false);
                for (Capability capability : Capability.values()) {
                    String reason = evidence.capabilities().get(capability);
                    if (reason != null) source.sendSuccess(() -> Component.literal("  " + capability + ": "
                            + (reason.isEmpty() ? "available" : reason)), false);
                }
                source.sendSuccess(() -> Component.literal("  Unlisted native capabilities remain unavailable. Full integration support is not certified."), false);
            }
        }
        if (detailed) source.sendSuccess(() -> Component.literal("Action roots reaching their claim limit: "
                + com.otectus.runicskills.common.actions.RunicActionContext.exhaustedRoots()), false);
        if (detailed) source.sendSuccess(() -> Component.literal("Integration rules revision "+IntegrationRules.current().revision()
                +": "+IntegrationRules.current().index().rules().size()+" active; "+IntegrationRules.current().dormantResources().size()
                +" dormant resources; last failure: "+IntegrationRules.lastFailure()),false);
        if (detailed) source.sendSuccess(() -> Component.literal("Tide observed live casts: "
                + com.otectus.runicskills.integration.tide.TideCatchBridge.activeCount() + "; committed fish casts: "
                + com.otectus.runicskills.integration.tide.TideCatchBridge.fishCommits()), false);
        return 1;
    }
}
