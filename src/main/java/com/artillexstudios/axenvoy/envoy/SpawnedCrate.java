package com.artillexstudios.axenvoy.envoy;

import com.artillexstudios.axapi.hologram.Hologram;
import com.artillexstudios.axapi.hologram.HologramFactory;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axenvoy.AxEnvoyPlugin;
import com.artillexstudios.axenvoy.integrations.blocks.BlockIntegration;
import com.artillexstudios.axenvoy.rewards.Reward;
import com.artillexstudios.axenvoy.user.User;
import com.artillexstudios.axenvoy.utils.FallingBlockChecker;
import com.artillexstudios.axenvoy.utils.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SpawnedCrate {
    public static final NamespacedKey FIREWORK_KEY = new NamespacedKey(AxEnvoyPlugin.getInstance(), "axenvoy_firework");
    private final Envoy parent;
    private final CrateType handle;
    private Location finishLocation;
    private FallingBlock fallingBlock;
    private Vex vex;
    private Hologram hologram;
    private int tick = 0;
    private int health;
    private int maxHealth;

    public SpawnedCrate(@NotNull Envoy parent, @NotNull CrateType handle, @NotNull Location location) {
        this.health = handle.getConfig().REQUIRED_INTERACTION_AMOUNT;
        this.maxHealth = health;
        this.parent = parent;
        this.handle = handle;
        this.finishLocation = location;
        this.parent.getSpawnedCrates().add(this);

        Scheduler.get().runAt(location, task -> {
            List<Entity> nearby;
            if (handle.getConfig().FALLING_BLOCK_ENABLED) {
                nearby = location.getWorld().getNearbyEntities(location, Bukkit.getServer().getSimulationDistance() * 16, Bukkit.getServer().getSimulationDistance() * 16, Bukkit.getServer().getSimulationDistance() * 16).stream().filter(entity -> entity.getType() == EntityType.PLAYER).toList();
            } else {
                nearby = Collections.emptyList();
            }

            if (!handle.getConfig().FALLING_BLOCK_ENABLED || nearby.isEmpty()) {
                land(location);
                return;
            }

            Location spawnAt = location.clone();
            spawnAt.add(0.5, this.handle.getConfig().FALLING_BLOCK_HEIGHT, 0.5);
            vex = location.getWorld().spawn(spawnAt, Vex.class, ent -> {
                ent.setInvisible(true);
                ent.setSilent(true);
                ent.setInvulnerable(true);
                ent.setGravity(true);
                ent.setAware(false);
                ent.setPersistent(false);
                if (ent.getEquipment() != null) {
                    ent.getEquipment().clear();
                }
            });

            vex.setGravity(true);

            fallingBlock = location.getWorld().spawnFallingBlock(spawnAt, Material.matchMaterial(this.handle.getConfig().FALLING_BLOCK_BLOCK.toUpperCase(Locale.ENGLISH)).createBlockData());
            vex.addPassenger(fallingBlock);
            fallingBlock.setPersistent(false);
            FallingBlockChecker.addToCheck(this);
            vex.setVelocity(new Vector(0, handle.getConfig().FALLING_BLOCK_SPEED, 0));
        });
    }

    public void land(@NotNull Location location) {
        this.finishLocation = location;
        BlockIntegration.Companion.place(handle.getConfig().BLOCK_TYPE, location);
        this.updateHologram();
        this.spawnFirework(location);
    }

    private void updateHologram() {
        if (!handle.getConfig().HOLOGRAM_ENABLED) return;
        if (hologram == null) {
            Location hologramLocation = finishLocation.clone().add(0.5, 0, 0.5);
            hologramLocation.add(0, handle.getConfig().HOLOGRAM_HEIGHT, 0);

            ArrayList<Component> formatted = new ArrayList<>(handle.getConfig().HOLOGRAM_LINES.size());
            for (String hologramLine : handle.getConfig().HOLOGRAM_LINES) {
                formatted.add(StringUtils.format(hologramLine).replaceText(b -> {
                    b.match("%hits%");
                    b.replacement(String.valueOf(health));
                }).replaceText(b -> {
                    b.match("%max_hits%");
                    b.replacement(String.valueOf(getHandle().getConfig().REQUIRED_INTERACTION_AMOUNT));
                }));
            }

            hologram = HologramFactory.get().spawnHologram(hologramLocation, Utils.serializeLocation(hologramLocation).replace(";", "-"), 0.3);

            for (Component component : formatted) {
                hologram.addLine(component);
            }
        } else {
            ArrayList<Component> formatted = new ArrayList<>(handle.getConfig().HOLOGRAM_LINES.size());
            for (String hologramLine : handle.getConfig().HOLOGRAM_LINES) {
                formatted.add(StringUtils.format(hologramLine).replaceText(b -> {
                    b.match("%hits%");
                    b.replacement(String.valueOf(health));
                }).replaceText(b -> {
                    b.match("%max_hits%");
                    b.replacement(String.valueOf(getHandle().getConfig().REQUIRED_INTERACTION_AMOUNT));
                }));
            }

            int num = 0;
            for (Component component : formatted) {
                hologram.setLine(num, component);
                num++;
            }
        }
    }

    public void claim(@Nullable Player player, Envoy envoy) {
        this.claim(player, envoy, true);
    }

    public void spawnFirework(Location location) {
        if (!this.handle.getConfig().FIREWORK_ENABLED) return;

        Scheduler.get().executeAt(location, () -> {
            Location loc2 = location.clone();
            loc2.add(0.5, 0.5, 0.5);
            Firework fw = (Firework) location.getWorld().spawnEntity(loc2, EntityType.FIREWORK);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(this.handle.getFireworkType()).withColor(org.bukkit.Color.fromRGB(this.handle.getFireworkColor().getRed(), this.handle.getFireworkColor().getGreen(), this.handle.getFireworkColor().getBlue())).build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(FIREWORK_KEY, PersistentDataType.BYTE, (byte) 0);
            fw.detonate();
        });
    }

    public void damage(User user, Envoy envoy) {
        if (user.canCollect(envoy, this.getHandle())) {
            user.getPlayer().playSound(finishLocation, Sound.BLOCK_IRON_DOOR_OPEN,1.5f,0.5f);
            health--;
            updateHologram();
            if (health == 0) {
                claim(user.getPlayer(), envoy);
            } else if(health+1 == maxHealth){
                Player p = user.getPlayer();
                String msg = StringUtils.formatToString(envoy.getConfig().PREFIX + envoy.getConfig().START_TOUCH_BROADCAST
                        .replace("%player%",p.getName())
                        .replace("%create_type%",this.handle.getConfig().DISPLAY_NAME)
                        .replace("%location%",envoy.getConfig().LOCATION_FORMAT
                                .replace("%world%", p.getWorld().getName())
                                .replace("%x%", String.valueOf(p.getLocation().getBlockX()))
                                .replace("%y%", String.valueOf(p.getLocation().getBlockY()))
                                .replace("%z%", String.valueOf(p.getLocation().getBlockZ()))));
                Bukkit.broadcastMessage(msg);
            } else if(health <= maxHealth * 0.5 && !user.broadcastCooldown) {
                user.broadcastCooldown = true;
                Bukkit.getScheduler().runTaskLaterAsynchronously(AxEnvoyPlugin.getInstance(),()-> user.broadcastCooldown = false,20 * 60);
                Player p = user.getPlayer();
                String msg = StringUtils.formatToString(envoy.getConfig().PREFIX + envoy.getConfig().HALF_TOUCH_BROADCAST
                        .replace("%player%",p.getName())
                        .replace("%create_type%",this.handle.getConfig().DISPLAY_NAME)
                        .replace("%location%",envoy.getConfig().LOCATION_FORMAT
                                .replace("%world%", p.getWorld().getName())
                                .replace("%x%", String.valueOf(p.getLocation().getBlockX()))
                                .replace("%y%", String.valueOf(p.getLocation().getBlockY()))
                                .replace("%z%", String.valueOf(p.getLocation().getBlockZ()))));
                Bukkit.broadcastMessage(msg);
            }
        } else {
            user.getPlayer().sendMessage(StringUtils.formatToString(envoy.getConfig().PREFIX + envoy.getConfig().COOLDOWN.replace("%player%", user.getPlayer().getName()).replace("%player_name%", user.getPlayer().getName()).replace("%crate%", getHandle().getConfig().DISPLAY_NAME).replace("%cooldown%", String.valueOf((user.getCollectCooldown(envoy, getHandle()) - System.currentTimeMillis()) / 1000))));
        }
    }


    public void claim(@Nullable Player player, Envoy envoy, boolean remove) {
        if (fallingBlock != null) {
            fallingBlock.remove();
            fallingBlock = null;
        }

        if (player != null) {
            Reward reward = Utils.randomReward(this.handle.getRewards());
            reward.execute(player, envoy);

            int cooldown = getHandle().getConfig().COLLECT_COOLDOWN > 0 ? getHandle().getConfig().COLLECT_COOLDOWN : envoy.getConfig().COLLECT_COOLDOWN;
            if (envoy.getConfig().COLLECT_GLOBAL_COOLDOWN) {
                cooldown = envoy.getConfig().COLLECT_COOLDOWN;
            }

            User.USER_MAP.get(player.getUniqueId()).addCrateCooldown(getHandle(), cooldown, envoy);
        }

        BlockIntegration.Companion.remove(getHandle().getConfig().BLOCK_TYPE, finishLocation);
        if (hologram != null) {
            hologram.remove();
        }

        if (remove) {
            this.parent.getSpawnedCrates().remove(this);
        }

        if (envoy != null) {
            boolean broadcast = envoy.getConfig().BROADCAST_COLLECT;
            if (!broadcast) {

                if (handle.getConfig().BROADCAST_COLLECT) {
                    broadcast = true;
                }
            }

            if (broadcast && player != null && !this.parent.getSpawnedCrates().isEmpty()) {
                String message = StringUtils.formatToString(envoy.getConfig().PREFIX + envoy.getConfig().COLLECT.replace("%player_name%", player.getName()).replace("%player%", player.getName()).replace("%crate%", this.handle.getConfig().DISPLAY_NAME).replace("%amount%", String.valueOf(envoy.getSpawnedCrates().size())));
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.getPersistentDataContainer().has(AxEnvoyPlugin.MESSAGE_KEY, PersistentDataType.BYTE)) {
                        onlinePlayer.sendMessage(message);
                    }
                }
            }

            if (this.parent.getSpawnedCrates().isEmpty()) {
                envoy.updateNext();
                envoy.setActive(false);

                String message = StringUtils.formatToString(envoy.getConfig().PREFIX + envoy.getConfig().ENDED);
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.sendMessage(message);
                }
            }
        }
    }

    public void tickFlare() {
        if (!this.handle.getConfig().FLARE_ENABLED) return;
        if (this.handle.getConfig().FLARE_EVERY == 0) return;
        tick++;

        if (tick == this.handle.getConfig().FLARE_EVERY) {
            Scheduler.get().executeAt(finishLocation, () -> {
                if (!getFinishLocation().getWorld().isChunkLoaded(getFinishLocation().getBlockX() >> 4, getFinishLocation().getBlockZ() >> 4))
                    return;
                Location loc2 = finishLocation.clone();
                loc2.add(0.5, 0.5, 0.5);
                Firework fw = (Firework) loc2.getWorld().spawnEntity(loc2, EntityType.FIREWORK);
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().with(this.handle.getFlareFireworkType()).withColor(org.bukkit.Color.fromRGB(this.handle.getFlareFireworkColor().getRed(), this.handle.getFlareFireworkColor().getGreen(), this.handle.getFlareFireworkColor().getBlue())).build());
                meta.setPower(0);
                fw.setFireworkMeta(meta);
                fw.getPersistentDataContainer().set(FIREWORK_KEY, PersistentDataType.BYTE, (byte) 0);
                fw.detonate();
            });

            tick = 0;
        }
    }

    public Vex getVex() {
        return vex;
    }

    public void setVex(Vex vex) {
        this.vex = vex;
    }

    public CrateType getHandle() {
        return handle;
    }

    public FallingBlock getFallingBlock() {
        return fallingBlock;
    }

    public void setFallingBlock(FallingBlock fallingBlock) {
        this.fallingBlock = fallingBlock;
    }

    public Location getFinishLocation() {
        return finishLocation;
    }
}
