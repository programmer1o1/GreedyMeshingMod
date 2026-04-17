package hi.sierra.greedy_meshing.mixin.client;

//? if >=1.21.9 && <1.21.11 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import hi.sierra.greedy_meshing.GreedyConfig;
import hi.sierra.greedy_meshing.client.GreedyDebugStore;
import hi.sierra.greedy_meshing.client.GreedyRuntimeState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void greedyMeshing$renderWireframe(PoseStack poseStack, Frustum frustum, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, boolean translucent, CallbackInfo ci) {
        boolean drawWireframe = GreedyConfig.debugWireframe();
        boolean drawTriangles = GreedyConfig.debugTrianglesHud();
        boolean drawComparison = GreedyConfig.debugComparison();
        if (!GreedyRuntimeState.isRuntimeGreedyActive() || (!drawWireframe && !drawTriangles && !drawComparison)) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

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
                boolean showGreedyOutline = drawWireframe && (!drawComparison || isLeftHalf);
                boolean showVanillaGrid = drawComparison && !isLeftHalf;

                if (showGreedyOutline) {
                    greedyMeshing$drawEdge(lines, pose, quad.x0(), quad.y0(), quad.z0(), quad.x1(), quad.y1(), quad.z1(), alpha, 0.0f, 1.0f, 0.0f);
                    greedyMeshing$drawEdge(lines, pose, quad.x1(), quad.y1(), quad.z1(), quad.x2(), quad.y2(), quad.z2(), alpha, 0.0f, 1.0f, 0.0f);
                    greedyMeshing$drawEdge(lines, pose, quad.x2(), quad.y2(), quad.z2(), quad.x3(), quad.y3(), quad.z3(), alpha, 0.0f, 1.0f, 0.0f);
                    greedyMeshing$drawEdge(lines, pose, quad.x3(), quad.y3(), quad.z3(), quad.x0(), quad.y0(), quad.z0(), alpha, 0.0f, 1.0f, 0.0f);
                }
                if (showVanillaGrid) {
                    float uLen = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
                    float vLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
                    int w = Math.max(1, Math.round(uLen));
                    int h = Math.max(1, Math.round(vLen));
                    greedyMeshing$drawEdge(lines, pose, quad.x0(), quad.y0(), quad.z0(), quad.x1(), quad.y1(), quad.z1(), alpha, 1.0f, 0.3f, 0.3f);
                    greedyMeshing$drawEdge(lines, pose, quad.x1(), quad.y1(), quad.z1(), quad.x2(), quad.y2(), quad.z2(), alpha, 1.0f, 0.3f, 0.3f);
                    greedyMeshing$drawEdge(lines, pose, quad.x2(), quad.y2(), quad.z2(), quad.x3(), quad.y3(), quad.z3(), alpha, 1.0f, 0.3f, 0.3f);
                    greedyMeshing$drawEdge(lines, pose, quad.x3(), quad.y3(), quad.z3(), quad.x0(), quad.y0(), quad.z0(), alpha, 1.0f, 0.3f, 0.3f);
                    for (int i = 1; i < w; i++) {
                        float t = (float) i / w;
                        float ax = quad.x0() + ex * t, ay = quad.y0() + ey * t, az = quad.z0() + ez * t;
                        float bx = quad.x3() + ex * t, by = quad.y3() + ey * t, bz = quad.z3() + ez * t;
                        greedyMeshing$drawEdge(lines, pose, ax, ay, az, bx, by, bz, alpha, 1.0f, 0.3f, 0.3f);
                    }
                    for (int j = 1; j < h; j++) {
                        float t = (float) j / h;
                        float ax = quad.x0() + fx * t, ay = quad.y0() + fy * t, az = quad.z0() + fz * t;
                        float bx = quad.x1() + fx * t, by = quad.y1() + fy * t, bz = quad.z1() + fz * t;
                        greedyMeshing$drawEdge(lines, pose, ax, ay, az, bx, by, bz, alpha, 1.0f, 0.3f, 0.3f);
                    }
                }
            }
            if (drawTriangles) {
                greedyMeshing$drawEdge(lines, pose, quad.x0(), quad.y0(), quad.z0(), quad.x2(), quad.y2(), quad.z2(), alpha, 1.0f, 0.85f, 0.1f);
            }
        }

        poseStack.popPose();
    }

    @Unique
    private static void greedyMeshing$drawEdge(VertexConsumer lines, Matrix4f pose,
                                                float ax, float ay, float az,
                                                float bx, float by, float bz,
                                                float alpha, float red, float green, float blue) {
        if (alpha <= 0.0f) return;
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0e-5f) { nx /= len; ny /= len; nz /= len; }
        lines.addVertex(pose, ax, ay, az).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
        lines.addVertex(pose, bx, by, bz).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
    }
}
*///?} else {
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;

// No-op mixin for versions that don't need DebugRenderer wireframe hook
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
}
//?}
