package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerConvergenceItemsConfig;
import com.otectus.runicskills.network.ServerNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client packet to update static config options that needs to match server.
 * Must only be sent on player join.
 */
public class CommonConfigSyncCP {

    private final int skillFirstCostLevel;
    private final boolean dropLockedItems;
    private final boolean displayTitlesAsPrefix;

    // Passive
    private final float attackDamageValue;
    private final float attackKnockbackValue;
    private final float maxHealthValue;
    private final float knockbackResistanceValue;
    private final float movementSpeedValue;
    private final float projectileDamageValue;
    private final float armorValue;
    private final float armorToughnessValue;
    private final float attackSpeedValue;
    private final float entityReachValue;
    private final float blockReachValue;
    private final float breakSpeedValue;
    private final float beneficialEffectValue;
    private final float magicResistValue;
    private final float criticalDamageValue;
    private final float luckValue;

    // Skils
    private final float oneHandedAmplifier;
    private final int fightingSpiritBoost;
    private final int fightingSpiritDuration;
    private final int berserkerPercent;
    private final float athleticsModifier;
    private final int lionHeartPercent;
    private final int quickRepositionBoost;
    private final int quickRepositionDuration;
    private final int stealthMasteryUnSneakPercent;
    private final int stealthMasterySneakPercent;
    private final float stealthMasteryModifier;
    private final int counterAttackDuration;
    private final int counterAttackPercent;
    private final int diamondSkinBoost;
    private final float diamondSkinSneakAmplifier;
    private final int hagglerPercent;
    private final float alchemyManipulationAmplifier;
    private final float obsidianSmasherModifier;
    private final int treasureHunterProbability;
    private final List<String> treasureHunterItemList;
    private final int convergenceProbability;
    private final List<String> convergenceItemList;
    private final float lifeEaterModifier;
    private final float criticalRoll6Modifier;
    private final int criticalRoll1Probability;
    private final float luckyDropModifier;
    private final int luckyDropProbability;
    private final float limitBreakerAmplifier;
    private final int limitBreakerProbability;

    // Iron's Spells 'n Spellbooks Integration
    private final boolean ironsEnableSchoolGating;
    private final int ironsBaseSpellGatingLevel;
    private final float ironsSpellLevelScaleFactor;
    private final float ironsSpellPowerValue;
    private final float ironsMaxManaValue;
    private final float ironsCastTimeReductionValue;
    private final int manaEfficiencyPercent;
    private final int spellEchoProbability;
    private final int arcaneShieldPercent;
    private final boolean ironsEnableSpellDamageScaling;
    private final float ironsSpellDamageScalePerLevel;
    private final boolean ironsEnableCooldownReduction;
    private final float ironsCooldownReductionPerLevel;
    private final float ironsMaxCooldownReduction;
    private final boolean ironsEnableSpellLevelBonus;
    private final int ironsSpellLevelBonusThreshold;
    private final int ironsSpellLevelBonusThreshold2;
    private final boolean ironsEnableManaRegen;
    private final float ironsManaRegenPerMagicLevel;

