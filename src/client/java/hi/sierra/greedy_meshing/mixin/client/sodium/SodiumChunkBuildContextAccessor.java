package hi.sierra.greedy_meshing.mixin.client.sodium;

//? if SODIUM {
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkBuildContext.class)
public interface SodiumChunkBuildContextAccessor {
    @Accessor("buffers")
    ChunkBuildBuffers greedyMeshing$getBuffers();

    @Accessor("cache")
    BlockRenderCache greedyMeshing$getCache();
}
//?}
