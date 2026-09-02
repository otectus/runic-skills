package com.otectus.runicskills.client.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Ore Detector — "Nearby ores glow through walls, revealing their location".
 *
 * <p>Blocks cannot glow. The glowing outline vanilla draws through walls is an entity effect, and
 * an ore is not an entity — so unlike Mystic Sight and Tracking, which hand their work to
 * {@code MobEffects.GLOWING}, this perk has to draw its own outlines.
 *
 * <p>It does so entirely on the client and sends nothing over the network. The client already has
 * every block in the chunks around the player, so it can answer "where is the nearby ore" without
 * asking the server, and the perk's own state is already synced to the client for the skills screen
 * to read. That also means a player without the perk pays nothing at all: the scan never starts.
 *
 * <p>The outlines are drawn with the depth test off, which is what "through walls" means, using a
 * plain position-colour buffer rather than a {@code RenderType} — every vanilla line type has
 * depth testing baked into its state, so borrowing one would have drawn outlines that stone hides.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT)
public final class OreDetectorRenderer {

    private OreDetectorRenderer() {}

    /**
     * How far the scan reaches, in blocks.
     *
     * <p>A constant rather than a config field because the perk has no value to configure — it
     * ships with a required level and nothing else. Twelve blocks is a little beyond normal mining
     * reach in every direction, which is a useful hint without turning into an x-ray of the chunk.
     */
    private static final int REACH = 12;

    /** How often the surrounding blocks are re-scanned. Ore does not move; this need not be fast. */
    private static final int SCAN_INTERVAL_TICKS = 40;

    /**
     * The most ore positions that will be drawn at once.
     *
     * <p>Standing in a large modded ore vein or an amethyst geode can otherwise produce thousands
     * of boxes, and this is a hint, not a rendering benchmark.
     */
    private static final int MAX_MARKED = 512;

    /** Positions found by the last scan, in the level they were found in. */
    private static final List<BlockPos> MARKED = new ArrayList<>();
    private static Level markedLevel;
    private static int lastScanTick;

    /** Drops the cache when the perk switches off, the player leaves, or the level changes. */
    private static void clear() {
        if (!MARKED.isEmpty()) MARKED.clear();
        markedLevel = null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null || client.level == null) {
            clear();
            return;
        }
        if (RegistryPerks.ORE_DETECTOR == null || !RegistryPerks.ORE_DETECTOR.get().isEnabled()) {
            clear();
            return;
        }
        if (client.level != markedLevel) {
            clear();
            markedLevel = client.level;
        }
        if (player.tickCount - lastScanTick < SCAN_INTERVAL_TICKS && !MARKED.isEmpty()) return;
        lastScanTick = player.tickCount;
        scan(client.level, player.blockPosition());
    }

    private static void scan(Level level, BlockPos centre) {
        MARKED.clear();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dy = -REACH; dy <= REACH; dy++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (MARKED.size() >= MAX_MARKED) return;
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    if (level.getBlockState(cursor).is(Tags.Blocks.ORES)) MARKED.add(cursor.immutable());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // After the translucent pass so the outlines sit on top of water and glass rather than
        // being sorted against them, and before the level renderer tears its state down.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (MARKED.isEmpty()) return;
        if (RegistryPerks.ORE_DETECTOR == null || !RegistryPerks.ORE_DETECTOR.get().isEnabled()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0F);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (BlockPos pos : MARKED) outlineBlock(buffer, matrix, pos);
        tesselator.end();

        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        pose.popPose();
    }

    /** The outline colour: a warm amber, translucent enough not to blind a player in a tunnel. */
    private static final float R = 1.0F;
    private static final float G = 0.75F;
    private static final float B = 0.25F;
    private static final float A = 0.55F;

    /** Emits the twelve edges of a block's cube as line pairs. */
    private static void outlineBlock(BufferBuilder buffer, Matrix4f matrix, BlockPos pos) {
        float x0 = pos.getX();
        float y0 = pos.getY();
        float z0 = pos.getZ();
        float x1 = x0 + 1.0F;
        float y1 = y0 + 1.0F;
        float z1 = z0 + 1.0F;

        // bottom face
        edge(buffer, matrix, x0, y0, z0, x1, y0, z0);
        edge(buffer, matrix, x1, y0, z0, x1, y0, z1);
        edge(buffer, matrix, x1, y0, z1, x0, y0, z1);
        edge(buffer, matrix, x0, y0, z1, x0, y0, z0);
        // top face
        edge(buffer, matrix, x0, y1, z0, x1, y1, z0);
        edge(buffer, matrix, x1, y1, z0, x1, y1, z1);
        edge(buffer, matrix, x1, y1, z1, x0, y1, z1);
        edge(buffer, matrix, x0, y1, z1, x0, y1, z0);
        // uprights
        edge(buffer, matrix, x0, y0, z0, x0, y1, z0);
        edge(buffer, matrix, x1, y0, z0, x1, y1, z0);
        edge(buffer, matrix, x1, y0, z1, x1, y1, z1);
        edge(buffer, matrix, x0, y0, z1, x0, y1, z1);
    }

    private static void edge(BufferBuilder buffer, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        buffer.vertex(matrix, x0, y0, z0).color(R, G, B, A).endVertex();
        buffer.vertex(matrix, x1, y1, z1).color(R, G, B, A).endVertex();
    }
}
