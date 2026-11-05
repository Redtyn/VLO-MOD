package pl.bialystok.vlo.story;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class VloCommands {
    private VloCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        d.register(CommandManager.literal("vlo")
                .then(CommandManager.literal("start").executes(c -> {
                    StoryManager.startSchool(c.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("quest").executes(c -> {
                    StoryManager.showQuest(c.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("tp").executes(c -> {
                    StoryManager.teleportToEntrance(c.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("stage").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("n", IntegerArgumentType.integer(0, 6)).executes(c -> {
                            StoryManager.forceStage(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "n"));
                            c.getSource().sendFeedback(() -> Text.literal("Стадия изменена."), true);
                            return 1;
                        })))
                .then(CommandManager.literal("reset").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    StoryManager.reset(c.getSource().getWorld());
                    c.getSource().sendFeedback(() -> Text.literal("Сюжет сброшен (школа осталась стоять как памятник)."), true);
                    return 1;
                })));
    }
}
