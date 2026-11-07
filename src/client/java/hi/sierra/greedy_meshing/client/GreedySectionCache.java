package hi.sierra.greedy_meshing.client;

import net.minecraft.core.BlockPos;
//? if UNOBFUSCATED {
/*import net.minecraft.client.renderer.block.BlockAndTintGetter;
*///?} else {
import net.minecraft.world.level.BlockAndTintGetter;
//?}
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/**
 * Per-build-thread cache of block states, packed light and light-occlusion for the section being
 * meshed plus a one-block border (18x18x18 cells).
 *
 * <p>Face visibility, the AO merge keys and the per-corner smooth lighting all look at the same
 * neighbouring cells over and over (every cell is read by up to ~27 different faces). Reading them
 * through the world slice each time dominated chunk-build cost on large merged sections. Cells are
 * filled lazily, so a section that only touches a few cells pays for only those cells. Positions
 * outside the padded box simply fall through to the world.</p>
 */
public final class GreedySectionCache {
    private static final int PAD = 18;
    private static final int CELLS = PAD * PAD * PAD;
    private static final int LIGHT_UNKNOWN = Integer.MIN_VALUE;

    private final BlockState[] states = new BlockState[CELLS];
    private final int[] light = new int[CELLS];
    /** 0 = unknown, 1 = does not occlude, 2 = occludes (see {@link #occludes}). */
    private final byte[] occluder = new byte[CELLS];
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private BlockAndTintGetter world;
    private int baseX;
    private int baseY;
    private int baseZ;

    public void begin(BlockAndTintGetter world, int baseX, int baseY, int baseZ) {
        this.world = world;
        this.baseX = baseX - 1;
        this.baseY = baseY - 1;
        this.baseZ = baseZ - 1;
        Arrays.fill(states, null);
        Arrays.fill(light, LIGHT_UNKNOWN);
        Arrays.fill(occluder, (byte) 0);
    }

    /** Drops the world reference so a parked build thread does not keep a section slice alive. */
    public void end() {
        this.world = null;
    }

    private int slot(int x, int y, int z) {
        int lx = x - baseX;
        int ly = y - baseY;
        int lz = z - baseZ;
        if ((lx | ly | lz) < 0 || lx >= PAD || ly >= PAD || lz >= PAD) {
            return -1;
        }
        return (ly * PAD + lz) * PAD + lx;
    }

    public BlockState state(int x, int y, int z) {
        int s = slot(x, y, z);
        if (s < 0) {
            pos.set(x, y, z);
            return world.getBlockState(pos);
        }
        BlockState st = states[s];
        if (st == null) {
            pos.set(x, y, z);
            st = world.getBlockState(pos);
            states[s] = st;
        }
        return st;
    }

    /** Packed block/sky light at the cell, with the version-correct light API. */
    public int light(int x, int y, int z) {
        int s = slot(x, y, z);
        if (s < 0) {
            BlockState st = state(x, y, z);
            return GreedyLighting.lightAt(world, st, pos);
        }
        int value = light[s];
        if (value == LIGHT_UNKNOWN) {
            BlockState st = state(x, y, z);
            pos.set(x, y, z);
            value = GreedyLighting.lightAt(world, st, pos);
            light[s] = value;
        }
        return value;
    }

    /**
     * Whether the cell blocks light the way ambient occlusion cares about. This is the same
     * predicate the AO merge key and the smooth-lighting "open corner" test use.
     */
    public boolean occludes(int x, int y, int z) {
        int s = slot(x, y, z);
        if (s < 0) {
            return computeOccludes(state(x, y, z), x, y, z);
        }
        byte v = occluder[s];
        if (v == 0) {
            v = computeOccludes(state(x, y, z), x, y, z) ? (byte) 2 : (byte) 1;
            occluder[s] = v;
        }
        return v == 2;
    }

    private boolean computeOccludes(BlockState neighbor, int x, int y, int z) {
        pos.set(x, y, z);
        //? if >=26.3 {
        /*// 26.3 narrowed isViewBlocking to the camera near-plane check; vanilla and Sodium AO
        // now test isLightPermeable(), which is the same solid-and-dampening predicate.
        return !neighbor.isLightPermeable();
        *///?} else {
        return neighbor.isViewBlocking(world, pos)
                //? if UNOBFUSCATED {
                /*&& neighbor.getLightDampening() != 0
                *///?} else if >=1.21.2 {
                && neighbor.getLightBlock() != 0
                //?} else {
                /*&& neighbor.getLightBlock(world, pos) != 0
                *///?}
                ;
        //?}
    }
}
