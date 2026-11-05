package pl.bialystok.vlo.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Двери без OpenDoorGoal/NavigationConditions: разрешаем путь через двери и сами открываем двери по ходу движения. */
public final class DoorHelper {
    private DoorHelper() {}

    public static void enableDoorPathing(MobEntity mob) {
        if (mob.getNavigation() instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
        }
    }

    /** Вызывать раз в несколько тиков на сервере. Открывает деревянные двери в 1–2 блоках перед мобом. */
    public static void openDoorsAhead(MobEntity mob) {
        World w = mob.getWorld();
        if (w.isClient || mob.getNavigation().isIdle()) return;

        Vec3d dir = mob.getRotationVec(1f);
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len < 1e-3) return;
        double dx = dir.x / len, dz = dir.z / len;

        for (double d = 0.6; d <= 1.8; d += 0.6) {
            BlockPos base = BlockPos.ofFloored(mob.getX() + dx * d, mob.getY(), mob.getZ() + dz * d);
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = base.up(dy);
                BlockState st = w.getBlockState(p);
                if (!(st.getBlock() instanceof DoorBlock door) || st.isOf(Blocks.IRON_DOOR) || st.get(DoorBlock.OPEN)) continue;

                door.setOpen(mob, w, st, p, true);
                BlockPos other = st.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? p.up() : p.down();
                BlockState os = w.getBlockState(other);
                if (os.getBlock() instanceof DoorBlock d2 && !os.get(DoorBlock.OPEN)) {
                    d2.setOpen(mob, w, os, other, true);
                }
            }
        }
    }
}
