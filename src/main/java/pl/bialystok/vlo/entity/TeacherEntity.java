package pl.bialystok.vlo.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import pl.bialystok.vlo.story.StoryManager;

/** Неподвижный, бессмертный и очень уставший учитель. Роль определяет диалоги. */
public class TeacherEntity extends PathAwareEntity {
    public static final int POLISH = 0, BUFFET = 1, MATH = 2, JANITOR = 3;
    public static final String[] NAMES = {
            "Пани Кристина (польский)", "Пан Богдан (столовая)", "Пан Збигнев (матан)", "Пани Ядвига (уборщица)"
    };
    private static final TrackedData<Integer> ROLE = DataTracker.registerData(TeacherEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public TeacherEntity(EntityType<? extends TeacherEntity> type, World world) {
        super(type, world);
        setInvulnerable(true);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ROLE, 0);
    }

    public int getRole() { return dataTracker.get(ROLE); }

    public void setRole(int role) {
        dataTracker.set(ROLE, role);
        setCustomName(Text.literal(NAMES[Math.floorMod(role, NAMES.length)]));
        setCustomNameVisible(true);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            StoryManager.teacherTalk(sp, getRole());
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Role", getRole());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(ROLE, nbt.getInt("Role"));
    }
}
