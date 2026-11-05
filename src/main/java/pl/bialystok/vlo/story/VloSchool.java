package pl.bialystok.vlo.story;

import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import pl.bialystok.vlo.entity.AdrikEntity;
import pl.bialystok.vlo.entity.DirectorEntity;
import pl.bialystok.vlo.entity.ModEntities;
import pl.bialystok.vlo.entity.TeacherEntity;
import pl.bialystok.vlo.item.ModItems;

/**
 * Школа «Vlo» строится кодом (без NBT-структур) от точки origin (юго-запад, уровень пола).
 * Все координаты ниже — относительные: x 0..48, z 0..32, y 0 = пол, -5..-1 = подвал.
 *
 *   z=0   ┌ Польский ┬ Математика ┬  Столовая  ┬ Учительская ┐
 *   z=13  ├──────────┴────────────┴────────────┴─────────────┤
 *   z=14-18            К О Р И Д О Р   (выход на востоке)
 *   z=19  ├ Туалет ┬ Физика ┬ Поправочная ┬ Директор ┬ Гардероб ┤
 *   z=32  └ (под кабинетом директора — секретный подвал)      ┘
 */
public final class VloSchool {
    private VloSchool() {}

    public record Room(String name, String flavor, int x1, int z1, int x2, int z2, boolean basement) {}

    public static final Room[] ROOMS = {
            new Room("Секретный подвал директора", "Пахнет сыростью, нафталином и неоплаченными премиями.", 31, 20, 41, 31, true),
            new Room("Кабинет директора", "В углу — железные плиты. Все делают вид, что это просто сейф.", 31, 20, 41, 31, false),
            new Room("Коридор", "Линолеум помнит каждого, кто бежал по нему в 2007-м.", 1, 14, 47, 18, false),
            new Room("Класс польского", "На доске: «Matura już za miesiąc». Это угроза.", 1, 1, 11, 12, false),
            new Room("Класс математики", "Здесь решали уравнения. Уравнения победили.", 13, 1, 23, 12, false),
            new Room("Столовая", "Меню: «Суп дня», «Второе дня», «Не спрашивайте».", 25, 1, 39, 12, false),
            new Room("Учительская", "Пахнет кофе и несбывшимися отпусками. Тише!", 41, 1, 47, 12, false),
            new Room("Заброшенный туалет", "Кран капает в ритме трека, который ты забыл.", 1, 20, 9, 31, false),
            new Room("Класс физики", "Закон сохранения энергии: энергии нет, но она сохраняется.", 11, 20, 21, 31, false),
            new Room("Поправочная (кабинет бесед)", "Одинокая лампа. Одинокий стул. Одинокий ты.", 23, 20, 29, 31, false),
            new Room("Гардероб", "Куртки висят и смотрят. Ты на них тоже.", 43, 20, 47, 31, false)
    };

    public static Room roomAt(BlockPos o, BlockPos p) {
        int x = p.getX() - o.getX(), y = p.getY() - o.getY(), z = p.getZ() - o.getZ();
        if (y < -5 || y > 5) return null;
        for (Room r : ROOMS) {
            if (r.basement() != (y < 0)) continue;
            if (x >= r.x1() && x <= r.x2() && z >= r.z1() && z <= r.z2()) return r;
        }
        return null;
    }

    // точки патруля директора (x, z)
    private static final int[][] PATROL = {
            {4, 16}, {12, 16}, {24, 16}, {36, 16}, {44, 16},
            {6, 7}, {18, 7}, {32, 8}, {28, 5}, {44, 7},
            {5, 25}, {16, 25}, {26, 25}, {36, 24}, {45, 25}
    };

    public static BlockPos randomPatrolPoint(BlockPos o, Random r) {
        int[] p = PATROL[r.nextInt(PATROL.length)];
        return o.add(p[0], 1, p[1]);
    }

    public static Vec3d detentionSpawn(BlockPos o) { return new Vec3d(o.getX() + 26.5, o.getY() + 1, o.getZ() + 24.5); }
    public static Vec3d entrance(BlockPos o)       { return new Vec3d(o.getX() + 45.5, o.getY() + 1, o.getZ() + 16.5); }

