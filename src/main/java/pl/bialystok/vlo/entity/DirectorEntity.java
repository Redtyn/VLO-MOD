package pl.bialystok.vlo.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.OpenDoorGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.NavigationConditions;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import pl.bialystok.vlo.item.ModItems;
import pl.bialystok.vlo.story.Dialogues;
import pl.bialystok.vlo.story.StoryManager;
import pl.bialystok.vlo.story.StoryState;
import pl.bialystok.vlo.story.VloSchool;

import java.util.UUID;

/**
 * Директор Мрочный — опасный, но ЧЕСТНЫЙ босс-препятствие.
 *
 * Правила честности:
 *  - никогда не убивает: урон <= (здоровье - 1); при низком здоровье игрока отправляет в "Поправочную" (карцер);
 *  - сначала ШАГИ (слышно за ~40 блоков) и предупреждения в actionbar, потом ЗАМЕТИЛ -> 2 секунды крика (ALERT), только потом погоня;
 *  - зрение: конус ~120°, дальность 16, присел = 6, шпаргалка = 3; бег слышен за 8 блоков даже со спины;
 *  - погоня максимум 12 сек, скорость чуть ниже спринта игрока; потерял из виду на 3 сек -> ищет;
 *  - после поимки / усталости уходит на ПЕРЕКУР (10 сек) и ничего не замечает дальше 3 блоков;
 *  - громкая связь сообщает всем игрокам, когда он заметил кого-то, ушёл курить и т.д.
 */
public class DirectorEntity extends PathAwareEntity {
    private enum Mode { PATROL, ALERT, CHASE, SEARCH, SMOKE }

    private Mode mode = Mode.PATROL;
    private int modeTicks;
    private int smokeDuration = 200;
    private UUID victim;
    private BlockPos lastKnown;
    private int lostTicks;
    private int stepTimer;
    private int pause;
    private int attackCooldown;
    private int rageCheck;
    private boolean rage;

