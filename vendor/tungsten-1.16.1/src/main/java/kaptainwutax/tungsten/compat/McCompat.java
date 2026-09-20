package kaptainwutax.tungsten.compat;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.tag.FluidTags;
import net.minecraft.tag.Tag;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.CollisionView;
import net.minecraft.world.RayTraceContext;

/** 1.16.1 shims for APIs added in later Yarn / later Java. */
public final class McCompat {
    private McCompat() {}

    public static boolean withinRange(Vec3d self, Vec3d other, double horizontal, double vertical) {
        if (self == null || other == null) return false;
        double dx = self.x - other.x;
        double dz = self.z - other.z;
        double dy = Math.abs(self.y - other.y);
        return dx * dx + dz * dz <= horizontal * horizontal && dy <= vertical;
    }

    public static Vec3d offsetDir(Vec3d self, Direction dir, double amount) {
        return self.add(dir.getOffsetX() * amount, dir.getOffsetY() * amount, dir.getOffsetZ() * amount);
    }

    public static Vec2f mul(Vec2f v, float s) {
        return new Vec2f(v.x * s, v.y * s);
    }

    public static float length(Vec2f v) {
        return (float) Math.sqrt(v.x * v.x + v.y * v.y);
    }

    public static float lengthSquared(Vec2f v) {
        return v.x * v.x + v.y * v.y;
    }

    public static Box getBoxAt(EntityDimensions dimensions, Vec3d pos) {
        return getBoxAt(dimensions, pos.x, pos.y, pos.z);
    }

    public static Box getBoxAt(EntityDimensions dimensions, double x, double y, double z) {
        float half = dimensions.width / 2.0F;
        return new Box(x - half, y, z - half, x + half, y + dimensions.height, z + half);
    }

    public static List<VoxelShape> getEntityCollisions(CollisionView world, Entity entity, Box box) {
        Predicate<Entity> always = e -> true;
        Stream<VoxelShape> stream = world.getEntityCollisions(entity, box, always);
        return stream.collect(Collectors.toList());
    }

    public static boolean noEntityCollisions(CollisionView world, Entity entity, Box box) {
        return world.getEntityCollisions(entity, box, e -> true).findAny().isEmpty();
    }

    /** Closest 1.16.1 stand-ins for later FluidHandling.WATER / ShapeType.FALLDAMAGE_RESETTING. */
    public static final RayTraceContext.FluidHandling FLUID_WATER = RayTraceContext.FluidHandling.ANY;
    public static final RayTraceContext.ShapeType SHAPE_FALLDAMAGE = RayTraceContext.ShapeType.COLLIDER;

    public static void addFluidTags(FluidState state, Set<Tag<Fluid>> out) {
        if (state == null || state.isEmpty()) return;
        if (state.isIn(FluidTags.WATER)) out.add(FluidTags.WATER);
        if (state.isIn(FluidTags.LAVA)) out.add(FluidTags.LAVA);
    }

    public static Set<Tag<Fluid>> singletonTagSet(Tag<Fluid> tag) {
        return tag == null ? Collections.emptySet() : Collections.singleton(tag);
    }

    /** Collect discrete Y coordinates from a shape (public stand-in for protected getPointPositions). */
    public static void forEachYPoint(VoxelShape shape, java.util.function.DoubleConsumer consumer) {
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
            consumer.accept(minY);
            consumer.accept(maxY);
        });
    }
}
