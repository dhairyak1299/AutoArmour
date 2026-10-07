package com.autoarmor;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public class AutoArmorConfig {
    public static boolean enabled = true;
    public static boolean autoOpen = true;
    public static boolean closeWhenDone = true;
    public static boolean pauseInCombat = true;
    public static boolean pauseWhileUsing = true;
    public static boolean pauseWhileMoving = false;
    public static boolean dropWorse = false;
    public static boolean considerEnchants = true;

    public static int delayMin = 95;
    public static int delayMax = 165;
    public static int minScoreGain = 1;
    public static int minDurability = 8;

    private static Configuration cfg;
    private static final String C = "general";

    public static void init(File file) {
        cfg = new Configuration(file);
        load();
    }

    public static void load() {
        enabled = cfg.getBoolean("enabled", C, true, "Master toggle");
        autoOpen = cfg.getBoolean("openInventory", C, true, "Open the inventory by itself when an upgrade exists");
        closeWhenDone = cfg.getBoolean("closeWhenDone", C, true, "Close the inventory after finishing");
        pauseInCombat = cfg.getBoolean("pauseInCombat", C, true, "Pause while taking damage");
        pauseWhileUsing = cfg.getBoolean("pauseWhileUsingItem", C, true, "Pause while eating/blocking/drawing a bow");
        pauseWhileMoving = cfg.getBoolean("pauseWhileMoving", C, false, "Pause while moving or airborne");
        dropWorse = cfg.getBoolean("dropReplacedArmor", C, false, "Drop the old piece instead of keeping it");
        considerEnchants = cfg.getBoolean("scoreEnchantments", C, true, "Include enchantments when comparing pieces");
        delayMin = cfg.getInt("delayMin", C, 95, 10, 500, "Minimum delay between clicks (ms)");
        delayMax = cfg.getInt("delayMax", C, 165, 10, 500, "Maximum delay between clicks (ms)");
        minScoreGain = cfg.getInt("minScoreGain", C, 1, 0, 40, "Minimum score improvement required to swap");
        minDurability = cfg.getInt("minDurability", C, 8, 0, 80, "Ignore pieces below this durability %");
        if (delayMax < delayMin) delayMax = delayMin;
        if (cfg.hasChanged()) cfg.save();
    }

    public static void save() {
        if (cfg == null) return;
        setB("enabled", enabled);
        setB("openInventory", autoOpen);
        setB("closeWhenDone", closeWhenDone);
        setB("pauseInCombat", pauseInCombat);
        setB("pauseWhileUsingItem", pauseWhileUsing);
        setB("pauseWhileMoving", pauseWhileMoving);
        setB("dropReplacedArmor", dropWorse);
        setB("scoreEnchantments", considerEnchants);
        setI("delayMin", delayMin);
        setI("delayMax", delayMax);
        setI("minScoreGain", minScoreGain);
        setI("minDurability", minDurability);
        cfg.save();
    }

    private static void setB(String name, boolean v) { cfg.get(C, name, v).set(v); }
    private static void setI(String name, int v) { cfg.get(C, name, v).set(v); }
}
