package com.otectus.runicskills.common.advancements;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A Runic advancement criterion that fires for a player and one item.
 *
 * <p>One class, three registered instances: §14.5 asks for triggers on a first native assembly, a
 * first paid native repair and completion of The Great Work, and all three are the same shape — a
 * thing a player did, optionally narrowed to the item they did it with. Three near-identical
 * classes would only differ in the id they carry, which is a constructor argument.
 *
 * <p>The optional {@code item} condition is an ordinary vanilla {@link ItemPredicate}, so a pack
 * writes it exactly as it would in any other criterion:
 *
 * <pre>{@code
 * "criteria": {
 *   "first_tool": {
 *     "trigger": "runicskills:tinker_assembly",
 *     "conditions": { "item": { "items": ["tconstruct:pickaxe"] } }
 *   }
 * }
 * }</pre>
 *
 * <p><b>Only a real player action fires one.</b> The call sites are the committed, once-per-take
 * points inside the Tinkers' bridge, never a preview, a quote or a hopper transfer — §14.5 requires
 * that, and it is why these are triggered from the bridge rather than from an item's tooltip or a
 * menu render.
 */
public class RunicItemTrigger extends SimpleCriterionTrigger<RunicItemTrigger.Instance> {

    private final ResourceLocation id;

    public RunicItemTrigger(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player,
                                      DeserializationContext context) {
        return new Instance(id, player, ItemPredicate.fromJson(json.get("item")));
    }

    /** Fires this criterion for {@code player}, for whatever they just did it with. */
    public void trigger(ServerPlayer player, ItemStack stack) {
        if (player == null) return;
        ItemStack subject = stack == null ? ItemStack.EMPTY : stack;
        trigger(player, instance -> instance.matches(subject));
    }

    /** The conditions one advancement wrote against this criterion. */
    public static class Instance extends AbstractCriterionTriggerInstance {

        private final ItemPredicate item;

        public Instance(ResourceLocation id, ContextAwarePredicate player, ItemPredicate item) {
            super(id, player);
            this.item = item;
        }

        public boolean matches(ItemStack stack) {
            return item == ItemPredicate.ANY || item.matches(stack);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            json.add("item", item.serializeToJson());
            return json;
        }
    }
}