    public DirectorEntity(EntityType<? extends DirectorEntity> type, World world) {
        super(type, world);
        setInvulnerable(true);
        setPersistent();
        setCustomName(Text.literal("Пан Директор Мрочный"));
        setCustomNameVisible(true);
        if (NavigationConditions.hasMobNavigation(this)) {
            ((MobNavigation) getNavigation()).setCanPathThroughDoors(true);
        }
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.24)   // чуть медленнее спринта игрока
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new OpenDoorGoal(this, false)); // открытые двери = подсказка игроку
    }

    @Override public boolean canImmediatelyDespawn(double d) { return false; }

    // ------------------------------------------------------------------ главный цикл

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        ServerWorld w = (ServerWorld) getWorld();
        modeTicks++;
        if (attackCooldown > 0) attackCooldown--;

        if (--rageCheck <= 0) {
            rageCheck = 100;
            StoryState s = StoryState.get(w.getServer());
            rage = s.stage >= 5 && s.stage < 6;
        }

        stepSounds(w);
        if (age % 40 == 0) proximityWarning(w);

        switch (mode) {
            case PATROL -> tickPatrol(w);
            case ALERT -> tickAlert(w);
            case CHASE -> tickChase(w);
            case SEARCH -> tickSearch(w);
            case SMOKE -> tickSmoke(w);
        }
    }

    private void setMode(Mode m) {
        mode = m;
        modeTicks = 0;
        lostTicks = 0;
        getNavigation().stop();
    }

    // ------------------------------------------------------------------ патруль

    private void tickPatrol(ServerWorld w) {
        StoryState s = StoryState.get(w.getServer());
        if (s.origin != null && getNavigation().isIdle()) {
            if (--pause <= 0) {
                pause = 30 + random.nextInt(70); // постоял, "прислушался"
                BlockPos t = VloSchool.randomPatrolPoint(s.origin, random);
                getNavigation().startMovingTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, rage ? 0.85 : 0.6);
            }
        }
        PlayerEntity seen = findVisiblePlayer(w);
        if (seen != null) startAlert(w, seen);
    }

    /** Внешний вызов: шум (чих Адрика, люк, упавший учебник) — идёт проверять. */
    public void investigate(BlockPos pos) {
        if (mode != Mode.PATROL && mode != Mode.SEARCH) return;
        lastKnown = pos;
        setMode(Mode.SEARCH);
        getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.9);
    }

    public static void alertNear(ServerWorld w, BlockPos pos) {
        for (DirectorEntity d : w.getEntitiesByClass(DirectorEntity.class, new Box(pos).expand(60), e -> true)) {
            d.investigate(pos);
        }
    }

    // ------------------------------------------------------------------ зрение/слух

    private PlayerEntity findVisiblePlayer(ServerWorld w) {
        double base = rage ? 20 : 16;
        PlayerEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            double range = base;
            if (p.isSneaking()) range = 6;
            if (p.hasStatusEffect(StatusEffects.INVISIBILITY)) range = 3;
            double d = squaredDistanceTo(p);
            boolean heard = p.isSprinting() && d < 64 && range > 3; // бег слышно за 8 блоков
            if (d > range * range && !heard) continue;
            if (!heard) {
                Vec3d look = getRotationVec(1f).normalize();
                Vec3d to = p.getEyePos().subtract(getEyePos()).normalize();
                if (look.dotProduct(to) < 0.5 && d > 9) continue; // вне конуса зрения
                if (!canSee(p)) continue;                         // за стеной/углом
            }
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private boolean sees(PlayerEntity p) {
        return p != null && canSee(p) && squaredDistanceTo(p) < 26 * 26;
    }

    private PlayerEntity victimPlayer(ServerWorld w) {
        if (victim == null) return null;
        PlayerEntity p = w.getPlayerByUuid(victim);
        return (p == null || !p.isAlive() || p.isSpectator()) ? null : p;
    }

    // ------------------------------------------------------------------ ALERT: 2 секунды на то, чтобы бежать

    private void startAlert(ServerWorld w, PlayerEntity p) {
        victim = p.getUuid();
        lastKnown = p.getBlockPos();
        setMode(Mode.ALERT);
        w.playSound(null, getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.HOSTILE, 2f, 0.5f);
        StoryManager.pa(w, "Ученик " + p.getName().getString() + " замечен вне урока! " + Dialogues.pick(Dialogues.DIRECTOR_SHOUTS));
        if (p instanceof ServerPlayerEntity sp) {
            StoryManager.title(sp, "ТЫ!", "Почему не на уроке?! Беги или прячься!", Formatting.RED);
        }
    }

    private void tickAlert(ServerWorld w) {
        PlayerEntity p = victimPlayer(w);
        if (p == null) { setMode(Mode.PATROL); return; }
        getNavigation().stop();
        getLookControl().lookAt(p, 30f, 30f);
        if (modeTicks >= (rage ? 30 : 45)) {
            if (sees(p)) {
                setMode(Mode.CHASE);
            } else {
                investigate(p.getBlockPos());
            }
        }
    }

    // ------------------------------------------------------------------ CHASE: ограниченная по времени

    private void tickChase(ServerWorld w) {
        PlayerEntity p = victimPlayer(w);
        if (!(p instanceof ServerPlayerEntity sp)) { startSmoke(w, 120); return; }

        if (sees(p)) { lostTicks = 0; lastKnown = p.getBlockPos(); } else { lostTicks++; }
        if (lostTicks > 60) { investigate(lastKnown); return; }

        if (modeTicks > 240) { // ~12 сек и он выдохся
            StoryManager.pa(w, "Директор (кашляет): ...я уже не тот... Перекур!");
            startSmoke(w, 200);
            return;
        }
        getLookControl().lookAt(p, 30f, 30f);
        if (age % 5 == 0) getNavigation().startMovingTo(p, rage ? 1.15 : 1.05);

        if (attackCooldown == 0 && squaredDistanceTo(p) < 3.0) {
            caught(w, sp);
        }
    }

    /** Поимка. НИКОГДА не убивает. */
    private void caught(ServerWorld w, ServerPlayerEntity p) {
        attackCooldown = 40;
        float hp = p.getHealth();
        if (hp <= 7f) {
            // слишком больно -> воспитательная беседа вместо смерти
            StoryState s = StoryState.get(w.getServer());
            if (s.origin != null) {
                Vec3d d = VloSchool.detentionSpawn(s.origin);
                p.teleport(w, d.x, d.y, d.z, 0f, 0f);
                p.setHealth(Math.max(p.getHealth(), 10f));
                refreshPositionAfterTeleport(s.origin.getX() + 4.5, s.origin.getY() + 1, s.origin.getZ() + 16.5);
            }
            StoryManager.title(p, "ВОСПИТАТЕЛЬНАЯ БЕСЕДА", "3 часа в Поправочной. Время пошло.", Formatting.DARK_RED);
            StoryManager.pa(w, "Ученик " + p.getName().getString() + " приглашён на беседу к директору. Не ждите его на физре.");
            startSmoke(w, 300);
            return;
        }
        float dmg = Math.min(6f, hp - 1f);
        p.damage(w.getDamageSources().mobAttack(this), dmg);
        p.takeKnockback(0.7, getX() - p.getX(), getZ() - p.getZ());
        p.velocityModified = true;
        w.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.5f, 0.6f);
        // конфискация энергетика
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isOf(ModItems.ENERGY_FLASH)) {
                st.decrement(1);
                p.sendMessage(Text.literal("Директор конфисковал твой Flash: «В школе — только компот!»").formatted(Formatting.RED), false);
                break;
            }
        }
        StoryManager.title(p, "ВЫГОВОР", "Записал в дневник. Следующий раз — родителей в школу.", Formatting.RED);
        startSmoke(w, 160);
    }

    // ------------------------------------------------------------------ SEARCH

    private void tickSearch(ServerWorld w) {
        PlayerEntity p = findVisiblePlayer(w);
        if (p != null) { startAlert(w, p); return; }
        if (modeTicks > 240 || (modeTicks > 20 && getNavigation().isIdle())) {
            for (ServerPlayerEntity sp : w.getPlayers()) {
                if (sp.squaredDistanceTo(this) < 20 * 20) {
                    sp.sendMessage(Text.literal("Директор бормочет: «Показалось... Опять эти дети.»").formatted(Formatting.GRAY, Formatting.ITALIC), true);
                }
            }
            setMode(Mode.PATROL);
        }
    }

    // ------------------------------------------------------------------ SMOKE: окно возможностей

    private void startSmoke(ServerWorld w, int ticks) {
        smokeDuration = ticks;
        setMode(Mode.SMOKE);
        StoryManager.pa(w, "Директор ушёл на перекур. Коридор свободен секунд " + (ticks / 20) + ". Не шумите.");
    }

    private void tickSmoke(ServerWorld w) {
        getNavigation().stop();
        if (age % 10 == 0) {
            w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 1.9, getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        }
        if (modeTicks > smokeDuration) setMode(Mode.PATROL);
    }

    // ------------------------------------------------------------------ звук и предупреждения

    private void stepSounds(ServerWorld w) {
        if (getNavigation().isIdle()) return;
        if (--stepTimer <= 0) {
            stepTimer = mode == Mode.CHASE ? 9 : 15;
            w.playSound(null, getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_STEP, SoundCategory.HOSTILE, 2.5f, 0.7f);
            if (random.nextInt(8) == 0) {
                w.playSound(null, getBlockPos(), SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.HOSTILE, 1.8f, 0.5f);
            }
        }
    }

    private void proximityWarning(ServerWorld w) {
        if (mode != Mode.PATROL && mode != Mode.SEARCH) return;
        for (ServerPlayerEntity p : w.getPlayers()) {
            double d = Math.sqrt(squaredDistanceTo(p));
            if (d > 24 || p.isSpectator()) continue;
            Text t;
            if (d > 14) t = Text.literal("Где-то вдали слышны шаги...").formatted(Formatting.GRAY, Formatting.ITALIC);
            else if (d > 8) t = Text.literal("Шаги всё ближе. Скрипит дверь...").formatted(Formatting.YELLOW, Formatting.ITALIC);
            else t = Text.literal("ОН РЯДОМ. Присядь (Shift) или прячься за угол!").formatted(Formatting.RED, Formatting.BOLD);
            p.sendMessage(t, true);
        }
    }
}
