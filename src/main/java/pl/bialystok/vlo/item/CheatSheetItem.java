package pl.bialystok.vlo.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Шпаргалка: Shift+ПКМ = 15 секунд "невидимости" для директора. */
public class CheatSheetItem extends VloItem {
    public CheatSheetItem(Settings settings) {
        super(settings, "tooltip.vlo.cheat_sheet");
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!player.isSneaking()) {
            if (!world.isClient) player.sendMessage(Text.translatable("msg.vlo.cheat_sneak"), true);
            return TypedActionResult.fail(stack);
        }
        if (!world.isClient) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 300, 0, false, false));
            player.sendMessage(Text.translatable("msg.vlo.cheat_used"), true);
        }
        if (!player.isCreative()) stack.decrement(1);
        player.getItemCooldownManager().set(this, 100);
        return TypedActionResult.success(stack, world.isClient);
    }
}
