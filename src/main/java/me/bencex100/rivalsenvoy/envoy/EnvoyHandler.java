package me.bencex100.rivalsenvoy.envoy;

import dev.dejvokep.boostedyaml.YamlDocument;
import me.bencex100.rivalsenvoy.config.ConfigManager;
import me.bencex100.rivalsenvoy.listeners.FallingBlockListener;
import me.bencex100.rivalsenvoy.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;

public class EnvoyHandler {
    public static HashMap<Location, Crate> crates = new HashMap<>();
    public static boolean active = false;
    public static boolean bcd = false;
    public static Location center = null;
    private static final YamlDocument config = ConfigManager.getCnf("config");
    private static final YamlDocument data = ConfigManager.getCnf("data");
//    private static Long cd;

    public static boolean isActive() {
        return active;
    }

    public static Location getCenter() {
        return center;
    }

    public static void startEnvoy() {
//        if (cd != null && System.currentTimeMillis() - cd < 1500) return;
//
//        cd = System.currentTimeMillis();
        FallingBlockListener.fallingBlocks.forEach((key, value) -> {
            key.remove();
            FallingBlockListener.fallingBlocks.remove(key);
        });
        center = Utils.deserializeLocation(data, "data.center").getBlock().getLocation();
        crates.forEach((key, value) -> value.collectCrate(null));
        crates.clear();
        bcd = false;
        active = true;

        final HashMap<String, Double> map = new HashMap<>();
        for (Object j : config.getSection("crates").getKeys()) {
            map.put(j.toString(), config.getDouble("crates." + j + ".spawn-chance"));
        }

        final ArrayList<Location> locations = new ArrayList<>();

        for (int i = 0; i < config.getInt("random-locations.crates-amount"); i++) {
            Location loc;
            int tries = 300 + config.getInt("random-locations.crates-amount") * 10;
            do {
                tries--;
                if (tries < 0) {
                    Bukkit.getLogger().warning("Error while starting event, can't find any locations!");
                    return;
                }
                loc = Utils.isGood(center.clone());
                if (locations.contains(loc))
                    loc = null;
                else
                    locations.add(loc);
            } while (loc == null);
            new Crate(loc, Utils.randomValue(map)).load();
        }
    }
}