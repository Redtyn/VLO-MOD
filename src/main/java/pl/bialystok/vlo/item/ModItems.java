package pl.bialystok.vlo.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import pl.bialystok.vlo.VloMod;
import pl.bialystok.vlo.entity.ModEntities;

public final class ModItems {
    private ModItems() {}

    /** Энергетик Flash: Скорость III на 45 сек, но слегка мутит. */
    public static final Item ENERGY_FLASH = reg("energy_flash", new VloItem(new Item.Settings().maxCount(16)
            .food(new FoodComponent.Builder().nutrition(2).saturationModifier(0.1f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.SPEED, 900, 2), 1.0f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0), 0.4f)
                    .build()),
            "tooltip.vlo.energy_flash"));

    public static final Item KEBAB = reg("kebab", new VloItem(new Item.Settings().maxCount(16)
            .food(new FoodComponent.Builder().nutrition(8).saturationModifier(0.8f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 200, 0), 0.5f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0), 0.4f)
                    .build()),
            "tooltip.vlo.kebab"));

    public static final Item CHEAT_SHEET = reg("cheat_sheet", new CheatSheetItem(new Item.Settings().maxCount(16)));
    public static final Item WORN_JACKET = reg("worn_jacket", new JacketItem(new Item.Settings().maxCount(1)));
    public static final Item HOMEWORK = reg("homework", new VloItem(new Item.Settings().maxCount(16), "tooltip.vlo.homework"));
    public static final Item ZLOTY = reg("zloty", new VloItem(new Item.Settings().maxCount(64), "tooltip.vlo.zloty"));
    public static final Item BASEMENT_KEY = reg("basement_key", new BasementKeyItem(new Item.Settings().maxCount(1)));
    public static final Item DIPLOMA = reg("diploma", new VloItem(new Item.Settings().maxCount(1), "tooltip.vlo.diploma") {
        @Override
        public boolean hasGlint(ItemStack stack) {
            return true;
        }
    });

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, VloMod.id(name), item);
    }

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, VloMod.id("main"), FabricItemGroup.builder()
                .icon(() -> new ItemStack(DIPLOMA))
                .displayName(Text.translatable("itemGroup.vlo.main"))
                .entries((ctx, entries) -> {
                    entries.add(ENERGY_FLASH);
                    entries.add(KEBAB);
                    entries.add(CHEAT_SHEET);
                    entries.add(WORN_JACKET);
                    entries.add(HOMEWORK);
                    entries.add(ZLOTY);
                    entries.add(BASEMENT_KEY);
                    entries.add(DIPLOMA);
                    entries.add(ModEntities.ADRIK_EGG);
                    entries.add(ModEntities.DIRECTOR_EGG);
                    entries.add(ModEntities.TEACHER_EGG);
                })
                .build());
    }
}
