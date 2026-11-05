package pl.bialystok.vlo.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.NavigationConditions;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import pl.bialystok.vlo.story.Dialogues;
import pl.bialystok.vlo.story.StoryManager;

import java.util.EnumSet;

/**
 * Адрик: высокий парень в очках, немного туповатый, но верный кореш.
 * Ходит за игроком, травит тупые шутки, попадает в нелепые ситуации
 * (иногда — и в опасные: громкий чих привлекает директора).
 */
public class AdrikEntity extends PathAwareEntity {
    private int jokeTimer = 300;
    private int mishapTimer = 1800;
    private int frozen = 0;

    public AdrikEntity(EntityType<? extends AdrikEntity> type, World world) {
        super(type, world);
        setInvulnerable(true); // кореш бессмертный (в рамках разумного)
        setPersistent();
        setCustomName(Text.literal("Адрик"));
        setCustomNameVisible(true);
        if (NavigationConditions.hasMobNavigation(this)) {
            ((MobNavigation) getNavigation()).setCanPathThroughDoors(true);
        }
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new OpenDoorGoal(this, true));
        goalSelector.add(2, new FollowPlayerGoal(this));
        goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(4, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        if (frozen > 0) {
            frozen--;
            getNavigation().stop();
        }
        if (--jokeTimer <= 0) {
            jokeTimer = 500 + random.nextInt(700);
            say(Dialogues.pick(Dialogues.ADRIK_IDLE));
        }
        if (--mishapTimer <= 0) {
            mishapTimer = 1800 + random.nextInt(2400);
            mishap();
        }
    }

    /** Нелепые ситуации. Некоторые влияют на геймплей. */
    private void mishap() {
        ServerWorld w = (ServerWorld) getWorld();
        switch (random.nextInt(6)) {
            case 0 -> {
                say("Ой, шнурок... Я просто хотел его завязать. Земля сама прибежала.");
                setVelocity((random.nextDouble() - 0.5) * 0.8, 0.45, (random.nextDouble() - 0.5) * 0.8);
                velocityModified = true;
            }
            case 1 -> {
                say("Подожди, тут в телеге мем про Дуду... Залип. Не трогай меня 6 секунд.");
                frozen = 120;
            }
            case 2 -> {
                say("А-А-АПЧХИ!!! ...Ой. Это было громко, да?");
                w.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.NEUTRAL, 2.5f, 0.6f);
                DirectorEntity.alertNear(w, getBlockPos());
            }
            case 3 -> {
                say("Я выпил Flash и теперь вижу звуки. Я быстрый. Я — ветер. Я — стена!");
                addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 4));
                addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
            }
            case 4 -> {
                say("Уронил учебник. Он был тяжёлый. Видимо, там слишком много знаний.");
                w.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.NEUTRAL, 1.6f, 1.4f);
                DirectorEntity.alertNear(w, getBlockPos());
            }
            default -> {
                say("Я сел на стул, а стула не было. Теперь я философ.");
                addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
            }
        }
    }

    public void say(String msg) {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.squaredDistanceTo(this) < 48 * 48) {
                p.sendMessage(Text.literal("<Адрик> ").formatted(Formatting.AQUA)
                        .append(Text.literal(msg).formatted(Formatting.WHITE)), false);
            }
        }
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            StoryManager.adrikTalk(sp, this);
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    /** Следует за ближайшим игроком; если отстал — телепортируется (чтобы не потеряться в школе). */
    static class FollowPlayerGoal extends Goal {
        private final AdrikEntity mob;
        private PlayerEntity target;
        private int cooldown;

        FollowPlayerGoal(AdrikEntity mob) {
            this.mob = mob;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (mob.frozen > 0) return false;
            target = mob.getWorld().getClosestPlayer(mob, 64);
            return target != null && !target.isSpectator() && mob.squaredDistanceTo(target) > 16;
        }

        @Override
        public boolean shouldContinue() {
            return mob.frozen == 0 && target != null && target.isAlive() && mob.squaredDistanceTo(target) > 6.25;
        }

        @Override
        public void tick() {
            mob.getLookControl().lookAt(target, 30f, 30f);
            if (--cooldown <= 0) {
                cooldown = 10;
                if (mob.squaredDistanceTo(target) > 28 * 28) {
                    mob.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
                    mob.getNavigation().stop();
                } else {
                    mob.getNavigation().startMovingTo(target, 1.15);
                }
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
            target = null;
        }
    }
}