    public CommonConfigSyncCP() {
        skillFirstCostLevel = HandlerCommonConfig.HANDLER.instance().skillFirstCostLevel;
        dropLockedItems = HandlerCommonConfig.HANDLER.instance().dropLockedItems;
        displayTitlesAsPrefix = HandlerCommonConfig.HANDLER.instance().displayTitlesAsPrefix;
        attackDamageValue = HandlerCommonConfig.HANDLER.instance().attackDamageValue;
        attackKnockbackValue = HandlerCommonConfig.HANDLER.instance().attackKnockbackValue;
        maxHealthValue = HandlerCommonConfig.HANDLER.instance().maxHealthValue;
        knockbackResistanceValue = HandlerCommonConfig.HANDLER.instance().knockbackResistanceValue;
        movementSpeedValue = HandlerCommonConfig.HANDLER.instance().movementSpeedValue;
        projectileDamageValue = HandlerCommonConfig.HANDLER.instance().projectileDamageValue;
        armorValue = HandlerCommonConfig.HANDLER.instance().armorValue;
        armorToughnessValue = HandlerCommonConfig.HANDLER.instance().armorToughnessValue;
        attackSpeedValue = HandlerCommonConfig.HANDLER.instance().attackSpeedValue;
        entityReachValue = HandlerCommonConfig.HANDLER.instance().entityReachValue;
        blockReachValue = HandlerCommonConfig.HANDLER.instance().blockReachValue;
        breakSpeedValue = HandlerCommonConfig.HANDLER.instance().breakSpeedValue;
        beneficialEffectValue = HandlerCommonConfig.HANDLER.instance().beneficialEffectValue;
        magicResistValue = HandlerCommonConfig.HANDLER.instance().magicResistValue;
        criticalDamageValue = HandlerCommonConfig.HANDLER.instance().criticalDamageValue;
        luckValue = HandlerCommonConfig.HANDLER.instance().luckValue;
        oneHandedAmplifier = HandlerCommonConfig.HANDLER.instance().oneHandedAmplifier;
        fightingSpiritBoost = HandlerCommonConfig.HANDLER.instance().fightingSpiritBoost;
        fightingSpiritDuration = HandlerCommonConfig.HANDLER.instance().fightingSpiritDuration;
        berserkerPercent = HandlerCommonConfig.HANDLER.instance().berserkerPercent;
        athleticsModifier = HandlerCommonConfig.HANDLER.instance().athleticsModifier;
        lionHeartPercent = HandlerCommonConfig.HANDLER.instance().lionHeartPercent;
        quickRepositionBoost = HandlerCommonConfig.HANDLER.instance().quickRepositionBoost;
        quickRepositionDuration = HandlerCommonConfig.HANDLER.instance().quickRepositionDuration;
        stealthMasteryUnSneakPercent = HandlerCommonConfig.HANDLER.instance().stealthMasteryUnSneakPercent;
        stealthMasterySneakPercent = HandlerCommonConfig.HANDLER.instance().stealthMasterySneakPercent;
        stealthMasteryModifier = HandlerCommonConfig.HANDLER.instance().stealthMasteryModifier;
        counterAttackDuration = HandlerCommonConfig.HANDLER.instance().counterAttackDuration;
        counterAttackPercent = HandlerCommonConfig.HANDLER.instance().counterAttackPercent;
        diamondSkinBoost = HandlerCommonConfig.HANDLER.instance().diamondSkinBoost;
        diamondSkinSneakAmplifier = HandlerCommonConfig.HANDLER.instance().diamondSkinSneakAmplifier;
        hagglerPercent = HandlerCommonConfig.HANDLER.instance().hagglerPercent;
        alchemyManipulationAmplifier = HandlerCommonConfig.HANDLER.instance().alchemyManipulationAmplifier;
        obsidianSmasherModifier = HandlerCommonConfig.HANDLER.instance().obsidianSmasherModifier;
        treasureHunterProbability = HandlerCommonConfig.HANDLER.instance().treasureHunterProbability;
        treasureHunterItemList = HandlerCommonConfig.HANDLER.instance().treasureHunterItemList;
        convergenceProbability = HandlerCommonConfig.HANDLER.instance().convergenceProbability;
        convergenceItemList = HandlerConvergenceItemsConfig.HANDLER.instance().convergenceItemList;
        lifeEaterModifier = HandlerCommonConfig.HANDLER.instance().lifeEaterModifier;
        criticalRoll6Modifier = HandlerCommonConfig.HANDLER.instance().criticalRoll6Modifier;
        criticalRoll1Probability = HandlerCommonConfig.HANDLER.instance().criticalRoll1Probability;
        luckyDropModifier = HandlerCommonConfig.HANDLER.instance().luckyDropModifier;
        luckyDropProbability = HandlerCommonConfig.HANDLER.instance().luckyDropProbability;
        limitBreakerAmplifier = HandlerCommonConfig.HANDLER.instance().limitBreakerAmplifier;
        limitBreakerProbability = HandlerCommonConfig.HANDLER.instance().limitBreakerProbability;
        ironsEnableSchoolGating = HandlerCommonConfig.HANDLER.instance().ironsEnableSchoolGating;
        ironsBaseSpellGatingLevel = HandlerCommonConfig.HANDLER.instance().ironsBaseSpellGatingLevel;
        ironsSpellLevelScaleFactor = HandlerCommonConfig.HANDLER.instance().ironsSpellLevelScaleFactor;
        ironsSpellPowerValue = HandlerCommonConfig.HANDLER.instance().ironsSpellPowerValue;
        ironsMaxManaValue = HandlerCommonConfig.HANDLER.instance().ironsMaxManaValue;
        ironsCastTimeReductionValue = HandlerCommonConfig.HANDLER.instance().ironsCastTimeReductionValue;
        manaEfficiencyPercent = HandlerCommonConfig.HANDLER.instance().manaEfficiencyPercent;
        spellEchoProbability = HandlerCommonConfig.HANDLER.instance().spellEchoProbability;
        arcaneShieldPercent = HandlerCommonConfig.HANDLER.instance().arcaneShieldPercent;
        ironsEnableSpellDamageScaling = HandlerCommonConfig.HANDLER.instance().ironsEnableSpellDamageScaling;
        ironsSpellDamageScalePerLevel = HandlerCommonConfig.HANDLER.instance().ironsSpellDamageScalePerLevel;
        ironsEnableCooldownReduction = HandlerCommonConfig.HANDLER.instance().ironsEnableCooldownReduction;
        ironsCooldownReductionPerLevel = HandlerCommonConfig.HANDLER.instance().ironsCooldownReductionPerLevel;
        ironsMaxCooldownReduction = HandlerCommonConfig.HANDLER.instance().ironsMaxCooldownReduction;
        ironsEnableSpellLevelBonus = HandlerCommonConfig.HANDLER.instance().ironsEnableSpellLevelBonus;
        ironsSpellLevelBonusThreshold = HandlerCommonConfig.HANDLER.instance().ironsSpellLevelBonusThreshold;
        ironsSpellLevelBonusThreshold2 = HandlerCommonConfig.HANDLER.instance().ironsSpellLevelBonusThreshold2;
        ironsEnableManaRegen = HandlerCommonConfig.HANDLER.instance().ironsEnableManaRegen;
        ironsManaRegenPerMagicLevel = HandlerCommonConfig.HANDLER.instance().ironsManaRegenPerMagicLevel;
    }

