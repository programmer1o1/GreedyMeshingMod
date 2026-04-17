package hi.sierra.greedy_meshing.mixin.client.sodium;

//? if SODIUM {
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockRenderer.class)
public interface SodiumBlockRendererAccessor {
    @Accessor("collector")
    TranslucentGeometryCollector greedyMeshing$getCollector();
}
//?}
