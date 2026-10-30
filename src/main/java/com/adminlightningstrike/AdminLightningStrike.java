package com.adminlightningstrike;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side only. /strike <player> (permission level 2+).
 * Repeating visual-only lightning (no fire, no vanilla bolt damage) every 10 ticks.
 * Each strike removes real health directly from the target, never below 2.0 HP (1 heart).
 * Health is never restored or changed by this mod, and max health is never touched.
 */
public class AdminLightningStrike implements ModInitializer {
    public static final String MOD_ID = "adminlightningstrike";

    /** Ticks between strikes: 10 ticks = 0.5 seconds. */
    private static final int STRIKE_INTERVAL_TICKS = 10;
    /** Health removed per strike (2.0 = 1 heart). */
    private static final float DAMAGE_PER_STRIKE = 2.0F;
    /** The attack never takes the target below this value. */
    private static final float FLOOR_HEALTH = 2.0F;

    private static final class Attack {
        int ticksUntilNext = 0; // first strike on the next tick
    }

    private static final Map<UUID, Attack> ACTIVE = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("strike")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)) // OP level 2+
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return startAttack(ctx.getSource(), target);
                        }))
            )
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> tickAttacks(server));

        // Protection: while the attack is active, ignore all other damage to the target
        // (except an explicit /kill by an admin).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player
                && ACTIVE.containsKey(player.getUUID())
                && !source.is(DamageTypes.GENERIC_KILL)) {
                return false;
            }
            return true;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            ACTIVE.remove(handler.getPlayer().getUUID()));
    }

    private static int startAttack(CommandSourceStack source, ServerPlayer target) {
        if (target.isSpectator() || target.isCreative()) {
            source.sendFailure(Component.literal(target.getName().getString()
                + " is in creative/spectator mode and cannot be damaged."));
            return 0;
        }
        if (!target.isAlive() || target.getHealth() <= FLOOR_HEALTH) {
            source.sendFailure(Component.literal(target.getName().getString()
                + " is already at or below 1 heart. Nothing to do."));
            return 0;
        }
        if (ACTIVE.putIfAbsent(target.getUUID(), new Attack()) != null) {
            source.sendFailure(Component.literal(target.getName().getString()
                + " is already being struck."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Striking " + target.getName().getString() + "..."), true);
        return 1;
    }

    private static void tickAttacks(net.minecraft.server.MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, Attack>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Attack> entry = it.next();
            ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());

            if (target == null || !target.isAlive() || target.isSpectator() || target.isCreative()) {
                it.remove();
                continue;
            }

            float health = target.getHealth();
            if (health <= FLOOR_HEALTH) {
                it.remove();
                finish(target);
                continue;
            }

            Attack attack = entry.getValue();
            if (attack.ticksUntilNext > 0) {
                attack.ticksUntilNext--;
                continue;
            }
            attack.ticksUntilNext = STRIKE_INTERVAL_TICKS - 1;

            strike(target, health);

            if (target.getHealth() <= FLOOR_HEALTH) {
                it.remove();
                finish(target);
            }
        }
    }

    private static void strike(ServerPlayer target, float healthBefore) {
        ServerLevel level = (ServerLevel) target.level();

        // Visual-only bolt: renders and plays thunder, but never ignites blocks or entities.
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
            6.0F, 0.8F + level.getRandom().nextFloat() * 0.3F);

        // Real health loss, clamped so the result is never below FLOOR_HEALTH (cannot kill).
        float newHealth = Math.max(FLOOR_HEALTH, healthBefore - DAMAGE_PER_STRIKE);
        target.setHealth(newHealth);

        // Play the hurt animation/sound for visibility.
        level.broadcastEntityEvent(target, (byte) 2);
    }

    private static void finish(ServerPlayer target) {
        target.sendSystemMessage(Component.literal("The storm leaves you at 1 heart."));
    }
}
