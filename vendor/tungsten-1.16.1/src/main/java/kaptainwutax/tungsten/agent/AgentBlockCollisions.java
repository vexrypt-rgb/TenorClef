package kaptainwutax.tungsten.agent;

import com.google.common.collect.AbstractIterator;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.CuboidBlockIterator;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.CollisionView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

public class AgentBlockCollisions extends AbstractIterator<VoxelShape> {

    private final Box box;
    private final ShapeContext context;
    private final CuboidBlockIterator blockIterator;
    private final BlockPos.Mutable pos;
    private final VoxelShape boxShape;
    private final CollisionView world;
    private final boolean forEntity;
    public int scannedBlocks;
    // Last chunk looked up. A collision box spans a few blocks, so nearly every lookup hits the
    // same chunk; resolving it through the chunk manager per block dominated PathFinder time.
    private int cachedChunkX = Integer.MIN_VALUE, cachedChunkZ = Integer.MIN_VALUE;
    private Chunk cachedChunk;

    public AgentBlockCollisions(CollisionView world, Agent agent, Box box) {
        this(world, agent, box, false);
    }

    public AgentBlockCollisions(CollisionView world, Agent agent, Box box, boolean forEntity) {
        this.context = new AgentShapeContext(agent);
        this.pos = new BlockPos.Mutable();
        this.boxShape = VoxelShapes.cuboid(box);
        this.world = world;
        this.box = box;
        this.forEntity = forEntity;
        int i = MathHelper.floor(box.minX - 1.0E-7D) - 1;
        int j = MathHelper.floor(box.maxX + 1.0E-7D) + 1;
        int k = MathHelper.floor(box.minY - 1.0E-7D) - 1;
        int l = MathHelper.floor(box.maxY + 1.0E-7D) + 1;
        int m = MathHelper.floor(box.minZ - 1.0E-7D) - 1;
        int n = MathHelper.floor(box.maxZ + 1.0E-7D) + 1;
        this.blockIterator = new CuboidBlockIterator(i, k, m, j, l, n);
    }

    protected VoxelShape computeNext() {
        // Always read via world as BlockView. On 1.16.1 ClientWorld, getChunk/getExistingChunk
        // often returns null (or EmptyChunk) when PathFinder ticks Agent off-thread, which
        // produced zero collisions / zero displacement and Baritone fallback.
        while (this.blockIterator.step()) {
            int i = this.blockIterator.getX();
            int j = this.blockIterator.getY();
            int k = this.blockIterator.getZ();
            int l = this.blockIterator.getEdgeCoordinatesCount();
            if (l == 3) {
                continue;
            }
            this.pos.set(i, j, k);
            BlockState blockState = getBlockState(i, j, k);
            this.scannedBlocks++;
            if (this.forEntity && !blockState.shouldSuffocate(this.world, this.pos)
                    || l == 1 && !blockState.exceedsCube()
                    || l == 2 && !blockState.isOf(Blocks.MOVING_PISTON)) {
                continue;
            }
            VoxelShape voxelShape = blockState.getCollisionShape(this.world, this.pos, this.context);
            if (voxelShape == VoxelShapes.fullCube()) {
                if (!this.box.intersects((double) i, (double) j, (double) k, (double) i + 1.0D, (double) j + 1.0D, (double) k + 1.0D)) {
                    continue;
                }
                return voxelShape.offset((double) i, (double) j, (double) k);
            }
            VoxelShape voxelShape2 = voxelShape.offset((double) i, (double) j, (double) k);
            if (voxelShape2.isEmpty() || !VoxelShapes.matchesAnywhere(voxelShape2, this.boxShape, BooleanBiFunction.AND)) {
                continue;
            }
            return voxelShape2;
        }
        return this.endOfData();
    }

    private BlockState getBlockState(int x, int y, int z) {
        if (y < 0 || y >= 256) return this.world.getBlockState(this.pos);
        int cx = x >> 4, cz = z >> 4;
        if (cx != cachedChunkX || cz != cachedChunkZ) {
            cachedChunkX = cx;
            cachedChunkZ = cz;
            Chunk c = null;
            if (this.world instanceof World) {
                c = ((World) this.world).getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
            }
            // EmptyChunk/null happen off-thread on 1.16.1 ClientWorld: defer to the world path.
            cachedChunk = c instanceof WorldChunk && !((WorldChunk) c).isEmpty() ? c : null;
        }
        return cachedChunk != null ? cachedChunk.getBlockState(this.pos) : this.world.getBlockState(this.pos);
    }

}
