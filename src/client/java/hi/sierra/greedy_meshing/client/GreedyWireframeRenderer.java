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

/**
 * Greedy-mesh debug visualization, rendered as translucent FILLED quads (not line outlines).
 *
 * <p>Lines are deliberately avoided: VulkanMod 0.5.x's {@code rendertype_lines} shader is broken on
 * MoltenVK (every line explodes into a giant screen-spanning triangle), so the outline overlay is
 * unusable there. Filled quads go through ordinary {@code debugQuads} (POSITION_COLOR / QUADS /
 * translucent) geometry that VulkanMod renders correctly on every version. Each filled quad is inset
 * slightly so neighbours stay visually separated — for the comparison view the vanilla side is filled
 * one cell per block (you see the per-block grid) while the greedy side fills the whole merged run.
 */
public final class GreedyWireframeRenderer {
    private static final int RADIUS = 1;

    private GreedyWireframeRenderer() {
    }

    //? if >=26.2 {
    /*public static void render(LevelRenderContext context) {
        boolean drawWireframe = GreedyConfig.debugWireframe();
        boolean drawTriangles = GreedyConfig.debugTrianglesHud();
        boolean drawComparison = GreedyConfig.debugComparison();
        if (!GreedyRuntimeState.isRuntimeGreedyActive() || (!drawWireframe && !drawTriangles && !drawComparison) || context.submitNodeCollector() == null) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        if (poseStack == null) {
            return;
        }

        Vec3 cameraPos = context.levelState().cameraRenderState.pos;

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Lines normally; filled quads only under VulkanMod <0.6 (its line shader is broken on MoltenVK).
        boolean fills = GreedyDebugDraw.useFills();
        GreedyDebugDraw.setCamera(cameraPos.x, cameraPos.y, cameraPos.z);
        float alpha = Math.max(0.0f, Math.min(1.0f, GreedyConfig.meshOpacity()));
        int sectionX = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.x));
        int sectionY = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.y));
        int sectionZ = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.z));

        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
        float yaw = (float) Math.toRadians(camera.yRot());
        float rightX = (float) Math.cos(yaw);
        float rightZ = (float) Math.sin(yaw);

        java.util.List<GreedyDebugStore.DebugQuad> quads = GreedyDebugStore.getQuadsNear(sectionX, sectionY, sectionZ, RADIUS);
        net.minecraft.client.renderer.rendertype.RenderType renderType = fills ? RenderTypes.debugQuads() : RenderTypes.lines();

        // 26.2's rendering pipeline is submission-based: the VertexConsumer is only valid inside this
        // deferred callback, so (unlike older versions) the per-quad draw loop must live in here rather
        // than after an eagerly-acquired VertexConsumer.
        context.submitNodeCollector().submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            Matrix4f matrixPose = pose.pose();
            for (GreedyDebugStore.DebugQuad quad : quads) {
                float ex = quad.x1() - quad.x0(), ey = quad.y1() - quad.y0(), ez = quad.z1() - quad.z0();
                float fx = quad.x3() - quad.x0(), fy = quad.y3() - quad.y0(), fz = quad.z3() - quad.z0();

                float cx = (quad.x0() + quad.x2()) * 0.5f - (float) cameraPos.x;
                float cz = (quad.z0() + quad.z2()) * 0.5f - (float) cameraPos.z;
                boolean isLeftHalf = (cx * rightX + cz * rightZ) < 0;

                if (drawWireframe || drawComparison) {
                    boolean showGreedyFill = drawWireframe && (!drawComparison || isLeftHalf);
                    boolean showVanillaGrid = drawComparison && !isLeftHalf;

                    if (showGreedyFill) {
                        GreedyDebugDraw.outline(consumer, matrixPose, quad, fills, alpha, 0.0f, 1.0f, 0.0f);
                    }
                    if (showVanillaGrid) {
                        int w = Math.max(1, Math.round((float) Math.sqrt(ex * ex + ey * ey + ez * ez)));
                        int h = Math.max(1, Math.round((float) Math.sqrt(fx * fx + fy * fy + fz * fz)));
                        GreedyDebugDraw.grid(consumer, matrixPose, quad, ex, ey, ez, fx, fy, fz, w, h, fills, alpha, 1.0f, 0.3f, 0.3f);
                    }
                }
                if (drawTriangles) {
                    GreedyDebugDraw.triangles(consumer, matrixPose, quad, fills, alpha, 1.0f, 0.85f, 0.1f);
                }
            }
        });

        poseStack.popPose();
        return;
    *///?} else if UNOBFUSCATED {
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

    //? if <26.2 {
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f pose = poseStack.last().pose();
        // Lines normally; filled quads only under VulkanMod <0.6 (its line shader is broken on MoltenVK).
        boolean fills = GreedyDebugDraw.useFills();
        //? if UNOBFUSCATED {
        /*VertexConsumer consumer = fills ? context.bufferSource().getBuffer(RenderTypes.debugQuads()) : context.bufferSource().getBuffer(RenderTypes.lines());
        *///?} else if >=1.21.11 {
        /*VertexConsumer consumer = fills ? context.consumers().getBuffer(RenderTypes.debugQuads()) : context.consumers().getBuffer(RenderTypes.lines());
        *///?} else {
        VertexConsumer consumer = fills ? context.consumers().getBuffer(RenderType.debugQuads()) : context.consumers().getBuffer(RenderType.lines());
        //?}
        GreedyDebugDraw.setCamera(cameraPos.x, cameraPos.y, cameraPos.z);
        float alpha = Math.max(0.0f, Math.min(1.0f, GreedyConfig.meshOpacity()));
        int sectionX = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.x));
        int sectionY = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.y));
        int sectionZ = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.z));

        //? if >=26.2 {
        /*net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
        *///?} else {
        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        //?}
        //? if >=1.21.11 {
        /*float yaw = (float) Math.toRadians(camera.yRot());
        *///?} else {
        float yaw = (float) Math.toRadians(camera.getYRot());
        //?}
        float rightX = (float) Math.cos(yaw);
        float rightZ = (float) Math.sin(yaw);

        for (GreedyDebugStore.DebugQuad quad : GreedyDebugStore.getQuadsNear(sectionX, sectionY, sectionZ, RADIUS)) {
            float ex = quad.x1() - quad.x0(), ey = quad.y1() - quad.y0(), ez = quad.z1() - quad.z0();
            float fx = quad.x3() - quad.x0(), fy = quad.y3() - quad.y0(), fz = quad.z3() - quad.z0();

            float cx = (quad.x0() + quad.x2()) * 0.5f - (float) cameraPos.x;
            float cz = (quad.z0() + quad.z2()) * 0.5f - (float) cameraPos.z;
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
    //?}
    }
}
