package xyz.iwolfking.woldsvaults.pouch.client;

import iskallia.vault.gear.attribute.ability.AbilityLevelAttribute;
import iskallia.vault.gear.trinket.TrinketEffect;
import iskallia.vault.gear.trinket.effects.AbilityAttributeTrinket;
import iskallia.vault.gear.trinket.effects.AttributeTrinket;
import iskallia.vault.gear.trinket.effects.DiceTrinket;
import iskallia.vault.gear.trinket.effects.ExplosionBlockPreventionTrinket;
import iskallia.vault.gear.trinket.effects.NightVisionTrinket;
import iskallia.vault.gear.trinket.effects.PotionEffectTrinket;
import iskallia.vault.gear.trinket.effects.ShadowCloakTrinket;
import iskallia.vault.gear.trinket.effects.VanillaAttributeTrinket;
import iskallia.vault.gear.trinket.effects.VaultExperienceTrinket;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import xyz.iwolfking.woldsvaults.api.lib.trinket.MultiAttributeTrinket;
import xyz.iwolfking.woldsvaults.client.init.ModKeybinds;
import xyz.iwolfking.woldsvaults.effect.trinkets.EffectOnHitTakenEffect;
import xyz.iwolfking.woldsvaults.effect.trinkets.SpeedLimitTrinketEffect;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRules;

/** Pouch-only summaries, using the synced effect config for variable values. */
public final class PouchDescriptions {
    private PouchDescriptions() {}

    public static Component describe(ItemStack stack) {
        return describe(stack, true);
    }

    public static Component describe(ItemStack stack, boolean includeStackSettings) {
        return new TextComponent(PouchRules.effects(stack).stream()
                .map(entry -> entry.trinket() instanceof SpeedLimitTrinketEffect && includeStackSettings
                        ? speedLimitDescription(stack).getString() : describe(entry.trinket()).getString())
                .filter(value -> !value.isBlank())
                .distinct().reduce((first, second) -> first + "\n" + second).orElse(""));
    }

    private static Component speedLimitDescription(ItemStack stack) {
        int capPercent = SpeedLimitTrinketEffect.getCapPercent(stack);
        Component limit = capPercent == 0 ? new TranslatableComponent("gui.woldsvaults.pouch.speed_uncapped")
                : new TextComponent(capPercent + "%");
        Component instruction = ModKeybinds.configureTrinket.isUnbound()
                ? new TranslatableComponent("gui.woldsvaults.pouch.speed_bind_key")
                : new TranslatableComponent("gui.woldsvaults.pouch.speed_configure",
                        ModKeybinds.configureTrinket.getTranslatedKeyMessage());
        return new TranslatableComponent("gui.woldsvaults.pouch.speed_limit", limit).append("\n").append(instruction);
    }

    private static Component describe(TrinketEffect<?> effect) {
        String key = "gui.woldsvaults.pouch.description." + String.valueOf(effect.getRegistryName()).replace(':', '.');
        if (!I18n.exists(key)) {
            String configured = effect.getTrinketConfig().getEffectText();
            return new TextComponent(configured == null ? "" : configured);
        }
        return new TranslatableComponent(key, arguments(effect));
    }

    private static Object[] arguments(TrinketEffect<?> effect) {
        TrinketEffect.Config config = effect.getConfig();
        if (config instanceof AttributeTrinket.Config<?> attribute) {
            double value = ((Number) attribute.toAttributeInstance().getValue()).doubleValue();
            return new Object[]{number(value * 100), number(value), number(1 + value)};
        }
        if (config instanceof VanillaAttributeTrinket.Config attribute) {
            return new Object[]{number(attribute.getModifier().amount * 100)};
        }
        if (config instanceof ExplosionBlockPreventionTrinket.Config cat) {
            return new Object[]{number(cat.getChance() * 100)};
        }
        if (config instanceof VaultExperienceTrinket.Config experience) {
            return new Object[]{number(experience.getExperienceIncrease() * 100)};
        }
        if (config instanceof PotionEffectTrinket.Config potion) {
            return new Object[]{potion.getAddedAmplifier()};
        }
        if (config instanceof NightVisionTrinket.Config goggles) {
            return new Object[]{number(goggles.getRadius())};
        }
        if (config instanceof AbilityAttributeTrinket.Config ability) {
            return new Object[]{((AbilityLevelAttribute) ability.toAttributeInstance().getValue()).getLevelChange()};
        }
        if (config instanceof DiceTrinket.DiceConfig dice) {
            return new Object[]{number(dice.getMinimumMultiplier() * 100), number(dice.getMaximumMultiplier() * 100)};
        }
        if (config instanceof ShadowCloakTrinket.Config cloak) {
            return new Object[]{number(cloak.getDisableLength() / 20.0)};
        }
        if (config instanceof EffectOnHitTakenEffect.Config onHit) {
            return new Object[]{number(onHit.getChance() * 100), number(onHit.getDuration() / 20.0)};
        }
        if (config instanceof MultiAttributeTrinket.Config<?> attributes) {
            return attributes.toAttributeInstances().stream()
                    .map(attribute -> number(((Number) attribute.getValue()).doubleValue() * 100)).toArray();
        }
        return new Object[0];
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
