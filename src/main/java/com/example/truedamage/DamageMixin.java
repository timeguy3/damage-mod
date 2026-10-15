package com.example.truedamage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class DamageMixin {
    @Unique
    private float truedamage$healthBefore;

    @Unique
    private float truedamage$absorptionBefore;

    @Inject(
            method = "hurtServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"
            )
    )
    private void truedamage$beforeHurt(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.truedamage$healthBefore = self.getHealth();
        this.truedamage$absorptionBefore = self.getAbsorptionAmount();
    }

    @Inject(
            method = "hurtServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void truedamage$afterHurt(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = (LivingEntity) (Object) this;
        ServerPlayer attacker = truedamage$resolveAttacker(source);
        if (attacker == null) {
            return;
        }

        float healthLost = Math.max(0.0F, this.truedamage$healthBefore - target.getHealth());
        float absorptionLost = Math.max(0.0F, this.truedamage$absorptionBefore - target.getAbsorptionAmount());
        float totalTaken = healthLost + absorptionLost;

        if (totalTaken <= 0.0001F) {
            return;
        }

        float heartsTaken = totalTaken / 2.0F;
        float remainingHealth = target.getHealth() + target.getAbsorptionAmount();

        MutableComponent message = Component.empty()
                .append(Component.literal("[Damage] ").withStyle(ChatFormatting.GRAY))
                .append(target.getDisplayName().copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" took ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format("%.2f", totalTaken)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(String.format(" (%.1f \u2764)", heartsTaken)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" [" + source.getMsgId() + "]").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.format(" | HP: %.1f/%.1f", remainingHealth, target.getMaxHealth()))
                        .withStyle(ChatFormatting.GREEN));

        attacker.sendSystemMessage(message);
    }

    @Unique
    private static ServerPlayer truedamage$resolveAttacker(DamageSource source) {
        Entity causing = source.getEntity();
        if (causing instanceof ServerPlayer player) {
            return player;
        }
        if (causing instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        if (source.getDirectEntity() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
