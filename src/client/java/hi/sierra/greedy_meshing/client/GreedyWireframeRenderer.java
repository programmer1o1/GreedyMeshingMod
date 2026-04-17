package hi.sierra.greedy_meshing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if UNOBFUSCATED {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
*///?} else if >=1.21.11 {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
//?}
import net.minecraft.core.SectionPos;
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderTypes;
*///?} else {
import net.minecraft.client.renderer.RenderType;
//?}
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import hi.sierra.greedy_meshing.GreedyConfig;
import org.joml.Matrix4f;

public final class GreedyWireframeRenderer {
    private static final int RADIUS = 1;

    private GreedyWireframeRenderer() {
    }

    //? if UNOBFUSCATED {
    /*public static void render(LevelRenderContext context) {
        boolean drawWireframe = GreedyConfig.debugWireframe();
        boolean drawTriangles = GreedyConfig.debugTrianglesHud();
        boolean drawComparison = GreedyConfig.debugComparison();
        if (!GreedyRuntimeState.isRuntimeGreedyActive() || (!drawWireframe && !drawTriangles && !drawComparison) || context.bufferSource() == null) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        if (poseStack == null) {
            return;
        }

        Vec3 cameraPos = context.levelState().cameraRenderState.pos;
    *///?} else if >=1.21.11 {
    /*public static void render(WorldRenderContext context) {
        boolean drawWireframe = GreedyConfig.debugWireframe();
        boolean drawTriangles = GreedyConfig.debugTrianglesHud();
        boolean drawComparison = GreedyConfig.debugComparison();
        if (!GreedyRuntimeState.isRuntimeGreedyActive() || (!drawWireframe && !drawTriangles && !drawComparison) || context.consumers() == null) {
            return;
        }

        PoseStack poseStack = context.matrices();
        if (poseStack == null) {
            return;
        }

        Vec3 cameraPos = context.worldState().cameraRenderState.pos;
    *///?} else {
    public static void render(WorldRenderContext context) {
        boolean drawWireframe = GreedyConfig.debugWireframe();
        boolean drawTriangles = GreedyConfig.debugTrianglesHud();
        boolean drawComparison = GreedyConfig.debugComparison();
        if (!GreedyRuntimeState.isRuntimeGreedyActive() || (!drawWireframe && !drawTriangles && !drawComparison) || context.consumers() == null) {
            return;
        }

        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) {
            return;
        }

        Vec3 cameraPos = context.camera().getPosition();
    //?}

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f pose = poseStack.last().pose();
        //? if UNOBFUSCATED {
        /*VertexConsumer lines = context.bufferSource().getBuffer(RenderTypes.lines());
        *///?} else if >=1.21.11 {
        /*VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
        *///?} else {
        VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());
        //?}
        float alpha = Math.max(0.0f, Math.min(1.0f, GreedyConfig.meshOpacity()));
        int sectionX = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.x));
        int sectionY = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.y));
        int sectionZ = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.z));

        // Camera right vector for determining left/right split
        // Right = lookDir cross upDir. Use camera yaw to compute.
        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        //? if >=1.21.11 {
        /*float yaw = (float) Math.toRadians(camera.yRot());
        *///?} else {
        float yaw = (float) Math.toRadians(camera.getYRot());
        //?}
        // Camera look direction (horizontal): (-sin(yaw), 0, cos(yaw))
        // Camera right direction: (cos(yaw), 0, sin(yaw))
        float rightX = (float) Math.cos(yaw);
        float rightZ = (float) Math.sin(yaw);

        for (GreedyDebugStore.DebugQuad quad : GreedyDebugStore.getQuadsNear(sectionX, sectionY, sectionZ, RADIUS)) {
            float ex = quad.x1() - quad.x0(), ey = quad.y1() - quad.y0(), ez = quad.z1() - quad.z0();
            float fx = quad.x3() - quad.x0(), fy = quad.y3() - quad.y0(), fz = quad.z3() - quad.z0();

            // Determine left/right by dotting quad center against camera right vector
            float cx = (quad.x0() + quad.x2()) * 0.5f - (float) cameraPos.x;
            float cz = (quad.z0() + quad.z2()) * 0.5f - (float) cameraPos.z;
            boolean isLeftHalf = (cx * rightX + cz * rightZ) < 0;

            if (drawWireframe || drawComparison) {
                boolean showGreedyOutline = drawWireframe && (!drawComparison || isLeftHalf);
                boolean showVanillaGrid = drawComparison && !isLeftHalf;

                if (showGreedyOutline) {
                    drawEdge(lines, pose, quad.x0(), quad.y0(), quad.z0(), quad.x1(), quad.y1(), quad.z1(), alpha, 0.0f, 1.0f, 0.0f);
                    drawEdge(lines, pose, quad.x1(), quad.y1(), quad.z1(), quad.x2(), quad.y2(), quad.z2(), alpha, 0.0f, 1.0f, 0.0f);
                    drawEdge(lines, pose, quad.x2(), quad.y2(), quad.z2(), quad.x3(), quad.y3(), quad.z3(), alpha, 0.0f, 1.0f, 0.0f);
                    drawEdge(lines, pose, quad.x3(), quad.y3(), quad.z3(), quad.x0(), quad.y0(), quad.z0(), alpha, 0.0f, 1.0f, 0.0f);
                }
                if (showVanillaGrid) {
                    float uLen = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
                    float vLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
                    int w = Math.max(1, Math.round(uLen));
                    int h = Math.max(1, Math.round(vLen));
                    drawEdge(lines, pose, quad.x0(), quad.y0(), quad.z0(), quad.x1(), quad.y1(), quad.z1(), alpha, 1.0f, 0.3f, 0.3f);
                    drawEdge(lines, pose, quad.x1(), quad.y1(), quad.z1(), quad.x2(), quad.y2(), quad.z2(), alpha, 1.0f, 0.3f, 0.3f);
                    drawEdge(lines, pose, quad.x2(), quad.y2(), quad.z2(), quad.x3(), quad.y3(), quad.z3(), alpha, 1.0f, 0.3f, 0.3f);
                    drawEdge(lines, pose, quad.x3(), quad.y3(), quad.z3(), quad.x0(), quad.y0(), quad.z0(), alpha, 1.0f, 0.3f, 0.3f);
                    for (int i = 1; i < w; i++) {
                        float t = (float) i / w;
                        float ax = quad.x0() + ex * t, ay = quad.y0() + ey * t, az = quad.z0() + ez * t;
                        float bx = quad.x3() + ex * t, by = quad.y3() + ey * t, bz = quad.z3() + ez * t;
                        drawEdge(lines, pose, ax, ay, az, bx, by, bz, alpha, 1.0f, 0.3f, 0.3f);
                    }
                    for (int j = 1; j < h; j++) {
                        float t = (float) j / h;
                        float ax = quad.x0() + fx * t, ay = quad.y0() + fy * t, az = quad.z0() + fz * t;
                        float bx = quad.x1() + fx * t, by = quad.y1() + fy * t, bz = quad.z1() + fz * t;
                        drawEdge(lines, pose, ax, ay, az, bx, by, bz, alpha, 1.0f, 0.3f, 0.3f);
                    }
                }
            }
            if (drawTriangles) {
                drawEdge(lines, pose, quad.x0(), quad.y0(), quad.z0(), quad.x2(), quad.y2(), quad.z2(), alpha, 1.0f, 0.85f, 0.1f);
            }
        }

        poseStack.popPose();
    }

    private static void drawEdge(VertexConsumer lines, Matrix4f pose,
                                 float ax, float ay, float az,
                                 float bx, float by, float bz,
                                 float alpha,
                                 float red, float green, float blue) {
        if (alpha <= 0.0f) {
            return;
        }

        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0e-5f) { nx /= len; ny /= len; nz /= len; }

        //? if >=1.21.11 {
        /*lines.addVertex(pose, ax, ay, az).setColor(red, green, blue, alpha).setNormal(nx, ny, nz).setLineWidth(1.0f);
        lines.addVertex(pose, bx, by, bz).setColor(red, green, blue, alpha).setNormal(nx, ny, nz).setLineWidth(1.0f);
        *///?} else {
        lines.addVertex(pose, ax, ay, az).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
        lines.addVertex(pose, bx, by, bz).setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
        //?}
    }
}
