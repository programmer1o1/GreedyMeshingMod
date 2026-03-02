package org.Jean.greedy_meshing.mixin.client.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkBuilderTask.class)
public interface SodiumChunkBuilderTaskAccessor {
    @Accessor("render")
    RenderSection greedyMeshing$getRender();
}
