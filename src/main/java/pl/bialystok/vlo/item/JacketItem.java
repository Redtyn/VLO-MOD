package pl.bialystok.vlo.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Поношенный пуховик: защита за счёт скорости. Можно использовать раз в минуту. */
public class JacketItem extends VloItem {
    public JacketItem(Settings settings) {
        super(settings, "tooltip.vlo.worn_jacket");
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 600, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 600, 0));
            player.sendMessage(Text.translatable("msg.vlo.jacket_used"), true);
        }
        player.getItemCooldownManager().set(this, 1200);
        return TypedActionResult.success(stack, world.isClient);
    }
}