    @SuppressWarnings("unchecked")
    public CommonConfigSyncCP(FriendlyByteBuf buffer) {
        skillFirstCostLevel = buffer.readInt();
        dropLockedItems = buffer.readBoolean();
        displayTitlesAsPrefix = buffer.readBoolean();
        attackDamageValue = buffer.readFloat();
        attackKnockbackValue = buffer.readFloat();
        maxHealthValue = buffer.readFloat();
        knockbackResistanceValue = buffer.readFloat();
        movementSpeedValue = buffer.readFloat();
        projectileDamageValue = buffer.readFloat();
        armorValue = buffer.readFloat();
        armorToughnessValue = buffer.readFloat();
        attackSpeedValue = buffer.readFloat();
        entityReachValue = buffer.readFloat();
        blockReachValue = buffer.readFloat();
        breakSpeedValue = buffer.readFloat();
        beneficialEffectValue = buffer.readFloat();
        magicResistValue = buffer.readFloat();
        criticalDamageValue = buffer.readFloat();
        luckValue = buffer.readFloat();
        oneHandedAmplifier = buffer.readFloat();
        fightingSpiritBoost = buffer.readInt();
        fightingSpiritDuration = buffer.readInt();
        berserkerPercent = buffer.readInt();
        athleticsModifier = buffer.readFloat();
        lionHeartPercent = buffer.readInt();
        quickRepositionBoost = buffer.readInt();
        quickRepositionDuration = buffer.readInt();
        stealthMasteryUnSneakPercent = buffer.readInt();
        stealthMasterySneakPercent = buffer.readInt();
        stealthMasteryModifier = buffer.readFloat();
        counterAttackDuration = buffer.readInt();
        counterAttackPercent = buffer.readInt();
        diamondSkinBoost = buffer.readInt();
        diamondSkinSneakAmplifier = buffer.readFloat();
        hagglerPercent = buffer.readInt();
        alchemyManipulationAmplifier = buffer.readFloat();
        obsidianSmasherModifier = buffer.readFloat();

        treasureHunterProbability = buffer.readInt();
        treasureHunterItemList = buffer.readList(buf -> buf.readUtf(Short.MAX_VALUE));

        convergenceProbability = buffer.readInt();
        convergenceItemList = buffer.readList(buf -> buf.readUtf(Short.MAX_VALUE));

        lifeEaterModifier = buffer.readFloat();
        criticalRoll6Modifier = buffer.readFloat();
        criticalRoll1Probability = buffer.readInt();
        luckyDropModifier = buffer.readFloat();
        luckyDropProbability = buffer.readInt();
        limitBreakerAmplifier = buffer.readFloat();
        limitBreakerProbability = buffer.readInt();
        ironsEnableSchoolGating = buffer.readBoolean();
        ironsBaseSpellGatingLevel = buffer.readInt();
        ironsSpellLevelScaleFactor = buffer.readFloat();
        ironsSpellPowerValue = buffer.readFloat();
        ironsMaxManaValue = buffer.readFloat();
        ironsCastTimeReductionValue = buffer.readFloat();
        manaEfficiencyPercent = buffer.readInt();
        spellEchoProbability = buffer.readInt();
        arcaneShieldPercent = buffer.readInt();
        ironsEnableSpellDamageScaling = buffer.readBoolean();
        ironsSpellDamageScalePerLevel = buffer.readFloat();
        ironsEnableCooldownReduction = buffer.readBoolean();
        ironsCooldownReductionPerLevel = buffer.readFloat();
        ironsMaxCooldownReduction = buffer.readFloat();
        ironsEnableSpellLevelBonus = buffer.readBoolean();
        ironsSpellLevelBonusThreshold = buffer.readInt();
        ironsSpellLevelBonusThreshold2 = buffer.readInt();
        ironsEnableManaRegen = buffer.readBoolean();
        ironsManaRegenPerMagicLevel = buffer.readFloat();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeInt(this.skillFirstCostLevel);
        buffer.writeBoolean(this.dropLockedItems);
        buffer.writeBoolean(this.displayTitlesAsPrefix);
        buffer.writeFloat(this.attackDamageValue);
        buffer.writeFloat(this.attackKnockbackValue);
        buffer.writeFloat(this.maxHealthValue);
        buffer.writeFloat(this.knockbackResistanceValue);
        buffer.writeFloat(this.movementSpeedValue);
        buffer.writeFloat(this.projectileDamageValue);
        buffer.writeFloat(this.armorValue);
        buffer.writeFloat(this.armorToughnessValue);
        buffer.writeFloat(this.attackSpeedValue);
        buffer.writeFloat(this.entityReachValue);
        buffer.writeFloat(this.blockReachValue);
        buffer.writeFloat(this.breakSpeedValue);
        buffer.writeFloat(this.beneficialEffectValue);
        buffer.writeFloat(this.magicResistValue);
        buffer.writeFloat(this.criticalDamageValue);
        buffer.writeFloat(this.luckValue);
        buffer.writeFloat(this.oneHandedAmplifier);
        buffer.writeInt(this.fightingSpiritBoost);
        buffer.writeInt(this.fightingSpiritDuration);
        buffer.writeInt(this.berserkerPercent);
        buffer.writeFloat(this.athleticsModifier);
        buffer.writeInt(this.lionHeartPercent);
        buffer.writeInt(this.quickRepositionBoost);
        buffer.writeInt(this.quickRepositionDuration);
        buffer.writeInt(this.stealthMasteryUnSneakPercent);
        buffer.writeInt(this.stealthMasterySneakPercent);
        buffer.writeFloat(this.stealthMasteryModifier);
        buffer.writeInt(this.counterAttackDuration);
        buffer.writeInt(this.counterAttackPercent);
        buffer.writeInt(this.diamondSkinBoost);
        buffer.writeFloat(this.diamondSkinSneakAmplifier);
        buffer.writeInt(this.hagglerPercent);
        buffer.writeFloat(this.alchemyManipulationAmplifier);
        buffer.writeFloat(this.obsidianSmasherModifier);
        buffer.writeInt(this.treasureHunterProbability);
        buffer.writeCollection(this.treasureHunterItemList, (buf, s) -> buf.writeUtf(s, Short.MAX_VALUE));

        buffer.writeInt(this.convergenceProbability);
        buffer.writeCollection(this.convergenceItemList, (buf, s) -> buf.writeUtf(s, Short.MAX_VALUE));

        buffer.writeFloat(this.lifeEaterModifier);
        buffer.writeFloat(this.criticalRoll6Modifier);
        buffer.writeInt(this.criticalRoll1Probability);
        buffer.writeFloat(this.luckyDropModifier);
        buffer.writeInt(this.luckyDropProbability);
        buffer.writeFloat(this.limitBreakerAmplifier);
        buffer.writeInt(this.limitBreakerProbability);
        buffer.writeBoolean(this.ironsEnableSchoolGating);
        buffer.writeInt(this.ironsBaseSpellGatingLevel);
        buffer.writeFloat(this.ironsSpellLevelScaleFactor);
        buffer.writeFloat(this.ironsSpellPowerValue);
        buffer.writeFloat(this.ironsMaxManaValue);
        buffer.writeFloat(this.ironsCastTimeReductionValue);
        buffer.writeInt(this.manaEfficiencyPercent);
        buffer.writeInt(this.spellEchoProbability);
        buffer.writeInt(this.arcaneShieldPercent);
        buffer.writeBoolean(this.ironsEnableSpellDamageScaling);
        buffer.writeFloat(this.ironsSpellDamageScalePerLevel);
        buffer.writeBoolean(this.ironsEnableCooldownReduction);
        buffer.writeFloat(this.ironsCooldownReductionPerLevel);
        buffer.writeFloat(this.ironsMaxCooldownReduction);
        buffer.writeBoolean(this.ironsEnableSpellLevelBonus);
        buffer.writeInt(this.ironsSpellLevelBonusThreshold);
        buffer.writeInt(this.ironsSpellLevelBonusThreshold2);
        buffer.writeBoolean(this.ironsEnableManaRegen);
        buffer.writeFloat(this.ironsManaRegenPerMagicLevel);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            LocalPlayer localPlayer = (Minecraft.getInstance()).player;
            if(localPlayer != null){
                HandlerCommonConfig.HANDLER.instance().skillFirstCostLevel = this.skillFirstCostLevel;
                HandlerCommonConfig.HANDLER.instance().dropLockedItems = this.dropLockedItems;
                HandlerCommonConfig.HANDLER.instance().displayTitlesAsPrefix = displayTitlesAsPrefix;
                HandlerCommonConfig.HANDLER.instance().attackDamageValue = this.attackDamageValue;
                HandlerCommonConfig.HANDLER.instance().attackKnockbackValue = this.attackKnockbackValue;
                HandlerCommonConfig.HANDLER.instance().maxHealthValue = this.maxHealthValue;
                HandlerCommonConfig.HANDLER.instance().knockbackResistanceValue = this.knockbackResistanceValue;
                HandlerCommonConfig.HANDLER.instance().movementSpeedValue = this.movementSpeedValue;
                HandlerCommonConfig.HANDLER.instance().projectileDamageValue = this.projectileDamageValue;
                HandlerCommonConfig.HANDLER.instance().armorValue = this.armorValue;
                HandlerCommonConfig.HANDLER.instance().armorToughnessValue = this.armorToughnessValue;
                HandlerCommonConfig.HANDLER.instance().attackSpeedValue = this.attackSpeedValue;
                HandlerCommonConfig.HANDLER.instance().entityReachValue = this.entityReachValue;
                HandlerCommonConfig.HANDLER.instance().blockReachValue = this.blockReachValue;
                HandlerCommonConfig.HANDLER.instance().breakSpeedValue = this.breakSpeedValue;
                HandlerCommonConfig.HANDLER.instance().beneficialEffectValue = this.beneficialEffectValue;
                HandlerCommonConfig.HANDLER.instance().magicResistValue = this.magicResistValue;
                HandlerCommonConfig.HANDLER.instance().criticalDamageValue = this.criticalDamageValue;
                HandlerCommonConfig.HANDLER.instance().luckValue = this.luckValue;
                HandlerCommonConfig.HANDLER.instance().oneHandedAmplifier = this.oneHandedAmplifier;
                HandlerCommonConfig.HANDLER.instance().fightingSpiritBoost = this.fightingSpiritBoost;
                HandlerCommonConfig.HANDLER.instance().fightingSpiritDuration = this.fightingSpiritDuration;
                HandlerCommonConfig.HANDLER.instance().berserkerPercent = this.berserkerPercent;
                HandlerCommonConfig.HANDLER.instance().athleticsModifier = this.athleticsModifier;
                HandlerCommonConfig.HANDLER.instance().lionHeartPercent = this.lionHeartPercent;
                HandlerCommonConfig.HANDLER.instance().quickRepositionBoost = this.quickRepositionBoost;
                HandlerCommonConfig.HANDLER.instance().quickRepositionDuration = this.quickRepositionDuration;
                HandlerCommonConfig.HANDLER.instance().stealthMasteryUnSneakPercent = this.stealthMasteryUnSneakPercent;
                HandlerCommonConfig.HANDLER.instance().stealthMasterySneakPercent = this.stealthMasterySneakPercent;
                HandlerCommonConfig.HANDLER.instance().stealthMasteryModifier = this.stealthMasteryModifier;
                HandlerCommonConfig.HANDLER.instance().counterAttackDuration = this.counterAttackDuration;
                HandlerCommonConfig.HANDLER.instance().counterAttackPercent = this.counterAttackPercent;
                HandlerCommonConfig.HANDLER.instance().diamondSkinBoost = this.diamondSkinBoost;
                HandlerCommonConfig.HANDLER.instance().diamondSkinSneakAmplifier = this.diamondSkinSneakAmplifier;
                HandlerCommonConfig.HANDLER.instance().hagglerPercent = this.hagglerPercent;
                HandlerCommonConfig.HANDLER.instance().alchemyManipulationAmplifier = this.alchemyManipulationAmplifier;
                HandlerCommonConfig.HANDLER.instance().obsidianSmasherModifier = this.obsidianSmasherModifier;
                HandlerCommonConfig.HANDLER.instance().treasureHunterProbability = this.treasureHunterProbability;
                HandlerCommonConfig.HANDLER.instance().treasureHunterItemList = this.treasureHunterItemList;
                HandlerCommonConfig.HANDLER.instance().convergenceProbability = this.convergenceProbability;
                HandlerConvergenceItemsConfig.HANDLER.instance().convergenceItemList = this.convergenceItemList;
                HandlerCommonConfig.HANDLER.instance().lifeEaterModifier = this.lifeEaterModifier;
                HandlerCommonConfig.HANDLER.instance().criticalRoll6Modifier = this.criticalRoll6Modifier;
                HandlerCommonConfig.HANDLER.instance().criticalRoll1Probability = this.criticalRoll1Probability;
                HandlerCommonConfig.HANDLER.instance().luckyDropModifier = this.luckyDropModifier;
                HandlerCommonConfig.HANDLER.instance().luckyDropProbability = this.luckyDropProbability;
                HandlerCommonConfig.HANDLER.instance().limitBreakerAmplifier = this.limitBreakerAmplifier;
                HandlerCommonConfig.HANDLER.instance().limitBreakerProbability = this.limitBreakerProbability;
                HandlerCommonConfig.HANDLER.instance().ironsEnableSchoolGating = this.ironsEnableSchoolGating;
                HandlerCommonConfig.HANDLER.instance().ironsBaseSpellGatingLevel = this.ironsBaseSpellGatingLevel;
                HandlerCommonConfig.HANDLER.instance().ironsSpellLevelScaleFactor = this.ironsSpellLevelScaleFactor;
                HandlerCommonConfig.HANDLER.instance().ironsSpellPowerValue = this.ironsSpellPowerValue;
                HandlerCommonConfig.HANDLER.instance().ironsMaxManaValue = this.ironsMaxManaValue;
                HandlerCommonConfig.HANDLER.instance().ironsCastTimeReductionValue = this.ironsCastTimeReductionValue;
                HandlerCommonConfig.HANDLER.instance().manaEfficiencyPercent = this.manaEfficiencyPercent;
                HandlerCommonConfig.HANDLER.instance().spellEchoProbability = this.spellEchoProbability;
                HandlerCommonConfig.HANDLER.instance().arcaneShieldPercent = this.arcaneShieldPercent;
                HandlerCommonConfig.HANDLER.instance().ironsEnableSpellDamageScaling = this.ironsEnableSpellDamageScaling;
                HandlerCommonConfig.HANDLER.instance().ironsSpellDamageScalePerLevel = this.ironsSpellDamageScalePerLevel;
                HandlerCommonConfig.HANDLER.instance().ironsEnableCooldownReduction = this.ironsEnableCooldownReduction;
                HandlerCommonConfig.HANDLER.instance().ironsCooldownReductionPerLevel = this.ironsCooldownReductionPerLevel;
                HandlerCommonConfig.HANDLER.instance().ironsMaxCooldownReduction = this.ironsMaxCooldownReduction;
                HandlerCommonConfig.HANDLER.instance().ironsEnableSpellLevelBonus = this.ironsEnableSpellLevelBonus;
                HandlerCommonConfig.HANDLER.instance().ironsSpellLevelBonusThreshold = this.ironsSpellLevelBonusThreshold;
                HandlerCommonConfig.HANDLER.instance().ironsSpellLevelBonusThreshold2 = this.ironsSpellLevelBonusThreshold2;
                HandlerCommonConfig.HANDLER.instance().ironsEnableManaRegen = this.ironsEnableManaRegen;
                HandlerCommonConfig.HANDLER.instance().ironsManaRegenPerMagicLevel = this.ironsManaRegenPerMagicLevel;
                // Removed: HandlerCommonConfig.HANDLER.save() — server-synced values must not
                // overwrite the user's local config file.
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendToPlayer(Player player) {
        ServerNetworking.sendToPlayer(new CommonConfigSyncCP(), (ServerPlayer) player);
    }
}
