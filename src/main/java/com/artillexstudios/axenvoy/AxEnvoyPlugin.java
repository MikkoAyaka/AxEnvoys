package com.artillexstudios.axenvoy;

import com.artillexstudios.axenvoy.commands.Commands;
import com.artillexstudios.axenvoy.config.ConfigManager;
import com.artillexstudios.axenvoy.envoy.Envoy;
import com.artillexstudios.axenvoy.envoy.EnvoyLoader;
import com.artillexstudios.axenvoy.envoy.SpawnedCrate;
import com.artillexstudios.axenvoy.listeners.ActivateFlare;
import com.artillexstudios.axenvoy.listeners.BlockPhysicsListener;
import com.artillexstudios.axenvoy.listeners.CollectionListener;
import com.artillexstudios.axenvoy.listeners.FallingBlockListener;
import com.artillexstudios.axenvoy.listeners.FireworkDamageListener;
import com.artillexstudios.axenvoy.user.User;
import com.artillexstudios.axenvoy.utils.FallingBlockChecker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AxEnvoyPlugin extends JavaPlugin {
    private static AxEnvoyPlugin instance;

    public static AxEnvoyPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        new ConfigManager();
        new Commands(this);

        User.listen();
        new FallingBlockChecker();
        Bukkit.getPluginManager().registerEvents(new ActivateFlare(), this);
        Bukkit.getPluginManager().registerEvents(new BlockPhysicsListener(), this);
        Bukkit.getPluginManager().registerEvents(new CollectionListener(), this);
        Bukkit.getPluginManager().registerEvents(new FallingBlockListener(), this);
    }

    @Override
    public void onDisable() {
        for (Envoy envoy : EnvoyLoader.envoys) {
            for (SpawnedCrate spawnedCrate : envoy.getSpawnedCrates()) {
                spawnedCrate.claim(null, null);
            }
        }
    }
}