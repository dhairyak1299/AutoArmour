package com.autoarmor;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = AutoArmorMod.MODID, name = "Auto Armor", version = AutoArmorMod.VERSION,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class AutoArmorMod {
    public static final String MODID = "autoarmor";
    public static final String VERSION = "1.0.0";

    public static KeyBinding openKey;
    public static volatile long lastGuiClosedMs = 0L;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        AutoArmorConfig.init(e.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        openKey = new KeyBinding("Open Auto Armor", Keyboard.KEY_B, "Auto Armor");
        ClientRegistry.registerKeyBinding(openKey);
        MinecraftForge.EVENT_BUS.register(new AutoArmorHandler());
    }
}
