package hi.sierra.greedy_meshing.mixin.client.sodium;

import hi.sierra.greedy_meshing.GreedyConfig;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.cortex.nvidium.sodiumCompat.NvidiumCompactChunkVertex", remap = false)
public class NvidiumCompactVertexMixin {
    @Inject(method = "getEncoder", at = @At("RETURN"), cancellable = true)
    private void greedyMeshing$preserveFaceMarker(CallbackInfoReturnable<ChunkVertexEncoder> callback) {
        if (!GreedyConfig.experimentalNvidium()) return;

        ChunkVertexEncoder encoder = callback.getReturnValue();
        callback.setReturnValue((pointer, material, vertices, sectionIndex) -> {
            long end = encoder.write(pointer, material, vertices, sectionIndex);
            for (int i = 0; i < vertices.length; i++) {
                int marker = vertices[i].color >>> 24;
                if (marker >= 234 && marker <= 251) {
                    // The compact format drops alpha. Nvidium only reads bit zero of its material byte.
                    long packedMaterial = pointer + 16L * i + 6;
                    MemoryUtil.memPutByte(packedMaterial, (byte) ((material & 7) | ((marker - 233) << 3)));
                }
            }
            return end;
        });
    }
}
