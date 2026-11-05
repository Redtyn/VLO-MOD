package pl.bialystok.vlo.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import pl.bialystok.vlo.story.StoryManager;

/** Ключ: ПКМ по железному люку в кабинете директора — люк исчезает. */
public class BasementKeyItem extends VloItem {
    public BasementKeyItem(Settings settings) {
        super(settings, "tooltip.vlo.basement_key");
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        if (!world.getBlockState(pos).isOf(Blocks.IRON_BLOCK)) return ActionResult.PASS;

        if (!world.isClient) {
            for (int dx = -3; dx <= 3; dx++)
                for (int dy = -3; dy <= 3; dy++)
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos p = pos.add(dx, dy, dz);
                        if (world.getBlockState(p).isOf(Blocks.IRON_BLOCK)) {
                            world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
                        }
                    }
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.2f, 0.6f);
            PlayerEntity player = ctx.getPlayer();
            if (player instanceof ServerPlayerEntity sp) {
                if (!sp.isCreative()) ctx.getStack().decrement(1);
                StoryManager.onHatchOpened(sp, pos);
            }
        }
        return ActionResult.success(world.isClient);
    }
}
