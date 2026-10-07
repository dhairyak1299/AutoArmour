package com.autoarmor;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class AutoArmorHandler {
    private static final int OPEN_WARMUP_TICKS = 3;
    private static final int MIN_TICKS_BETWEEN_CLICKS = 2;
    private static final int ARMOR_SLOT_BASE = 5;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rng = new Random();

    private boolean working;
    private boolean closePending;
    private boolean waitingUnequip;
    private int pendingEquipInvSlot = -1;
    private int pendingArmorType = -1;
    private long nextDelayMs;
    private long lastActionMs;
    private int ticksSinceOpen;
    private int ticksSinceLastClick;
    private int combatCooldownTicks;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            resetSession();
            return;
        }

        // Drain key presses every tick so a B press that closed the GUI can't reopen it.
        boolean pressed = false;
        while (AutoArmorMod.openKey.isPressed()) pressed = true;
        if (pressed && mc.currentScreen == null
                && System.currentTimeMillis() - AutoArmorMod.lastGuiClosedMs > 250L) {
            mc.displayGuiScreen(new AutoArmorGui());
            return;
        }

        if (!AutoArmorConfig.enabled) {
            if (working) stopWorking(true);
            return;
        }

        if (p.hurtTime > 0) combatCooldownTicks = 12;
        else if (combatCooldownTicks > 0) combatCooldownTicks--;

        if (!working) {
            if (AutoArmorConfig.autoOpen) {
                if (mc.currentScreen != null || shouldPause(p)) return;
                if (findBestUpgrade(p) == null) return;
                beginSession(p);
            } else {
                // Manual mode: only act while the player has the inventory open themselves.
                if (!(mc.currentScreen instanceof GuiInventory)) return;
                working = true;
                ticksSinceOpen = OPEN_WARMUP_TICKS;
                ticksSinceLastClick = MIN_TICKS_BETWEEN_CLICKS;
                lastActionMs = System.currentTimeMillis();
                scheduleNextDelay();
            }
        }
        runSession(p);
    }

    private void runSession(EntityPlayerSP p) {
        if (p.inventoryContainer == null || mc.playerController == null) {
            stopWorking(true);
            return;
        }
        if (!(mc.currentScreen instanceof GuiInventory)) {
            resetSession();
            return;
        }
        if (shouldPause(p)) return;

        ticksSinceOpen++;
        ticksSinceLastClick++;
        long now = System.currentTimeMillis();

        if (closePending) {
            if (now - lastActionMs >= nextDelayMs) stopWorking(true);
            return;
        }
        if (ticksSinceOpen < OPEN_WARMUP_TICKS) return;
        if (now - lastActionMs < nextDelayMs) return;
        if (ticksSinceLastClick < MIN_TICKS_BETWEEN_CLICKS) return;

        if (tryPerformStep(p)) {
            scheduleNextDelay();
            lastActionMs = now;
            return;
        }
        if (AutoArmorConfig.closeWhenDone && AutoArmorConfig.autoOpen) requestClose();
        else resetSession();
    }

    private boolean shouldPause(EntityPlayerSP p) {
        if (AutoArmorConfig.pauseInCombat && (p.hurtTime > 0 || combatCooldownTicks > 0)) return true;
        if (AutoArmorConfig.pauseWhileUsing && p.isUsingItem()) return true;
        return AutoArmorConfig.pauseWhileMoving
                && (Math.abs(p.motionX) > 0.08 || Math.abs(p.motionZ) > 0.08 || !p.onGround);
    }

    private void beginSession(EntityPlayerSP p) {
        p.sendQueue.addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        mc.displayGuiScreen(new GuiInventory(p));
        working = true;
        closePending = false;
        waitingUnequip = false;
        pendingEquipInvSlot = -1;
        pendingArmorType = -1;
        ticksSinceOpen = 0;
        ticksSinceLastClick = MIN_TICKS_BETWEEN_CLICKS;
        lastActionMs = System.currentTimeMillis();
        scheduleNextDelay();
    }

    private void requestClose() {
        if (!closePending) {
            closePending = true;
            scheduleNextDelay();
            lastActionMs = System.currentTimeMillis();
        }
    }

    private void stopWorking(boolean closeInv) {
        resetSession();
        if (closeInv && mc.currentScreen instanceof GuiInventory) {
            if (mc.thePlayer != null && mc.thePlayer.inventory.getItemStack() != null) {
                mc.thePlayer.inventory.setItemStack(null);
            }
            mc.displayGuiScreen(null);
        }
    }

    private void resetSession() {
        working = false;
        closePending = false;
        waitingUnequip = false;
        pendingEquipInvSlot = -1;
        pendingArmorType = -1;
    }

    private void scheduleNextDelay() {
        int min = AutoArmorConfig.delayMin;
        int max = Math.max(min, AutoArmorConfig.delayMax);
        nextDelayMs = min + (max > min ? rng.nextInt(max - min + 1) : 0);
    }

    private boolean tryPerformStep(EntityPlayerSP p) {
        int windowId = p.inventoryContainer.windowId;

        if (waitingUnequip && pendingEquipInvSlot >= 0 && pendingArmorType >= 0) {
            ItemStack still = p.inventoryContainer.getSlot(pendingEquipInvSlot).getStack();
            if (still != null && still.getItem() instanceof ItemArmor
                    && ((ItemArmor) still.getItem()).armorType == pendingArmorType) {
                mc.playerController.windowClick(windowId, pendingEquipInvSlot, 0, 1, p); // shift-click equip
                ticksSinceLastClick = 0;
            }
            waitingUnequip = false;
            pendingEquipInvSlot = -1;
            pendingArmorType = -1;
            return true;
        }

        Upgrade up = findBestUpgrade(p);
        if (up == null) return false;

        ItemStack equipped = getEquipped(p, up.armorType);
        if (equipped != null) {
            int armorSlot = ARMOR_SLOT_BASE + up.armorType;
            if (AutoArmorConfig.dropWorse) {
                mc.playerController.windowClick(windowId, armorSlot, 1, 4, p); // drop old piece
            } else {
                mc.playerController.windowClick(windowId, armorSlot, 0, 1, p); // shift-click old piece out
            }
            waitingUnequip = true;
            pendingEquipInvSlot = up.invSlot;
            pendingArmorType = up.armorType;
            ticksSinceLastClick = 0;
            return true;
        }

        mc.playerController.windowClick(windowId, up.invSlot, 0, 1, p);
        ticksSinceLastClick = 0;
        return true;
    }

    private Upgrade findBestUpgrade(EntityPlayerSP p) {
        Upgrade best = null;
        for (int type = 0; type < 4; type++) {
            int bestSlot = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int slot = 9; slot < 45; slot++) {
                ItemStack s = p.inventoryContainer.getSlot(slot).getStack();
                if (!isUsable(s, type)) continue;
                double sc = score(s);
                if (sc > bestScore) {
                    bestScore = sc;
                    bestSlot = slot;
                }
            }
            if (bestSlot < 0) continue;

            ItemStack eq = getEquipped(p, type);
            double eqScore = (eq != null && isUsable(eq, type)) ? score(eq) : Double.NEGATIVE_INFINITY;
            if (bestScore < eqScore + AutoArmorConfig.minScoreGain) continue;

            double priority = eq == null ? 1000000.0 + bestScore : bestScore - eqScore;
            if (best == null || priority > best.priority) best = new Upgrade(type, bestSlot, priority);
        }
        return best;
    }

    private boolean isUsable(ItemStack s, int type) {
        if (s == null || !(s.getItem() instanceof ItemArmor)) return false;
        if (((ItemArmor) s.getItem()).armorType != type) return false;
        if (!s.isItemStackDamageable()) return true;
        int max = s.getMaxDamage();
        if (max <= 0) return true;
        double pct = (max - s.getItemDamage()) * 100.0 / max;
        return pct >= AutoArmorConfig.minDurability;
    }

    private ItemStack getEquipped(EntityPlayerSP p, int type) {
        return p.inventory.armorInventory[3 - type];
    }

    private double score(ItemStack s) {
        ItemArmor armor = (ItemArmor) s.getItem();
        double sc = armor.damageReduceAmount * 25.0;
        if (AutoArmorConfig.considerEnchants) {
            sc += lvl(Enchantment.protection, s) * 18.0;
            sc += lvl(Enchantment.blastProtection, s) * 10.0;
            sc += lvl(Enchantment.fireProtection, s) * 9.0;
            sc += lvl(Enchantment.projectileProtection, s) * 9.0;
            if (armor.armorType == 3) sc += lvl(Enchantment.featherFalling, s) * 14.0;
            if (armor.armorType == 0) {
                sc += lvl(Enchantment.respiration, s) * 4.0;
                sc += lvl(Enchantment.aquaAffinity, s) * 3.0;
            }
            sc += lvl(Enchantment.unbreaking, s) * 4.0;
            sc += lvl(Enchantment.thorns, s) * 2.0;
        }
        if (s.isItemStackDamageable()) {
            int max = s.getMaxDamage();
            if (max > 0) sc += ((double) (max - s.getItemDamage()) / max) * 6.0;
        }
        return sc;
    }

    private int lvl(Enchantment e, ItemStack s) {
        return EnchantmentHelper.getEnchantmentLevel(e.effectId, s);
    }

    private static final class Upgrade {
        final int armorType;
        final int invSlot;
        final double priority;

        Upgrade(int armorType, int invSlot, double priority) {
            this.armorType = armorType;
            this.invSlot = invSlot;
            this.priority = priority;
        }
    }
}
