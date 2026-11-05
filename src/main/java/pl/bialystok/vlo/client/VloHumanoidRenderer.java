package pl.bialystok.vlo.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

import java.util.function.Function;

/** Рендерер "человечков" на модели игрока с ванильными скинами (свои PNG можно подложить позже). */
@Environment(EnvType.CLIENT)
public class VloHumanoidRenderer<T extends MobEntity> extends BipedEntityRenderer<T, PlayerEntityModel<T>> {
    private final Function<T, Identifier> texture;
    private final float scale;

    public VloHumanoidRenderer(EntityRendererFactory.Context ctx, Function<T, Identifier> texture, float scale) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    public Identifier getTexture(T entity) {
        return texture.apply(entity);
    }

    @Override
    protected void scale(T entity, MatrixStack matrices, float amount) {
        matrices.scale(scale, scale, scale);
    }
}
