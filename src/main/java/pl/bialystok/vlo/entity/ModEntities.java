package pl.bialystok.vlo.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import pl.bialystok.vlo.VloMod;

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<AdrikEntity> ADRIK = Registry.register(Registries.ENTITY_TYPE, VloMod.id("adrik"),
            EntityType.Builder.create(AdrikEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.95f).maxTrackingRange(10).build("adrik"));

    public static final EntityType<DirectorEntity> DIRECTOR = Registry.register(Registries.ENTITY_TYPE, VloMod.id("director"),
            EntityType.Builder.create(DirectorEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.95f).maxTrackingRange(10).build("director"));

    public static final EntityType<TeacherEntity> TEACHER = Registry.register(Registries.ENTITY_TYPE, VloMod.id("teacher"),
            EntityType.Builder.create(TeacherEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.95f).maxTrackingRange(10).build("teacher"));

    public static Item ADRIK_EGG;
    public static Item DIRECTOR_EGG;
    public static Item TEACHER_EGG;

    public static void register() {
        FabricDefaultAttributeRegistry.register(ADRIK, AdrikEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DIRECTOR, DirectorEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TEACHER, TeacherEntity.createAttributes());

        ADRIK_EGG = Registry.register(Registries.ITEM, VloMod.id("adrik_spawn_egg"),
                new SpawnEggItem(ADRIK, 0x3b6ea5, 0xf2d16b, new Item.Settings()));
        DIRECTOR_EGG = Registry.register(Registries.ITEM, VloMod.id("director_spawn_egg"),
                new SpawnEggItem(DIRECTOR, 0x222222, 0xaa0000, new Item.Settings()));
        TEACHER_EGG = Registry.register(Registries.ITEM, VloMod.id("teacher_spawn_egg"),
                new SpawnEggItem(TEACHER, 0x8a6f4d, 0xffffff, new Item.Settings()));
    }
}
