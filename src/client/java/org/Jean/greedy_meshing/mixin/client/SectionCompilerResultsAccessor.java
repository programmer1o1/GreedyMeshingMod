package org.Jean.greedy_meshing.mixin.client;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(SectionCompiler.Results.class)
public interface SectionCompilerResultsAccessor {
    @Accessor("renderedLayers")
    Map<ChunkSectionLayer, MeshData> greedyMeshing$getRenderedLayers();

    @Accessor("blockEntities")
    List<BlockEntity> greedyMeshing$getBlockEntities();

    @Accessor("visibilitySet")
    void greedyMeshing$setVisibilitySet(VisibilitySet visibilitySet);

    @Accessor("transparencyState")
    void greedyMeshing$setTransparencyState(MeshData.SortState sortState);
}
