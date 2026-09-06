package com.otectus.runicskills.integration.tconstruct;

import com.google.gson.JsonObject;
import com.otectus.runicskills.common.util.ContainerInteraction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tinker Station service that fits a {@code runicskills:keystone}, and its cost.
 *
 * <p><b>Why a real station recipe.</b> §10.4 asks for a service the player performs at the station,
 * paying materials, on a tool the station validates — which is a description of a
 * {@code ITinkerStationRecipe}. Writing it as one means the material cost is consumed by the
 * station's own input shrink, the tool is rebuilt by the station's own commit, the error text
 * appears in the station's own error line, and the whole thing is a datapack file a pack can
 * retune. A bespoke button, or a right-click handler, would have had to reimplement each of those
 * and would have got the ordering wrong somewhere.
 *
 * <p><b>What it costs.</b> Whatever the JSON says; the shipped recipe is §10.2's one netherite
 * ingot and one amethyst shard. The ingredients are matched against the station's input slots and
 * every one of them must be filled by exactly one input, with no unmatched input left over — so
 * the cost cannot be avoided by putting the materials somewhere else, and the default
 * {@code updateInputs} then shrinks each input by one at commit. There is no free path: this recipe
 * is the only thing that adds the modifier.
 *
 * <p><b>What it refuses.</b> A tool outside {@code runicskills:keystone_eligible} does not match at
 * all, so the station carries on looking for another recipe. A tool that already carries a keystone
 * matches and fails with a message, because silence there reads as "the materials are wrong". An
 * ineligible smith fails the same way when one is identifiable — see {@link KeystoneService} for
 * why that check cannot be the only one, and {@link TConstructStationBridge#allowsTake} for the one
 * that is authoritative.
 */
public class KeystoneStationRecipe implements ITinkerStationRecipe {

    private final ResourceLocation id;

    /** The material cost, one stack each, matched against the station's inputs. */
    private final List<Ingredient> inputs;

    public KeystoneStationRecipe(ResourceLocation id, List<Ingredient> inputs) {
        this.id = id;
        this.inputs = List.copyOf(inputs);
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TConstructBootstrap.KEYSTONE_RECIPE.get();
    }

    @Override
    public boolean matches(ITinkerStationContainer inventory, Level level) {
        return KeystoneService.isEligibleTool(inventory.getTinkerableStack())
                && matchesInputs(inventory);
    }

    /**
     * The tool this take would produce, or the reason there is none.
     *
     * <p>Works on a copy of the tool rather than on {@code getTinkerable()}, which reads the
     * station's own input stack: this method runs on every preview refresh, and mutating the input
     * would fit the keystone the moment the materials were laid out, before anybody paid for it.
     */
    @Override
    public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inventory,
                                                          RegistryAccess access) {
        ItemStack stack = inventory.getTinkerableStack();
        if (!KeystoneService.isEligibleTool(stack) || !matchesInputs(inventory)) {
            return RecipeResult.pass();
        }

        ToolStack tool = ToolStack.copyFrom(stack);
        if (KeystoneService.alreadyFitted(tool)) {
            return RecipeResult.failure(
                    Component.translatable("message.runicskills.tconstruct.keystone.already"));
        }

        // The smith, when the preview happens to be computed inside somebody's click. Usually it is
        // not, and this is null; the take guard is what makes that safe.
        Player smith = ContainerInteraction.currentPlayer();
        Component refusal = KeystoneService.refusal(smith);
        if (refusal != null) return RecipeResult.failure(refusal);

        tool.addModifier(KeystoneModifier.ID, 1);
        tool.rebuildStats();
        Component invalid = tool.tryValidate();
        if (invalid != null) return RecipeResult.failure(invalid);
        return ITinkerStationRecipe.success(tool, inventory);
    }

    /**
     * Whether the station's inputs are exactly this recipe's cost.
     *
     * <p>Greedy, and that is sufficient rather than lucky: the ingredient list is two entries long
     * and the check requires every input to be claimed as well as every ingredient to be filled, so
     * a pack that wrote two overlapping ingredients would get a refusal rather than a discount.
     */
    private boolean matchesInputs(ITinkerStationContainer inventory) {
        List<ItemStack> present = new ArrayList<>();
        for (int slot = 0; slot < inventory.getInputCount(); slot++) {
            ItemStack input = inventory.getInput(slot);
            if (!input.isEmpty()) present.add(input);
        }
        if (present.size() != this.inputs.size()) return false;

        for (Ingredient ingredient : this.inputs) {
            boolean matched = false;
            for (int index = 0; index < present.size(); index++) {
                if (ingredient.test(present.get(index))) {
                    present.remove(index);
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return present.isEmpty();
    }

    /**
     * Reads the recipe from JSON and from the network.
     *
     * <p>Hand-written rather than built on Tinkers' loadable machinery: the recipe has one field,
     * and a hand-written serializer is a shape that does not move between 3.11 and 3.12.
     */
    public static class Serializer implements RecipeSerializer<KeystoneStationRecipe> {

        @Override
        public KeystoneStationRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<Ingredient> inputs = new ArrayList<>();
            for (var element : GsonHelper.getAsJsonArray(json, "inputs")) {
                inputs.add(Ingredient.fromJson(element));
            }
            if (inputs.isEmpty()) {
                throw new com.google.gson.JsonSyntaxException(
                        "A keystone recipe with no inputs would be a free permanent upgrade slot");
            }
            return new KeystoneStationRecipe(id, inputs);
        }

        @Override
        public KeystoneStationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            List<Ingredient> inputs = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                inputs.add(Ingredient.fromNetwork(buffer));
            }
            return new KeystoneStationRecipe(id, inputs);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, KeystoneStationRecipe recipe) {
            buffer.writeVarInt(recipe.inputs.size());
            for (Ingredient ingredient : recipe.inputs) {
                ingredient.toNetwork(buffer);
            }
        }
    }
}
