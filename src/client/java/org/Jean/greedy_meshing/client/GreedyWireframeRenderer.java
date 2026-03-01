package org.Jean.greedy_meshing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.core.SectionPos;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.Jean.greedy_meshing.GreedyConfig;
import org.joml.Matrix4f;

public final class GreedyWireframeRenderer {
    private static final float WIREFRAME_Y_OFFSET = -1.5f;
    private static final int RADIUS = 1;

    private GreedyWireframeRenderer() {
    }

    public static void render(WorldRenderContext context) {
        boolean drawWireframe = GreedyConfig.debugWireframe();
        boolean drawTriangles = GreedyConfig.debugTrianglesHud();
        if (!GreedyRuntimeState.isRuntimeGreedyActive() || (!drawWireframe && !drawTriangles) || context.consumers() == null) {
            return;
        }

        PoseStack poseStack = context.matrices();
        if (poseStack == null) {
            return;
        }

        Vec3 cameraPos = context.worldState().cameraRenderState.entityPos;
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
        float alpha = Math.max(0.0f, Math.min(1.0f, GreedyConfig.meshOpacity()));
        int sectionX = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.x));
        int sectionY = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.y));
        int sectionZ = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.z));

        for (GreedyDebugStore.DebugQuad quad : GreedyDebugStore.getQuadsNear(sectionX, sectionY, sectionZ, RADIUS)) {
            if (drawWireframe) {
                drawEdge(lines, pose, quad.x0(), quad.y0() + WIREFRAME_Y_OFFSET, quad.z0(), quad.x1(), quad.y1() + WIREFRAME_Y_OFFSET, quad.z1(), alpha, 0.0f, 1.0f, 0.0f);
                drawEdge(lines, pose, quad.x1(), quad.y1() + WIREFRAME_Y_OFFSET, quad.z1(), quad.x2(), quad.y2() + WIREFRAME_Y_OFFSET, quad.z2(), alpha, 0.0f, 1.0f, 0.0f);
                drawEdge(lines, pose, quad.x2(), quad.y2() + WIREFRAME_Y_OFFSET, quad.z2(), quad.x3(), quad.y3() + WIREFRAME_Y_OFFSET, quad.z3(), alpha, 0.0f, 1.0f, 0.0f);
                drawEdge(lines, pose, quad.x3(), quad.y3() + WIREFRAME_Y_OFFSET, quad.z3(), quad.x0(), quad.y0() + WIREFRAME_Y_OFFSET, quad.z0(), alpha, 0.0f, 1.0f, 0.0f);
            }
            if (drawTriangles) {
                drawEdge(lines, pose, quad.x0(), quad.y0() + WIREFRAME_Y_OFFSET, quad.z0(), quad.x2(), quad.y2() + WIREFRAME_Y_OFFSET, quad.z2(), alpha, 1.0f, 0.85f, 0.1f);
            }
        }

        poseStack.popPose();
    }

    private static void drawEdge(VertexConsumer lines, Matrix4f pose,
                                 float ax, float ay, float az,
                                 float bx, float by, float bz,
                                 float alpha,
                                 float red,
                                 float green,
                                 float blue) {
        if (alpha <= 0.0f) {
            return;
        }

        float nx = bx - ax;
        float ny = by - ay;
        float nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0e-5f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }

        lines.addVertex(pose, ax, ay, az)
                .setColor(red, green, blue, alpha)
                .setNormal(nx, ny, nz)
                .setLineWidth(1.0f);
        lines.addVertex(pose, bx, by, bz)
                .setColor(red, green, blue, alpha)
                .setNormal(nx, ny, nz)
                .setLineWidth(1.0f);
    }
}
