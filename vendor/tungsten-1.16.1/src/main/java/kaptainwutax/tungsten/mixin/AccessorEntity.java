package kaptainwutax.tungsten.mixin;

import java.util.Collections;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.tag.Tag;
import net.minecraft.util.math.Vec3d;

@Mixin(Entity.class)
public interface AccessorEntity {

	@Accessor
	Vec3d getMovementMultiplier();

	@Accessor
	boolean getFirstUpdate();

	/** 1.16.1: Entity stores a single submerged Tag (intermediary field_25599). */
	@Accessor("field_25599")
	Tag<Fluid> getSubmergedFluidTagRaw();

	default Set<Tag<Fluid>> getSubmergedFluidTag() {
		return kaptainwutax.tungsten.compat.McCompat.singletonTagSet(getSubmergedFluidTagRaw());
	}
}
