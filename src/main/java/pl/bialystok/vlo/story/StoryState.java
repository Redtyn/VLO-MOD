package pl.bialystok.vlo.story;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/** Общий прогресс сюжета для всего мира (кооп: у вас с другом один сюжет на двоих). */
public class StoryState extends PersistentState {
    public int stage = 0;             // 0 не начато ... 6 выпуск
    public BlockPos origin = null;    // юго-западный угол школы на уровне пола
    public boolean sideActive = false;
    public int sideIndex = 0;
    public boolean hatchOpened = false;

    public static final PersistentState.Type<StoryState> TYPE =
            new PersistentState.Type<>(StoryState::new, StoryState::fromNbt, null);

    public static StoryState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "vlo_story");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("Stage", stage);
        nbt.putBoolean("SideActive", sideActive);
        nbt.putInt("SideIndex", sideIndex);
        nbt.putBoolean("Hatch", hatchOpened);
        if (origin != null) {
            nbt.putInt("OX", origin.getX());
            nbt.putInt("OY", origin.getY());
            nbt.putInt("OZ", origin.getZ());
        }
        return nbt;
    }

    public static StoryState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        StoryState s = new StoryState();
        s.stage = nbt.getInt("Stage");
        s.sideActive = nbt.getBoolean("SideActive");
        s.sideIndex = nbt.getInt("SideIndex");
        s.hatchOpened = nbt.getBoolean("Hatch");
        if (nbt.contains("OX")) s.origin = new BlockPos(nbt.getInt("OX"), nbt.getInt("OY"), nbt.getInt("OZ"));
        return s;
    }
}
