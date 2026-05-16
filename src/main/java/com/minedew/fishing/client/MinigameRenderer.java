package com.minedew.fishing.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public class MinigameRenderer {
    private static final int BAR_WIDTH = 50;
    private static final int BAR_HEIGHT = 320;
    private static final int BAR_X_OFFSET = 100;
    private static final int PROGRESS_BAR_WIDTH = 24;
    private static final int PROGRESS_BAR_X_OFFSET = 160;
    private static final int TIMER_BAR_HEIGHT = 8;
    private static final int TIMER_BAR_WIDTH = 200;

    private static final int COLOR_BACKGROUND = -532401374;
    private static final int COLOR_BORDER = -7638187;
    private static final int COLOR_CAPTURE_BAR = -865665178;
    private static final int COLOR_FISH = -29696;
    private static final int COLOR_PROGRESS_EMPTY = -13884394;
    private static final int COLOR_PROGRESS_FULL = -12490271;
    private static final int COLOR_TEXT = -1828;
    private static final int COLOR_FISH_NAME = -10496;
    private static final int COLOR_TIMER_BAR = -48060;
    private static final int COLOR_TIMER_WARNING = -22016;
    private static final int COLOR_TREASURE = -10496;
    private static final int COLOR_TREASURE_DARK = -4684277;

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        MinigameManager manager = MinigameManager.getInstance();
        MinigameState state = manager.getCurrentMinigame();
        if (state == null || !state.isActive()) return;

        if (manager.isHookTimingActive()) {
            renderHookTiming(context, manager);
            return;
        }

        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int barX = screenWidth - BAR_X_OFFSET - BAR_WIDTH;
        int barY = (screenHeight - BAR_HEIGHT) / 2;
        int progressBarX = screenWidth - PROGRESS_BAR_X_OFFSET - PROGRESS_BAR_WIDTH;

        // Main fishing bar background
        context.fill(barX - 2, barY - 2, barX + BAR_WIDTH + 2, barY + BAR_HEIGHT + 2, COLOR_BORDER);
        context.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, COLOR_BACKGROUND);

        // Capture bar (green zone)
        float captureBarPos = state.getCaptureBarPosition();
        int captureBarY = barY + (int) ((1.0F - captureBarPos - state.getCaptureBarHeight()) * BAR_HEIGHT);
        int captureBarHeight = (int) (state.getCaptureBarHeight() * BAR_HEIGHT);

        float pulse = state.isFishCaptured()
            ? (float) Math.sin(state.getTickCount() * 0.15F) * 0.15F + 0.85F
            : (float) Math.sin(state.getTickCount() * 0.08F) * 0.08F + 0.92F;
        int captureBarColor = blendColor(COLOR_CAPTURE_BAR, pulse);
        context.fill(barX + 1, captureBarY, barX + BAR_WIDTH - 1, captureBarY + captureBarHeight, captureBarColor);

        if (state.isFishCaptured()) {
            int borderColor = blendColor(-1, pulse);
            context.fill(barX, captureBarY, barX + 1, captureBarY + captureBarHeight, borderColor);
            context.fill(barX + BAR_WIDTH - 1, captureBarY, barX + BAR_WIDTH, captureBarY + captureBarHeight, borderColor);
        }

        // Fish icon (pixel art fish shape)
        float fishPos = state.getFishPosition();
        int fishY = barY + (int) ((1.0F - fishPos) * BAR_HEIGHT);
        int fishSize = 14;
        float wobbleSpeed = state.isFishCaptured() ? 0.2F : 0.35F;
        float wobble = (float) Math.sin(state.getTickCount() * wobbleSpeed) * 2.5F;
        int fishCenterX = barX + 25 + (int) wobble;
        int fishColor = state.isFishCaptured() ? blendColor(COLOR_FISH, 1.1F) : COLOR_FISH;

        // Draw fish body segments
        context.fill(fishCenterX - fishSize, fishY - 1, fishCenterX - fishSize + 2, fishY + 2, fishColor);
        context.fill(fishCenterX - fishSize + 2, fishY - 3, fishCenterX - fishSize + 5, fishY + 4, fishColor);
        context.fill(fishCenterX - fishSize + 5, fishY - 5, fishCenterX - fishSize + 8, fishY + 6, fishColor);
        context.fill(fishCenterX - fishSize + 8, fishY - 6, fishCenterX + fishSize - 8, fishY + 7, fishColor);
        context.fill(fishCenterX + fishSize - 8, fishY - 5, fishCenterX + fishSize - 5, fishY + 6, fishColor);
        context.fill(fishCenterX + fishSize - 5, fishY - 3, fishCenterX + fishSize - 2, fishY + 4, fishColor);
        context.fill(fishCenterX + fishSize - 2, fishY - 1, fishCenterX + fishSize, fishY + 2, fishColor);

        // Fish eye highlight
        int highlightColor = -1996488705;
        context.fill(fishCenterX - 2, fishY - 2, fishCenterX + 2, fishY, highlightColor);

        // Treasure chest
        if (state.hasTreasure()) {
            float treasurePos = state.getTreasurePosition();
            int treasureY = barY + (int) ((1.0F - treasurePos) * BAR_HEIGHT);
            int treasureSize = 10;
            float treasurePulse = (float) Math.sin(state.getTickCount() * 0.25F) * 0.3F + 0.7F;
            int treasureColor = blendColor(COLOR_TREASURE, treasurePulse);
            int treasureDarkColor = blendColor(COLOR_TREASURE_DARK, treasurePulse);
            int treasureCenterX = barX + 25;

            context.fill(
                treasureCenterX - treasureSize, treasureY - treasureSize / 2,
                treasureCenterX + treasureSize, treasureY + treasureSize / 2, treasureDarkColor
            );
            context.fill(
                treasureCenterX - treasureSize, treasureY - treasureSize / 2 - 3,
                treasureCenterX + treasureSize, treasureY - treasureSize / 2, treasureColor
            );
            context.fill(treasureCenterX - 2, treasureY - 2, treasureCenterX + 2, treasureY + 2, -1);

            if (state.isTreasureCaptured()) {
                float sparklePhase = state.getTickCount() * 0.3F;
                int sparkleColor = blendColor(-1, treasurePulse);
                for (int i = 0; i < 4; i++) {
                    float angle = sparklePhase + i * 3.14159F / 2.0F;
                    int sparkleX = treasureCenterX + (int) (Math.cos(angle) * 15.0);
                    int sparkleY = treasureY + (int) (Math.sin(angle) * 15.0);
                    context.fill(sparkleX - 1, sparkleY - 1, sparkleX + 1, sparkleY + 1, sparkleColor);
                }
            }
        }

        // Progress bar
        context.fill(progressBarX - 2, barY - 2, progressBarX + PROGRESS_BAR_WIDTH + 2, barY + BAR_HEIGHT + 2, COLOR_BORDER);
        context.fill(progressBarX, barY, progressBarX + PROGRESS_BAR_WIDTH, barY + BAR_HEIGHT, COLOR_PROGRESS_EMPTY);

        float progress = state.getProgress();
        int progressHeight = (int) (progress * BAR_HEIGHT);
        int progressY = barY + BAR_HEIGHT - progressHeight;
        if (progressHeight > 0) {
            context.fill(progressBarX, progressY, progressBarX + PROGRESS_BAR_WIDTH, barY + BAR_HEIGHT, COLOR_PROGRESS_FULL);
        }

        // Text: fish name
        String fishName = state.getFishType().getDisplayName();
        int textX = barX + 25 - client.font.width(fishName) / 2;
        int textY = barY - 20;
        context.text(client.font, fishName, textX, textY, COLOR_FISH_NAME, true);

        // Text: difficulty stars
        String difficulty = getDifficultyStars(state.getFishType().getDifficulty());
        int difficultyX = barX + 25 - client.font.width(difficulty) / 2;
        int difficultyY = barY - 10;
        context.text(client.font, difficulty, difficultyX, difficultyY, COLOR_TEXT, true);

        // "PERFECT!" text when fish is captured
        if (state.isFishCaptured()) {
            String perfectText = "PERFECT!";
            float perfectPulse = (float) Math.sin(state.getTickCount() * 0.2F) * 0.3F + 0.7F;
            int perfectColor = blendColor(-256, perfectPulse);
            int perfectX = barX + 25 - client.font.width(perfectText) / 2;
            int perfectY = barY + BAR_HEIGHT + 10;
            context.text(client.font, perfectText, perfectX, perfectY, perfectColor, true);
        }

        // Progress percentage text
        String progressText = String.format("%.0f%%", progress * 100.0F);
        int progressTextX = progressBarX + 12 - client.font.width(progressText) / 2;
        int progressTextY = barY + BAR_HEIGHT + 10;
        context.text(client.font, progressText, progressTextX, progressTextY, COLOR_TEXT, true);

        // Treasure progress bar
        if (state.hasTreasure()) {
            int treasureBarX = progressBarX + PROGRESS_BAR_WIDTH + 10;
            int treasureBarWidth = 16;
            context.fill(treasureBarX - 2, barY - 2, treasureBarX + treasureBarWidth + 2, barY + BAR_HEIGHT + 2, COLOR_BORDER);
            context.fill(treasureBarX, barY, treasureBarX + treasureBarWidth, barY + BAR_HEIGHT, COLOR_PROGRESS_EMPTY);

            float treasureProgress = state.getTreasureProgress();
            int treasureProgressHeight = (int) (treasureProgress * BAR_HEIGHT);
            int treasureProgressY = barY + BAR_HEIGHT - treasureProgressHeight;
            if (treasureProgressHeight > 0) {
                float treasurePulse = (float) Math.sin(state.getTickCount() * 0.2F) * 0.2F + 0.8F;
                int treasureBarColor = blendColor(COLOR_TREASURE, treasurePulse);
                context.fill(treasureBarX, treasureProgressY, treasureBarX + treasureBarWidth, barY + BAR_HEIGHT, treasureBarColor);
            }

            String treasureIcon = "\u2671";
            int treasureIconX = treasureBarX + treasureBarWidth / 2 - client.font.width(treasureIcon) / 2;
            int treasureIconY = barY - 20;
            context.text(client.font, treasureIcon, treasureIconX, treasureIconY, COLOR_TREASURE, true);

            if (state.isTreasureComplete()) {
                String bonusText = "BONUS!";
                float bonusPulse = (float) Math.sin(state.getTickCount() * 0.25F) * 0.3F + 0.7F;
                int bonusColor = blendColor(COLOR_TREASURE, bonusPulse);
                int bonusX = treasureBarX + treasureBarWidth / 2 - client.font.width(bonusText) / 2;
                int bonusY = barY + BAR_HEIGHT + 10;
                context.text(client.font, bonusText, bonusX, bonusY, bonusColor, true);
            }
        }

        // Timer bar
        float timeRemaining = state.getTimeRemaining();
        int timerBarX = (screenWidth - TIMER_BAR_WIDTH) / 2;
        int timerBarY = 35;

        context.fill(timerBarX - 3, timerBarY - 3, timerBarX + TIMER_BAR_WIDTH + 3, timerBarY + TIMER_BAR_HEIGHT + 3, COLOR_BORDER);
        context.fill(timerBarX, timerBarY, timerBarX + TIMER_BAR_WIDTH, timerBarY + TIMER_BAR_HEIGHT, COLOR_PROGRESS_EMPTY);

        int timerFillWidth = (int) (timeRemaining * TIMER_BAR_WIDTH);
        int timerColor;
        if (timeRemaining > 0.5F) {
            timerColor = COLOR_CAPTURE_BAR;
        } else if (timeRemaining > 0.25F) {
            timerColor = COLOR_TIMER_WARNING;
        } else {
            timerColor = COLOR_TIMER_BAR;
        }

        if (timeRemaining < 0.4F) {
            float pulseSpeed = timeRemaining < 0.15F ? 0.4F : 0.25F;
            float timerPulse = (float) Math.sin(state.getTickCount() * pulseSpeed) * 0.35F + 0.65F;
            timerColor = blendColor(timerColor, timerPulse);
        }

        if (timerFillWidth > 0) {
            context.fill(timerBarX, timerBarY, timerBarX + timerFillWidth, timerBarY + TIMER_BAR_HEIGHT, timerColor);
        }

        float timeSeconds = timeRemaining * 30.0F;
        String timerText = String.format("%.1fs", timeSeconds);
        int timerTextX = timerBarX + 100 - client.font.width(timerText) / 2;
        int timerTextY = timerBarY - 14;
        int textColor = timeRemaining < 0.25F ? COLOR_TIMER_BAR : COLOR_TEXT;
        context.text(client.font, timerText, timerTextX, timerTextY, textColor, true);
    }

    private static String getDifficultyStars(int difficulty) {
        return switch (difficulty) {
            case 1 -> "\u2605";
            case 2 -> "\u2605\u2605";
            case 3 -> "\u2605\u2605\u2605";
            case 4 -> "\u2605\u2605\u2605\u2605";
            default -> "\u2605";
        };
    }

    private static void renderHookTiming(GuiGraphicsExtractor context, MinigameManager manager) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        String exclamation = "!";
        int tickCount = manager.getHookTimingTickCount();
        float pulse = (float) Math.sin(tickCount * 0.3F) * 0.3F + 0.7F;
        float scale = 3.0F + pulse * 1.5F;

        int textWidth = client.font.width(exclamation);
        int x = screenWidth / 2 - (int) (textWidth * scale / 2.0F);
        int y = screenHeight / 2 - 40;
        int glowColor = blendColor(-65536, pulse);

        for (int i = 3; i > 0; i--) {
            int glowAlpha = 64 - i * 16;
            int layerColor = (glowColor & 0x00FFFFFF) | (glowAlpha << 24);
            context.text(client.font, exclamation, x - i, y, layerColor, false);
            context.text(client.font, exclamation, x + i, y, layerColor, false);
            context.text(client.font, exclamation, x, y - i, layerColor, false);
            context.text(client.font, exclamation, x, y + i, layerColor, false);
        }

        context.text(client.font, exclamation, x, y, -1, true);

        String clickText = "CLICK NOW!";
        float timeRemaining = manager.getHookTimingProgress();
        int textColor = timeRemaining > 0.5F ? COLOR_TEXT : COLOR_TIMER_BAR;
        int clickX = screenWidth / 2 - client.font.width(clickText) / 2;
        int clickY = y + 30;
        context.text(client.font, clickText, clickX, clickY, textColor, true);

        int timerBarWidth = 200;
        int timerBarHeight = 8;
        int timerBarX = (screenWidth - timerBarWidth) / 2;
        int timerBarY = clickY + 15;

        context.fill(timerBarX - 2, timerBarY - 2, timerBarX + timerBarWidth + 2, timerBarY + timerBarHeight + 2, COLOR_BORDER);
        context.fill(timerBarX, timerBarY, timerBarX + timerBarWidth, timerBarY + timerBarHeight, COLOR_PROGRESS_EMPTY);

        int fillWidth = (int) (timeRemaining * timerBarWidth);
        int fillColor = timeRemaining > 0.5F ? COLOR_CAPTURE_BAR : COLOR_TIMER_BAR;
        if (fillWidth > 0) {
            context.fill(timerBarX, timerBarY, timerBarX + fillWidth, timerBarY + timerBarHeight, fillColor);
        }

        String fishHint = "Fish: " + manager.getHookTimingFishName();
        int hintX = screenWidth / 2 - client.font.width(fishHint) / 2;
        int hintY = timerBarY + 15;
        context.text(client.font, fishHint, hintX, hintY, COLOR_FISH_NAME, true);
    }

    private static int blendColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        r = Mth.clamp(r, 0, 255);
        g = Mth.clamp(g, 0, 255);
        b = Mth.clamp(b, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
