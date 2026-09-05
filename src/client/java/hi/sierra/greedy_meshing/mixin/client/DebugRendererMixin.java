package hi.sierra.greedy_meshing.mixin.client;

//? if >=1.21.2 && <1.21.11 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.client.GreedyDebugDraw;
import hi.sierra.greedy_meshing.client.GreedyDebugStore;
import hi.sierra.greedy_meshing.client.GreedyRuntimeState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the greedy-mesh debug overlay (translucent {@code debugQuads} fills) by hooking the TAIL of
 * vanilla {@code DebugRenderer.render}.
 *
 * <p>This is the SPIR-V-stable path for the overlay under VulkanMod: {@code WorldRenderEvents} fires at
 * a point VulkanMod 0.5.x renders inconsistently, whereas {@code DebugRenderer.render} runs in the
 * vanilla {@code LevelRenderer} flow VulkanMod preserves. The fill geometry itself goes through
 * {@link GreedyDebugDraw} — lines are avoided because VulkanMod 0.5.x's line shader explodes them into
 * giant triangles on MoltenVK. {@code GreedyMeshingClient} registers the {@code WorldRenderEvents}/
 * {@code LevelRenderEvents} fallback only for versions this mixin does not cover (1.21/1.21.1, 1.21.11, 26.x).
 *
 * <p>{@code DebugRenderer.render}'s signature drifts: 1.21.2–1.21.5 take
 * {@code (PoseStack, Frustum, BufferSource, double×3)}; 1.21.9–1.21.10 add a trailing {@code boolean}.
 * 1.21.11 removed {@code render} (gizmo rework), so this mixin is a no-op there. The
 * {@code frustum}/{@code translucent} params are unused — only the signature must match.
 */
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {

    //? if <1.21.9 {
    @Inject(method = "render", at = @At("TAIL"))
    private void greedyMeshing$renderWireframe(PoseStack poseStack, Frustum frustum, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void greedyMeshing$renderWireframe(PoseStack poseStack, Frustum frustum, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, boolean translucent, CallbackInfo ci) {
    *///?}
        boolean drawWireframe = GreedyConfig.debugWireframe();
        boolean drawTriangles = GreedyConfig.debugTrianglesHud();
        boolean drawComparison = GreedyConfig.debugComparison();
        if (!GreedyRuntimeState.isRuntimeGreedyActive() || (!drawWireframe && !drawTriangles && !drawComparison)) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);
        Matrix4f pose = poseStack.last().pose();
        // Lines normally; filled quads only under VulkanMod <0.6 (its line shader is broken on MoltenVK).
        boolean fills = GreedyDebugDraw.useFills();
        VertexConsumer consumer = fills ? bufferSource.getBuffer(RenderType.debugQuads()) : bufferSource.getBuffer(RenderType.lines());

        GreedyDebugDraw.setCamera(camX, camY, camZ);
        float alpha = Math.max(0.0f, Math.min(1.0f, GreedyConfig.meshOpacity()));
        int sectionX = SectionPos.blockToSectionCoord(Mth.floor(camX));
        int sectionY = SectionPos.blockToSectionCoord(Mth.floor(camY));
        int sectionZ = SectionPos.blockToSectionCoord(Mth.floor(camZ));

        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        float yaw = (float) Math.toRadians(camera.getYRot());
        float rightX = (float) Math.cos(yaw);
        float rightZ = (float) Math.sin(yaw);

        for (GreedyDebugStore.DebugQuad quad : GreedyDebugStore.getQuadsNear(sectionX, sectionY, sectionZ, 1)) {
            float ex = quad.x1() - quad.x0(), ey = quad.y1() - quad.y0(), ez = quad.z1() - quad.z0();
            float fx = quad.x3() - quad.x0(), fy = quad.y3() - quad.y0(), fz = quad.z3() - quad.z0();

            float cx = (quad.x0() + quad.x2()) * 0.5f - (float) camX;
            float cz = (quad.z0() + quad.z2()) * 0.5f - (float) camZ;
            boolean isLeftHalf = (cx * rightX + cz * rightZ) < 0;

            if (drawWireframe || drawComparison) {
                boolean showGreedyFill = drawWireframe && (!drawComparison || isLeftHalf);
                boolean showVanillaGrid = drawComparison && !isLeftHalf;

                if (showGreedyFill) {
                    GreedyDebugDraw.outline(consumer, pose, quad, fills, alpha, 0.0f, 1.0f, 0.0f);
                }
                if (showVanillaGrid) {
                    int w = Math.max(1, Math.round((float) Math.sqrt(ex * ex + ey * ey + ez * ez)));
                    int h = Math.max(1, Math.round((float) Math.sqrt(fx * fx + fy * fy + fz * fz)));
                    GreedyDebugDraw.grid(consumer, pose, quad, ex, ey, ez, fx, fy, fz, w, h, fills, alpha, 1.0f, 0.3f, 0.3f);
                }
            }
            if (drawTriangles) {
                GreedyDebugDraw.triangles(consumer, pose, quad, fills, alpha, 1.0f, 0.85f, 0.1f);
            }
        }

        poseStack.popPose();
    }
}
//?} else {
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;

// No-op mixin for versions that don't use the DebugRenderer overlay hook:
//   - 1.21 / 1.21.1: DebugRenderer.render has a different (no-Frustum) signature and the
//     WorldRenderEvents path already renders cleanly there, so GreedyMeshingClient keeps that.
//   - >=1.21.11: vanilla removed DebugRenderer.render (gizmo rework).
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
}
//?}
