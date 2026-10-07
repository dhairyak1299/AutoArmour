package com.autoarmor;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

public class AutoArmorGui extends GuiScreen {
    private static final int W = 150;
    private static final int ID_ENABLED = 0;
    private static final int ID_TOGGLE_BASE = 1;   // 1..7
    private static final int TOGGLES = 7;
    private static final int ID_STEP_BASE = 20;    // 20+2i = minus, 21+2i = plus

    private static final String[] TOGGLE_NAMES = {
        "Open inventory", "Close when done", "Pause in combat", "Pause using item",
        "Pause while moving", "Drop replaced armor", "Score enchants"
    };

    private GuiButton enabledBtn;
    private final GuiButton[] toggleBtns = new GuiButton[TOGGLES];
    private final int[] stepX = new int[4];
    private final int[] stepY = new int[4];
    private int top;

    @Override
    public void initGui() {
        buttonList.clear();
        int left = width / 2 - W - 5;
        int right = width / 2 + 5;
        int y = Math.max(28, height / 2 - 90);
        top = y;

        enabledBtn = new GuiButton(ID_ENABLED, width / 2 - 75, y, 150, 20, "");
        buttonList.add(enabledBtn);
        y += 30;

        for (int i = 0; i < TOGGLES; i++) {
            int x = (i % 2 == 0) ? left : right;
            int ty = y + (i / 2) * 24;
            toggleBtns[i] = new GuiButton(ID_TOGGLE_BASE + i, x, ty, W, 20, "");
            buttonList.add(toggleBtns[i]);
        }
        y += 4 * 24 + 8;

        for (int i = 0; i < 4; i++) {
            int x = (i % 2 == 0) ? left : right;
            int sy = y + (i / 2) * 24;
            stepX[i] = x;
            stepY[i] = sy;
            buttonList.add(new GuiButton(ID_STEP_BASE + i * 2, x, sy, 20, 20, "-"));
            buttonList.add(new GuiButton(ID_STEP_BASE + i * 2 + 1, x + W - 20, sy, 20, 20, "+"));
        }
        refresh();
    }

    private static String onOff(String name, boolean v) {
        return name + ": " + (v ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF");
    }

    private boolean getToggle(int i) {
        switch (i) {
            case 0: return AutoArmorConfig.autoOpen;
            case 1: return AutoArmorConfig.closeWhenDone;
            case 2: return AutoArmorConfig.pauseInCombat;
            case 3: return AutoArmorConfig.pauseWhileUsing;
            case 4: return AutoArmorConfig.pauseWhileMoving;
            case 5: return AutoArmorConfig.dropWorse;
            default: return AutoArmorConfig.considerEnchants;
        }
    }

    private void flipToggle(int i) {
        switch (i) {
            case 0: AutoArmorConfig.autoOpen = !AutoArmorConfig.autoOpen; break;
            case 1: AutoArmorConfig.closeWhenDone = !AutoArmorConfig.closeWhenDone; break;
            case 2: AutoArmorConfig.pauseInCombat = !AutoArmorConfig.pauseInCombat; break;
            case 3: AutoArmorConfig.pauseWhileUsing = !AutoArmorConfig.pauseWhileUsing; break;
            case 4: AutoArmorConfig.pauseWhileMoving = !AutoArmorConfig.pauseWhileMoving; break;
            case 5: AutoArmorConfig.dropWorse = !AutoArmorConfig.dropWorse; break;
            default: AutoArmorConfig.considerEnchants = !AutoArmorConfig.considerEnchants; break;
        }
    }

    private void refresh() {
        enabledBtn.displayString = "Auto Armor: "
                + (AutoArmorConfig.enabled ? EnumChatFormatting.GREEN + "ENABLED" : EnumChatFormatting.RED + "DISABLED");
        for (int i = 0; i < TOGGLES; i++) {
            toggleBtns[i].displayString = onOff(TOGGLE_NAMES[i], getToggle(i));
        }
        toggleBtns[1].enabled = AutoArmorConfig.autoOpen; // only meaningful with auto-open
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        if (b.id == ID_ENABLED) {
            AutoArmorConfig.enabled = !AutoArmorConfig.enabled;
        } else if (b.id >= ID_TOGGLE_BASE && b.id < ID_TOGGLE_BASE + TOGGLES) {
            flipToggle(b.id - ID_TOGGLE_BASE);
        } else if (b.id >= ID_STEP_BASE) {
            int idx = (b.id - ID_STEP_BASE) / 2;
            int dir = ((b.id - ID_STEP_BASE) % 2 == 0) ? -1 : 1;
            step(idx, dir);
        }
        refresh();
    }

    private void step(int idx, int dir) {
        switch (idx) {
            case 0:
                AutoArmorConfig.delayMin = clamp(AutoArmorConfig.delayMin + dir * 5, 10, 500);
                if (AutoArmorConfig.delayMax < AutoArmorConfig.delayMin) AutoArmorConfig.delayMax = AutoArmorConfig.delayMin;
                break;
            case 1:
                AutoArmorConfig.delayMax = clamp(AutoArmorConfig.delayMax + dir * 5, 10, 500);
                if (AutoArmorConfig.delayMin > AutoArmorConfig.delayMax) AutoArmorConfig.delayMin = AutoArmorConfig.delayMax;
                break;
            case 2:
                AutoArmorConfig.minScoreGain = clamp(AutoArmorConfig.minScoreGain + dir, 0, 40);
                break;
            default:
                AutoArmorConfig.minDurability = clamp(AutoArmorConfig.minDurability + dir, 0, 80);
                break;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Auto Armor", width / 2, top - 16, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);

        String[] labels = {
            "Delay min: " + AutoArmorConfig.delayMin + "ms",
            "Delay max: " + AutoArmorConfig.delayMax + "ms",
            "Min score gain: " + AutoArmorConfig.minScoreGain,
            "Min durability: " + AutoArmorConfig.minDurability + "%"
        };
        for (int i = 0; i < 4; i++) {
            drawCenteredString(fontRendererObj, labels[i], stepX[i] + W / 2, stepY[i] + 6, 0xFFFFFF);
        }
        drawCenteredString(fontRendererObj, "Press B or ESC to close", width / 2, stepY[3] + 34, 0xAAAAAA);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == AutoArmorMod.openKey.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        AutoArmorConfig.save();
        AutoArmorMod.lastGuiClosedMs = System.currentTimeMillis();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
