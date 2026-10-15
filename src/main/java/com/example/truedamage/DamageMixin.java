package com.example.truedamage;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class DamageMixin {
    @Unique
    private static boolean truedamage$enabled = true;

    @Unique
    private float truedamage$healthBefore;

    @Unique
    private float truedamage$absorptionBefore;

    @Inject(method = "tick", at = @At("HEAD"))
    private void truedamage$registerCommand(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
            var commands = serverLevel.getServer().getCommands();
            if (commands.getDispatcher().getRoot().getChild("damagechat") == null) {
                commands.getDispatcher().register(
                        Commands.literal("damagechat")
                                .executes(ctx -> {
                                    truedamage$enabled = !truedamage$enabled;
                                    truedamage$sendStatus(ctx.getSource().getEntity());
                                    return 1;
                                })
                                .then(Commands.literal("on").executes(ctx -> {
                                    truedamage$enabled = true;
                                    truedamage$sendStatus(ctx.getSource().getEntity());
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    truedamage$enabled = false;
                                    truedamage$sendStatus(ctx.getSource().getEntity());
                                    return 1;
                                }))
                );
                commands.sendCommands(player);
            }
        }
    }

    @Unique
    private static void truedamage$sendStatus(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            MutableComponent status = Component.empty()
                    .append(Component.literal("[Damage] ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(truedamage$enabled ? "Damage messages turned ON" : "Damage messages turned OFF")
                            .withStyle(truedamage$enabled ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
            player.sendSystemMessage(status);
        }
    }

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
        if (!truedamage$enabled) {
            return;
        }

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
