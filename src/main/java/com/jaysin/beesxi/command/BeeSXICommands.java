package com.jaysin.beesxi.command;

import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.brigadier.Command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class BeeSXICommands {
    private static final AtomicBoolean DEBUG_MODE_ENABLED = new AtomicBoolean(false);

    private BeeSXICommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("beesxi")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                    .executes(context -> sendDebugStatus(context.getSource()))
                    .then(Commands.literal("on").executes(context -> setDebugMode(context.getSource(), true)))
                    .then(Commands.literal("off").executes(context -> setDebugMode(context.getSource(), false)))
                    .then(Commands.literal("toggle").executes(context -> toggleDebugMode(context.getSource())))
                    .then(Commands.literal("status").executes(context -> sendDebugStatus(context.getSource()))))
        );
    }

    public static boolean isDebugModeEnabled() {
        return DEBUG_MODE_ENABLED.get();
    }

    private static int setDebugMode(CommandSourceStack source, boolean enabled) {
        DEBUG_MODE_ENABLED.set(enabled);
        source.sendSuccess(() -> Component.literal("BeeSXI debug mode " + (enabled ? "enabled" : "disabled") + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleDebugMode(CommandSourceStack source) {
        return setDebugMode(source, !DEBUG_MODE_ENABLED.get());
    }

    private static int sendDebugStatus(CommandSourceStack source) {
        boolean enabled = DEBUG_MODE_ENABLED.get();
        source.sendSuccess(() -> Component.literal("BeeSXI debug mode is currently " + (enabled ? "enabled" : "disabled") + "."), false);
        return Command.SINGLE_SUCCESS;
    }
}
