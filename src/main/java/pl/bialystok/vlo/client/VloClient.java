package pl.bialystok.vlo.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.util.Identifier;
import pl.bialystok.vlo.entity.ModEntities;

@Environment(EnvType.CLIENT)
public class VloClient implements ClientModInitializer {
    private static Identifier skin(String name) {
        return Identifier.of("minecraft", "textures/entity/player/wide/" + name + ".png");
    }

    // Чтобы заменить на свои скины: положите PNG в assets/vlo/textures/entity/ и верните Identifier.of("vlo", "textures/entity/adrik.png")
    private static final Identifier[] TEACHER_SKINS = {skin("noor"), skin("zuri"), skin("efe"), skin("sunny")};

    @Override
    public void onInitializeClient() {
        // Адрик: высокий (x1.1), скин «kai» — очки дорисуете в своём PNG.
        EntityRendererRegistry.register(ModEntities.ADRIK, ctx -> new VloHumanoidRenderer<>(ctx, e -> skin("kai"), 1.1f));
        // Директор: мрачный и крупный.
        EntityRendererRegistry.register(ModEntities.DIRECTOR, ctx -> new VloHumanoidRenderer<>(ctx, e -> skin("makena"), 1.05f));
        // Учителя: скин зависит от роли.
        EntityRendererRegistry.register(ModEntities.TEACHER,
                ctx -> new VloHumanoidRenderer<>(ctx, e -> TEACHER_SKINS[Math.floorMod(e.getRole(), TEACHER_SKINS.length)], 1.0f));
    }
}
