package pl.bialystok.vlo.story;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import pl.bialystok.vlo.entity.AdrikEntity;
import pl.bialystok.vlo.entity.DirectorEntity;
import pl.bialystok.vlo.entity.TeacherEntity;
import pl.bialystok.vlo.item.ModItems;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Сюжет: стадии 1..6, квесты, диалоги, громкая связь, трекер комнат. */
public final class StoryManager {
    private StoryManager() {}

    private static final Map<UUID, String> LAST_ROOM = new HashMap<>();
    private static int paTimer = 1200;

    // ======================================================================= тики

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        StoryState s = StoryState.get(server);
        if (s.origin == null) return;
        ServerWorld w = server.getOverworld();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getServerWorld() != w) continue;
            VloSchool.Room r = VloSchool.roomAt(s.origin, p.getBlockPos());
            String name = r == null ? "" : r.name();
            String prev = LAST_ROOM.put(p.getUuid(), name);
            if (r != null && !name.equals(prev)) {
                p.sendMessage(Text.literal(r.name() + " — ").formatted(Formatting.GOLD)
                        .append(Text.literal(r.flavor()).formatted(Formatting.GRAY)), true);
            }
        }

        paTimer -= 20;
        if (paTimer <= 0 && s.stage >= 1 && s.stage < 6) {
            paTimer = 1000 + ThreadLocalRandom.current().nextInt(1000);
            randomPa(w, s);
        }
    }

    private static void randomPa(ServerWorld w, StoryState s) {
        DirectorEntity d = w.getEntitiesByClass(DirectorEntity.class,
                new Box(s.origin).expand(80), e -> true).stream().findFirst().orElse(null);
        if (d != null && ThreadLocalRandom.current().nextBoolean()) {
            VloSchool.Room r = VloSchool.roomAt(s.origin, d.getBlockPos());
            String where = r == null ? "где-то в здании" : "в зоне: " + r.name();
            pa(w, "Директор вышел на обход. Последний раз его видели " + where + ". Ведите себя прилично, хотя бы тихо.");
        } else {
            pa(w, Dialogues.pick(Dialogues.PA_FLUFF));
        }
    }

    // ======================================================================= сообщения

    public static void pa(ServerWorld w, String msg) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            p.sendMessage(Text.literal("[ГРОМКАЯ СВЯЗЬ] ").formatted(Formatting.RED, Formatting.BOLD)
                    .append(Text.literal(msg).formatted(Formatting.YELLOW)), false);
            p.playSoundToPlayer(SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 0.7f, 1.3f);
        }
    }

    public static void title(ServerPlayerEntity p, String title, String sub, Formatting color) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 15));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(sub).formatted(Formatting.GRAY)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title).formatted(color, Formatting.BOLD)));
    }

    private static void npc(ServerPlayerEntity p, String who, Formatting color, String text) {
        p.sendMessage(Text.literal("<" + who + "> ").formatted(color).append(Text.literal(text).formatted(Formatting.WHITE)), false);
    }

    private static void quest(ServerPlayerEntity p, String text) {
        p.sendMessage(Text.literal("[Квест] ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal(text).formatted(Formatting.YELLOW)), false);
    }

    // ======================================================================= инвентарь

    private static int count(ServerPlayerEntity p, Item item) {
        int c = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) c += s.getCount();
        }
        return c;
    }

    private static void take(ServerPlayerEntity p, Item item, int n) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size() && n > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) {
                int t = Math.min(n, s.getCount());
                s.decrement(t);
                n -= t;
            }
        }
    }

    private static void give(ServerPlayerEntity p, ItemStack stack) {
        p.getInventory().offerOrDrop(stack);
    }

    // ======================================================================= цели

    public static String objective(int stage) {
        return switch (stage) {
            case 0 -> "Напиши /vlo start, чтобы впустить школу в свою жизнь.";
            case 1 -> "Принеси Адрику 2 «Энергетик Flash» (ищи в столовой и сундуках).";
            case 2 -> "Собери 3 домашки (классы, туалет, учительская) и сдай Пани Кристине (класс польского).";
            case 3 -> "Набери 10 злотых и закрой долг у Пана Богдана (столовая).";
            case 4 -> "Стащи шпаргалку из учительской (тихо!) и обменяй у Пана Збигнева (математика) на ключ.";
            case 5 -> "Открой ключом железный люк в кабинете директора, найди Аттестат в подвале и отдай Адрику.";
            default -> "Ты выжил. Выпуск. Школа уже скучает.";
        };
    }

    public static void showQuest(ServerPlayerEntity p) {
        StoryState s = StoryState.get(p.getServer());
        quest(p, objective(s.stage));
        switch (s.stage) {
            case 1 -> p.sendMessage(Text.literal("  Flash: " + count(p, ModItems.ENERGY_FLASH) + "/2").formatted(Formatting.GRAY), false);
            case 2 -> p.sendMessage(Text.literal("  Домашки: " + count(p, ModItems.HOMEWORK) + "/3").formatted(Formatting.GRAY), false);
            case 3 -> p.sendMessage(Text.literal("  Злотые: " + count(p, ModItems.ZLOTY) + "/10").formatted(Formatting.GRAY), false);
            case 4 -> p.sendMessage(Text.literal("  Шпаргалки: " + count(p, ModItems.CHEAT_SHEET) + "/1").formatted(Formatting.GRAY), false);
            case 5 -> p.sendMessage(Text.literal("  Люк открыт: " + (s.hatchOpened ? "да" : "нет")
                    + ", Аттестат: " + (count(p, ModItems.DIPLOMA) > 0 ? "есть" : "нет")).formatted(Formatting.GRAY), false);
            default -> {}
        }
        if (s.sideActive) {
            Side q = SIDE[s.sideIndex];
            p.sendMessage(Text.literal("  Побочка Адрика: " + q.title + " (" + count(p, q.item) + "/" + q.count + ")").formatted(Formatting.AQUA), false);
        }
    }

    private static void advance(ServerPlayerEntity who, StoryState s, int newStage) {
        s.stage = newStage;
        s.markDirty();
        MinecraftServer srv = who.getServer();
        for (ServerPlayerEntity p : srv.getPlayerManager().getPlayerList()) {
            p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 1f);
            quest(p, "Выполнено! " + objective(newStage));
        }
    }

    // ======================================================================= старт игры

    public static void startSchool(ServerPlayerEntity host) {
        ServerWorld w = host.getServerWorld();
        MinecraftServer srv = w.getServer();
        StoryState s = StoryState.get(srv);
        if (s.origin != null) {
            host.sendMessage(Text.literal("Школа уже открыта. Напиши /vlo tp, чтобы вернуться в гардероб."), false);
            return;
        }
        host.sendMessage(Text.literal("Строим школу... не двигайся, штукатурка сохнет."), false);
        BlockPos o = host.getBlockPos().add(4, -1, 4);
        VloSchool.build(w, o);
        s.origin = o;
        s.stage = 1;
        s.sideActive = false;
        s.sideIndex = 0;
        s.hatchOpened = false;
        s.markDirty();
        VloSchool.spawnNpcs(w, o);
        srv.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, srv); // школа без левых зомби

        Vec3d e = VloSchool.entrance(o);
        for (ServerPlayerEntity p : w.getPlayers()) {
            p.teleport(w, e.x, e.y, e.z, 90f, 0f);
            p.setSpawnPoint(w.getRegistryKey(), BlockPos.ofFloored(e), 90f, true, false);
            title(p, "ШКОЛА «VLO»", "V Liceum Ogólnokształcące, Białystok", Formatting.RED);
            p.sendMessage(Text.literal("Ты — ученик 3Б. Вчера ты не сделал домашку по ВСЕМ предметам, сегодня двери школы захлопнулись.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
            p.sendMessage(Text.literal("На окнах решётки, на стенах — линолеум 2007 года, в коридоре — шаги директора.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
            p.sendMessage(Text.literal("Рядом топчется Адрик. Он ничего не понимает, но обещал помочь. /vlo quest — цель.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
            quest(p, objective(1));
        }
    }

    public static void teleportToEntrance(ServerPlayerEntity p) {
        StoryState s = StoryState.get(p.getServer());
        if (s.origin == null) { p.sendMessage(Text.literal("Школы ещё нет: /vlo start"), false); return; }
        Vec3d e = VloSchool.entrance(s.origin);
        p.teleport(p.getServerWorld(), e.x, e.y, e.z, 90f, 0f);
    }

    public static void forceStage(MinecraftServer srv, int stage) {
        StoryState s = StoryState.get(srv);
        s.stage = stage;
        s.markDirty();
    }

    public static void reset(ServerWorld w) {
        StoryState s = StoryState.get(w.getServer());
        if (s.origin != null) VloSchool.clearNpcs(w, s.origin);
        s.stage = 0; s.origin = null; s.sideActive = false; s.sideIndex = 0; s.hatchOpened = false;
        s.markDirty();
    }

    // ======================================================================= Адрик

    private record Side(String title, String text, Item item, int count, String done, int zloty) {}

    private static final Side[] SIDE = {
            new Side("3 печеньки",
                    "Кореш, принеси 3 печеньки. Не спрашивай зачем. Я просто хочу узнать, каково это — иметь печеньки.",
                    Items.COOKIE, 3, "О-о-о, печеньки! Я их потом отдам обратно. Наверное. Держи злотые, они мне всё равно жмут.", 2),
            new Side("4 картошки",
                    "Нужна бульба. 4 штуки. Для супа. Или для государства. Я не вдавался.",
                    Items.POTATO, 4, "Бульба! Теперь я богат и опасен. Лови премию.", 2),
            new Side("5 бумажек",
                    "Принеси 5 бумажек. Мне надо распечатать реферат из одного слова. Слово «Ну».",
                    Items.PAPER, 5, "Ща напечатаю. Только принтер найду. И реферат. И смысл. Вот тебе за труды.", 3),
            new Side("2 хлеба",
                    "Мне снился хлеб. Принеси 2 хлеба, иначе сон сбудется и будет страшно.",
                    Items.BREAD, 2, "Сон не сбылся. Но пахнет подозрительно хорошо. Держи.", 2)
    };

    public static void adrikTalk(ServerPlayerEntity p, AdrikEntity a) {
        StoryState s = StoryState.get(p.getServer());
        final String N = "Адрик";

        if (s.stage == 0) {
            npc(p, N, Formatting.AQUA, "Э, ты чего? Напиши /vlo start. Школа сама себя не откроет. Хотя она и закрыта сама.");
            return;
        }
        if (s.stage == 1) {
            if (count(p, ModItems.ENERGY_FLASH) >= 2) {
                take(p, ModItems.ENERGY_FLASH, 2);
                give(p, new ItemStack(ModItems.WORN_JACKET));
                npc(p, N, Formatting.AQUA, "Ты принёс Flash?! Ты лучший. Я выпил один сразу, второй — на потом. Нет, тоже сразу.");
                npc(p, N, Formatting.AQUA, "Держи пуховик. Он поношенный, но с характером. И с запахом. Дальше — к Пани Кристине, она по польскому, у неё глаза как у судьи.");
                advance(p, s, 2);
            } else {
                npc(p, N, Formatting.AQUA, "Мне нужны 2 «Энергетик Flash». Батарейка села. Ищи в столовой, там всё подозрительное, зато съедобное. Иногда.");
            }
            return;
        }
        if (s.stage == 5 && count(p, ModItems.DIPLOMA) >= 1) {
            ending(p, s);
            return;
        }
        // побочки
        if (s.sideActive) {
            Side q = SIDE[s.sideIndex];
            if (count(p, q.item) >= q.count) {
                take(p, q.item, q.count);
                give(p, new ItemStack(ModItems.ZLOTY, q.zloty));
                s.sideActive = false;
                s.sideIndex++;
                s.markDirty();
                npc(p, N, Formatting.AQUA, q.done);
                quest(p, "Побочка выполнена: " + q.title + ". Награда: " + q.zloty + " злотых.");
            } else {
                npc(p, N, Formatting.AQUA, "Я же просил: " + q.title + ". Ты что, забыл? Это ж я обычно забываю.");
            }
            return;
        }
        if (s.sideIndex < SIDE.length) {
            Side q = SIDE[s.sideIndex];
            s.sideActive = true;
            s.markDirty();
            npc(p, N, Formatting.AQUA, q.text);
            quest(p, "Побочка от Адрика: " + q.title + ".");
            return;
        }
        npc(p, N, Formatting.AQUA, Dialogues.pick(Dialogues.ADRIK_IDLE));
        p.sendMessage(Text.literal("(" + objective(s.stage) + ")").formatted(Formatting.GRAY, Formatting.ITALIC), false);
    }

    // ======================================================================= учителя

    public static void teacherTalk(ServerPlayerEntity p, int role) {
        StoryState s = StoryState.get(p.getServer());
        if (s.stage == 0) return;
        switch (role) {
            case TeacherEntity.POLISH -> polish(p, s);
            case TeacherEntity.BUFFET -> buffet(p, s);
            case TeacherEntity.MATH -> math(p, s);
            default -> npc(p, "Пани Ядвига", Formatting.GREEN, Dialogues.pick(Dialogues.JANITOR_HINTS));
        }
    }

    private static void polish(ServerPlayerEntity p, StoryState s) {
        final String N = "Пани Кристина";
        if (s.stage < 2) {
            npc(p, N, Formatting.LIGHT_PURPLE, "Dzień dobry... то есть «уйди». Сначала разберись со своим очкастым другом, а потом будем говорить о литературе.");
        } else if (s.stage == 2) {
            if (count(p, ModItems.HOMEWORK) >= 3) {
                take(p, ModItems.HOMEWORK, 3);
                give(p, new ItemStack(ModItems.ZLOTY, 2));
                npc(p, N, Formatting.LIGHT_PURPLE, "«Мой кот как метафора кризиса»?.. Это... лучшее, что я читала за последние восемь лет. Ставлю тройку. Это максимум. Не гордись.");
                npc(p, N, Formatting.LIGHT_PURPLE, "Держи 2 злотых — на транспорт до кризиса. А долги у Пана Богдана в столовой сами себя не закроют.");
                advance(p, s, 3);
            } else {
                npc(p, N, Formatting.LIGHT_PURPLE, "Мне нужно 3 домашки. Не «я забыл», а три штуки. Ищи по классам, туалетам и тёмным углам. В «Vlo» домашки не пропадают, они прячутся.");
            }
        } else {
            npc(p, N, Formatting.LIGHT_PURPLE, "Ты уже сдал. Иди с миром. На матуру — не опаздывай. А лучше вообще не приходи, я устала.");
        }
    }

    private static void buffet(ServerPlayerEntity p, StoryState s) {
        final String N = "Пан Богдан";
        if (s.stage < 3) {
            npc(p, N, Formatting.GOLD, "Сегодня «Суп дня». Какой — сам не знаю. Но дорого. Приходи, когда будет что дать.");
        } else if (s.stage == 3) {
            if (count(p, ModItems.ZLOTY) >= 10) {
                take(p, ModItems.ZLOTY, 10);
                give(p, new ItemStack(ModItems.KEBAB, 2));
                npc(p, N, Formatting.GOLD, "10 злотых! Долг закрыт, я теперь могу купить новый половник. Держи два кебаба — за счёт заведения. Не говори санэпидемстанции.");
                npc(p, N, Formatting.GOLD, "И слушай: Пан Збигнев по математике обожает шпаргалки. Сам не признаётся. Они лежат в учительской. Но там шастает директор, так что — тихо.");
                advance(p, s, 4);
            } else {
                npc(p, N, Formatting.GOLD, "Ты мне должен 10 злотых за «Суп дня». Да, я знаю, ты его не ел. Он тебя ел.");
            }
        } else {
            if (count(p, ModItems.ZLOTY) >= 2) {
                take(p, ModItems.ZLOTY, 2);
                give(p, new ItemStack(ModItems.KEBAB, 1));
                npc(p, N, Formatting.GOLD, "Кебаб за 2 злотых. Это курица. Нет, не спрашивай.");
            } else {
                npc(p, N, Formatting.GOLD, "Кебаб — 2 злотых. Нет злотых — нет кебаба. Классика образования.");
            }
        }
    }

    private static void math(ServerPlayerEntity p, StoryState s) {
        final String N = "Пан Збигнев";
        if (s.stage < 4) {
            npc(p, N, Formatting.RED, "Реши уравнение: x + долги = 0. Что, x не находится? Вот и у тебя так же. Приходи, когда закроешь долги.");
        } else if (s.stage == 4) {
            if (count(p, ModItems.CHEAT_SHEET) >= 1) {
                take(p, ModItems.CHEAT_SHEET, 1);
                give(p, new ItemStack(ModItems.BASEMENT_KEY));
                npc(p, N, Formatting.RED, "Шпаргалка?.. Я... не видел. Ты мне ничего не давал. Я математик, я ничего не видел.");
                npc(p, N, Formatting.RED, "Это ключ от подвала директора. Люк — в его кабинете, железные плиты. Аттестат — в сундуке внизу. И, ради Бога, не чихай.");
                advance(p, s, 5);
                ServerWorld w = p.getServerWorld();
                pa(w, "Внимание! Замечена утечка шпаргалок. Директор переходит в режим повышенной бдительности.");
            } else {
                npc(p, N, Formatting.RED, "Мне нужна шпаргалка. Любая. Лучше с ответами на матуру. Они лежат в учительской, но директор... ну вы поняли.");
            }
        } else {
            npc(p, N, Formatting.RED, "Я ничего не видел. Я математик. Идите уже, дети.");
        }
    }

    // ======================================================================= события

    public static void onHatchOpened(ServerPlayerEntity p, BlockPos pos) {
        ServerWorld w = p.getServerWorld();
        StoryState s = StoryState.get(p.getServer());
        s.hatchOpened = true;
        s.markDirty();
        pa(w, "ТРЕВОГА! Сработала сигнализация в кабинете директора! Все по классам!");
        DirectorEntity.alertNear(w, pos);
        title(p, "ЛЮК ОТКРЫТ", "Вниз по лестнице. Директор уже идёт.", Formatting.GOLD);
    }

    private static void ending(ServerPlayerEntity p, StoryState s) {
        ServerWorld w = p.getServerWorld();
        take(p, ModItems.DIPLOMA, 1);
        advance(p, s, 6);
        npc(p, "Адрик", Formatting.AQUA, "Это... это аттестат? Настоящий? Я пока не умею читать, но печать выглядит дорого.");
        npc(p, "Адрик", Formatting.AQUA, "Слушай, а мы реально выжили? Я готов поплакать, но у меня очки запотеют. Пошли отсюда. Купим в Żabka хот-догов и забудем всё это как страшный сон завуча.");
        VloSchool.openExit(w, s.origin);
        w.getEntitiesByClass(DirectorEntity.class, new Box(s.origin).expand(80), e -> true).forEach(d -> d.discard());
        pa(w, "Директор Мрочный уходит на пенсию. Он всех вас... поздравляет. (всхлип) Выход открыт. Все свободны.");
        for (ServerPlayerEntity pl : w.getPlayers()) {
            title(pl, "ВЫПУСК!", "Ты сдал школу. Школа тебя — нет.", Formatting.GOLD);
            w.spawnParticles(ParticleTypes.FIREWORK, pl.getX(), pl.getY() + 1, pl.getZ(), 80, 1.5, 1.0, 1.5, 0.15);
        }
    }
}
