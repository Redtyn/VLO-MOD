package pl.bialystok.vlo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Identifier;
import pl.bialystok.vlo.entity.ModEntities;
import pl.bialystok.vlo.item.ModItems;
import pl.bialystok.vlo.story.StoryManager;
import pl.bialystok.vlo.story.VloCommands;

/** V Liceum Ogólnokształcące: Школа Выживания. Точка входа мода. */
public class VloMod implements ModInitializer {
    public static final String MOD_ID = "vlo";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModEntities.register();   // сначала сущности (яйца спавна нужны группе предметов)
        ModItems.register();
        CommandRegistrationCallback.EVENT.register(VloCommands::register);
        ServerTickEvents.END_SERVER_TICK.register(StoryManager::tick);
    }
}