    // ====================================================================== помощники

    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    private static void set(ServerWorld w, BlockPos o, int x, int y, int z, BlockState s) {
        w.setBlockState(o.add(x, y, z), s, Block.NOTIFY_LISTENERS);
    }

    private static void fill(ServerWorld w, BlockPos o, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    m.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                    w.setBlockState(m, s, Block.NOTIFY_LISTENERS);
                }
    }

    private static void door(ServerWorld w, BlockPos o, int x, int z, Direction facing) {
        fill(w, o, x, 1, z, x, 2, z, AIR);
        BlockState d = Blocks.SPRUCE_DOOR.getDefaultState().with(DoorBlock.FACING, facing);
        set(w, o, x, 1, z, d.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(w, o, x, 2, z, d.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void chest(ServerWorld w, BlockPos o, int x, int y, int z, Direction facing, ItemStack... items) {
        BlockPos p = o.add(x, y, z);
        w.setBlockState(p, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing), Block.NOTIFY_LISTENERS);
        if (w.getBlockEntity(p) instanceof ChestBlockEntity c) {
            for (int i = 0; i < items.length; i++) c.setStack(i * 2 % 27, items[i]); // разбросаем по слотам
        }
    }

    private static ItemStack st(Item item, int n) { return new ItemStack(item, n); }

    private static final String[] HW = {
            "Домашка: доказать, что Земля плоская (с чертежом)",
            "Реферат «Мой сосед по парте как философская категория»",
            "Сочинение: «Żabka как символ позднего капитализма» (200 слов, минимум 300)",
            "Задача: через сколько часов поезд Białystok — Варшава встретит смысл жизни?",
            "ОБЖ: как пережить родительское собрание (практикум)",
            "Конспект: «Почему я не сделал дз» (том 2)",
            "Матура по польскому: пересказ «Pan Tadeusz» голосовыми на 40 минут",
            "Лабораторная: измерение скорости убегания от директора"
    };

    public static ItemStack homework(Random r) {
        ItemStack s = new ItemStack(ModItems.HOMEWORK);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(HW[r.nextInt(HW.length)])
                .styled(st -> st.withItalic(false).withColor(Formatting.YELLOW)));
        return s;
    }

    private static ItemStack diploma() {
        ItemStack s = new ItemStack(ModItems.DIPLOMA);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Аттестат зрелости (оригинал, строго между нами)")
                .styled(st -> st.withItalic(false).withColor(Formatting.GOLD)));
        return s;
    }

    // ====================================================================== сборка

    public static void build(ServerWorld w, BlockPos o) {
        Random r = w.getRandom();
        BlockState wall = Blocks.SMOOTH_SANDSTONE.getDefaultState();
        BlockState ceil = Blocks.POLISHED_ANDESITE.getDefaultState();
        BlockState floor = Blocks.POLISHED_ANDESITE.getDefaultState();

        // 1. расчистка + фундамент
        fill(w, o, -1, 1, -1, 49, 9, 33, AIR);
        fill(w, o, -1, -6, -1, 49, -1, 33, Blocks.STONE.getDefaultState());
        // 2. коробка: пол, стены, потолок
        fill(w, o, 0, 0, 0, 48, 0, 32, floor);
        fill(w, o, 0, 1, 0, 48, 4, 32, wall);
        fill(w, o, 0, 5, 0, 48, 5, 32, ceil);

        // 3. помещения: {x1,z1,x2,z2, пол}
        BlockState parquet = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState tile = Blocks.CYAN_TERRACOTTA.getDefaultState();
        BlockState dark = Blocks.DEEPSLATE_TILES.getDefaultState();
        Object[][] carve = {
                {1, 14, 47, 18, floor},          // коридор
                {1, 1, 11, 12, parquet},         // польский
                {13, 1, 23, 12, parquet},        // математика
                {25, 1, 39, 12, Blocks.GRAY_TERRACOTTA.getDefaultState()}, // столовая
                {41, 1, 47, 12, Blocks.BROWN_TERRACOTTA.getDefaultState()}, // учительская
                {1, 20, 9, 31, tile},            // туалет
                {11, 20, 21, 31, parquet},       // физика
                {23, 20, 29, 31, dark},          // поправочная
                {31, 20, 41, 31, Blocks.DARK_OAK_PLANKS.getDefaultState()}, // кабинет директора
                {43, 20, 47, 31, floor}          // гардероб
        };
        for (Object[] c : carve) {
            fill(w, o, (int) c[0], 1, (int) c[1], (int) c[2], 4, (int) c[3], AIR);
            fill(w, o, (int) c[0], 0, (int) c[1], (int) c[2], 0, (int) c[3], (BlockState) c[4]);
        }
        // двери
        for (int x : new int[]{6, 18, 32, 33, 44}) door(w, o, x, 13, Direction.SOUTH);
        for (int x : new int[]{5, 16, 26, 36, 45}) door(w, o, x, 19, Direction.NORTH);
        // "окна" в наружных стенах
        for (int x = 3; x <= 45; x += 6) {
            fill(w, o, x, 2, 0, x + 1, 3, 0, Blocks.GLASS.getDefaultState());
            fill(w, o, x, 2, 32, x + 1, 3, 32, Blocks.GLASS.getDefaultState());
        }
        // выход на востоке — решётка (откроется в финале)
        fill(w, o, 48, 1, 15, 48, 3, 17, Blocks.IRON_BARS.getDefaultState());

        // 4. освещение (нарочно мало — атмосфера)
        for (int x : new int[]{4, 16, 28, 40}) set(w, o, x, 4, 16, Blocks.SHROOMLIGHT.getDefaultState());
        BlockState lantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        BlockState soul = Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        int[][] lamps = {{6, 6}, {18, 6}, {30, 6}, {36, 6}, {44, 6}, {16, 25}, {26, 25}, {36, 25}, {45, 25}, {5, 25}};
        for (int[] l : lamps) set(w, o, l[0], 4, l[1], lantern);
        set(w, o, 4, 4, 22, soul); // туалет — тусклый

        // 5. коридор
        for (int x = 2; x <= 46; x += 2) fill(w, o, x, 1, 14, x, 2, 14, Blocks.BLUE_CONCRETE.getDefaultState()); // шкафчики
        set(w, o, 3, 1, 18, Blocks.CAULDRON.getDefaultState()); // мусорка
        chest(w, o, 1, 1, 17, Direction.EAST, st(ModItems.ZLOTY, 2), st(Items.COOKIE, 3));

        // 6. классы
        classroom(w, o, 1, 11, 1, 12, 1, new int[]{5, 8, 11}, 1);    // польский
        chest(w, o, 11, 1, 2, Direction.WEST, homework(r), st(ModItems.ZLOTY, 1));
        classroom(w, o, 13, 23, 1, 12, 1, new int[]{5, 8, 11}, 1);   // математика
        chest(w, o, 23, 1, 12, Direction.NORTH, homework(r), st(ModItems.ZLOTY, 1));
        classroom(w, o, 11, 21, 20, 31, 31, new int[]{22, 25, 28}, -1); // физика
        set(w, o, 12, 1, 30, Blocks.BREWING_STAND.getDefaultState());
        chest(w, o, 21, 1, 21, Direction.WEST, homework(r), st(ModItems.ZLOTY, 2));

        // 7. столовая: стойка, «суп», столы
        fill(w, o, 27, 1, 3, 37, 1, 3, Blocks.DARK_OAK_SLAB.getDefaultState());
        set(w, o, 29, 1, 3, Blocks.CAULDRON.getDefaultState());
        set(w, o, 32, 1, 3, Blocks.CAULDRON.getDefaultState());
        set(w, o, 35, 1, 3, Blocks.SMOKER.getDefaultState().with(AbstractFurnaceBlock.FACING, Direction.SOUTH));
        for (int z : new int[]{7, 10}) {
            fill(w, o, 27, 1, z, 37, 1, z, Blocks.SPRUCE_SLAB.getDefaultState());
            for (int x = 27; x <= 37; x += 2) {
                set(w, o, x, 1, z - 1, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
                set(w, o, x, 1, z + 1, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
            }
        }
        set(w, o, 39, 4, 1, Blocks.COBWEB.getDefaultState());
        chest(w, o, 39, 1, 5, Direction.WEST, st(ModItems.ENERGY_FLASH, 2), st(ModItems.KEBAB, 2), st(ModItems.ZLOTY, 2));
        chest(w, o, 25, 1, 12, Direction.NORTH, st(ModItems.ZLOTY, 1), st(Items.BREAD, 2));

        // 8. учительская
        fill(w, o, 41, 1, 1, 47, 1, 12, Blocks.BROWN_CARPET.getDefaultState());
        fill(w, o, 41, 1, 1, 47, 2, 1, Blocks.BOOKSHELF.getDefaultState());
        fill(w, o, 43, 1, 6, 45, 1, 6, Blocks.DARK_OAK_PLANKS.getDefaultState());
        set(w, o, 44, 2, 6, Blocks.CAULDRON.getDefaultState());   // «кофе»
        chest(w, o, 46, 1, 12, Direction.NORTH, st(ModItems.CHEAT_SHEET, 2), st(ModItems.ZLOTY, 2));
        chest(w, o, 42, 1, 12, Direction.NORTH, st(ModItems.ENERGY_FLASH, 1), homework(r), st(Items.COOKIE, 4));

        // 9. заброшенный туалет
        for (int x : new int[]{3, 5, 7}) fill(w, o, x, 1, 28, x, 2, 31, Blocks.IRON_BARS.getDefaultState());
        for (int x : new int[]{2, 4, 6, 8}) set(w, o, x, 1, 31, Blocks.CAULDRON.getDefaultState());
        for (int x : new int[]{2, 5, 8}) set(w, o, x, 1, 20, Blocks.CAULDRON.getDefaultState()); // раковины
        set(w, o, 1, 4, 20, Blocks.COBWEB.getDefaultState());
        set(w, o, 9, 4, 31, Blocks.COBWEB.getDefaultState());
        set(w, o, 5, 4, 25, Blocks.COBWEB.getDefaultState());
        set(w, o, 7, 0, 24, Blocks.MOSSY_COBBLESTONE.getDefaultState());
        set(w, o, 3, 0, 26, Blocks.MOSSY_COBBLESTONE.getDefaultState());
        chest(w, o, 9, 1, 22, Direction.WEST, homework(r), st(ModItems.ZLOTY, 3));

        // 10. Поправочная
        fill(w, o, 26, 1, 26, 27, 1, 26, Blocks.SPRUCE_SLAB.getDefaultState());
        set(w, o, 26, 1, 27, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
        fill(w, o, 23, 2, 25, 23, 3, 27, Blocks.IRON_BARS.getDefaultState());
        chest(w, o, 29, 1, 30, Direction.WEST, st(ModItems.ZLOTY, 3), st(ModItems.ENERGY_FLASH, 1));

        // 11. кабинет директора + люк
        fill(w, o, 34, 1, 22, 40, 1, 30, Blocks.RED_CARPET.getDefaultState());
        fill(w, o, 36, 1, 26, 38, 1, 26, Blocks.DARK_OAK_PLANKS.getDefaultState()); // стол
        set(w, o, 36, 2, 26, Blocks.GOLD_BLOCK.getDefaultState());                  // «Кубок лучшего директора»
        set(w, o, 38, 2, 26, Blocks.SKELETON_SKULL.getDefaultState());              // «бывший завуч»
        set(w, o, 37, 1, 27, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
        fill(w, o, 41, 1, 22, 41, 3, 30, Blocks.BOOKSHELF.getDefaultState());
        // люк в подвал: железные плиты (ПКМ ключом)
        set(w, o, 33, 0, 20, Blocks.IRON_BLOCK.getDefaultState());
        set(w, o, 32, 0, 21, Blocks.IRON_BLOCK.getDefaultState());
        set(w, o, 33, 0, 21, Blocks.IRON_BLOCK.getDefaultState());
        set(w, o, 32, 1, 20, Blocks.IRON_BLOCK.getDefaultState());   // «сейф» над лестницей

        // 12. секретный подвал
        fill(w, o, 31, -4, 20, 41, -1, 31, AIR);
        fill(w, o, 31, -5, 20, 41, -5, 31, Blocks.DEEPSLATE_BRICKS.getDefaultState());
        for (int y = -4; y <= 0; y++) set(w, o, 32, y, 20, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH));
        for (int x : new int[]{33, 37, 40}) set(w, o, x, -1, 25, soul);
        fill(w, o, 41, -4, 21, 41, -2, 25, Blocks.BOOKSHELF.getDefaultState());       // «личные дела»
        set(w, o, 36, -4, 25, Blocks.CRYING_OBSIDIAN.getDefaultState());               // алтарь «Премии»
        set(w, o, 36, -3, 25, Blocks.CANDLE.getDefaultState());
        for (int z : new int[]{22, 24, 28}) set(w, o, 32, -4, z, Blocks.BARREL.getDefaultState()); // «протоколы собраний»
        set(w, o, 33, -2, 30, Blocks.COBWEB.getDefaultState());
        set(w, o, 40, -4, 22, Blocks.SKELETON_SKULL.getDefaultState());
        chest(w, o, 36, -4, 30, Direction.NORTH, diploma(), st(ModItems.ZLOTY, 5), st(ModItems.ENERGY_FLASH, 2));
    }

    /** Класс: доска на дальней стене, парты рядами. dz = в какую сторону стоят стулья от парт. */
    private static void classroom(ServerWorld w, BlockPos o, int x1, int x2, int z1, int z2, int boardZ, int[] rows, int dz) {
        // доска: для северных классов z=1, для южных — задняя стена z=31 (параметр boardZ)
        for (int x = x1 + 2; x <= x2 - 2; x++) fill(w, o, x, 2, boardZ, x, 3, boardZ, Blocks.GREEN_CONCRETE.getDefaultState());
        for (int zr : rows) {
            for (int x = x1 + 1; x <= x2 - 3; x += 3) {
                set(w, o, x, 1, zr, Blocks.SPRUCE_SLAB.getDefaultState());
                set(w, o, x + 1, 1, zr, Blocks.SPRUCE_SLAB.getDefaultState());
                set(w, o, x, 1, zr + dz, Blocks.OAK_STAIRS.getDefaultState()
                        .with(StairsBlock.FACING, dz > 0 ? Direction.NORTH : Direction.SOUTH));
            }
        }
    }

    // ====================================================================== NPC

    public static void clearNpcs(ServerWorld w, BlockPos o) {
        Box box = new Box(o.getX() - 5, o.getY() - 10, o.getZ() - 5, o.getX() + 55, o.getY() + 10, o.getZ() + 40);
        w.getEntitiesByClass(AdrikEntity.class, box, e -> true).forEach(e -> e.discard());
        w.getEntitiesByClass(DirectorEntity.class, box, e -> true).forEach(e -> e.discard());
        w.getEntitiesByClass(TeacherEntity.class, box, e -> true).forEach(e -> e.discard());
    }

    public static void spawnNpcs(ServerWorld w, BlockPos o) {
        clearNpcs(w, o);
        AdrikEntity adrik = ModEntities.ADRIK.create(w);
        adrik.refreshPositionAndAngles(o.getX() + 44.5, o.getY() + 1, o.getZ() + 17.5, 90f, 0f);
        w.spawnEntity(adrik);

        DirectorEntity dir = ModEntities.DIRECTOR.create(w);
        dir.refreshPositionAndAngles(o.getX() + 4.5, o.getY() + 1, o.getZ() + 16.5, 0f, 0f);
        w.spawnEntity(dir);

        teacher(w, o, TeacherEntity.POLISH, 6, 3);
        teacher(w, o, TeacherEntity.BUFFET, 32, 2);
        teacher(w, o, TeacherEntity.MATH, 18, 3);
        teacher(w, o, TeacherEntity.JANITOR, 2, 16);
    }

    private static void teacher(ServerWorld w, BlockPos o, int role, int x, int z) {
        TeacherEntity t = ModEntities.TEACHER.create(w);
        t.setRole(role);
        t.refreshPositionAndAngles(o.getX() + x + 0.5, o.getY() + 1, o.getZ() + z + 0.5, 0f, 0f);
        w.spawnEntity(t);
    }

    /** Финал: открываем выход. */
    public static void openExit(ServerWorld w, BlockPos o) {
        fill(w, o, 48, 1, 15, 48, 3, 17, AIR);
    }
}
