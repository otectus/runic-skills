package com.otectus.runicskills.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.otectus.runicskills.RunicSkills;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The one particle the Powers system draws. Colour, glyph, size, spin, lifetime and motion are all
 * constructor arguments, so there is no per-Power particle class and no per-Power registration —
 * seventy-five Powers share this, and a descriptor decides what each looks like.
 *
 * <h2>Why it is not a registered {@code ParticleType}</h2>
 * A registered type would need a {@code ParticleOptions} implementation with a codec purely to carry
 * a colour and a sprite index across a boundary this never crosses: the proc packet has already
 * arrived and been validated by the time anything here runs, and these are spawned directly into the
 * local {@code ParticleEngine}. Registering one would add a network format, a JSON sprite manifest
 * and a provider for no behaviour the client does not already have in hand.
 *
 * <p>It draws from its own 4x2 sheet rather than the vanilla particle atlas, which is what lets the
 * tier silhouettes be exact 16px shapes instead of whatever the atlas happens to contain.
 */
public class RunicGlyphParticle extends Particle {

    private static final ResourceLocation SHEET =
            new ResourceLocation(RunicSkills.MOD_ID, "textures/particle/runic_glyphs.png");

    private static final int SHEET_COLS = 4;
    private static final int SHEET_ROWS = 2;

    /**
     * Custom render type so the glyph sheet can be bound instead of the particle atlas.
     *
     * <p>{@code end} restores blend state rather than leaving it on. Every other borrow site in this
     * mod does the same now; a render type is the easiest place in the codebase to leak it from,
     * because nothing downstream looks like it belongs to us.
     */
    public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            // depthMask off: glyphs are translucent overlays and should not occlude one another.
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, SHEET);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        @Override
        public String toString() {
            return "runicskills:runic_glyph";
        }
    };

    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;
    private final float baseSize;
    private final float spin;
    /** Fraction of the lifetime spent fading in, so a glyph resolves rather than popping. */
    private static final float FADE_IN = 0.25F;

    /**
     * @param glyph     index into the sheet, see the {@code GLYPH_*} constants on
     *                  {@link PowerVfxStyle}
     * @param rgb       packed 0xRRGGBB
     * @param size      quad half-size in blocks
     * @param lifetime  ticks the glyph lives for
     * @param spin      radians per tick of roll; 0 for a glyph that should stay upright
     */
    public RunicGlyphParticle(ClientLevel level, Vec3 pos, Vec3 velocity,
                              int glyph, int rgb, float size, int lifetime, float spin) {
        super(level, pos.x, pos.y, pos.z);
        int cell = Math.floorMod(glyph, SHEET_COLS * SHEET_ROWS);
        int col = cell % SHEET_COLS;
        int row = cell / SHEET_COLS;
        // Inset by a fraction of a texel so neighbouring cells cannot bleed in under filtering.
        float inset = 0.001F;
        this.u0 = (float) col / SHEET_COLS + inset;
        this.u1 = (float) (col + 1) / SHEET_COLS - inset;
        this.v0 = (float) row / SHEET_ROWS + inset;
        this.v1 = (float) (row + 1) / SHEET_ROWS - inset;

        this.rCol = ((rgb >> 16) & 0xFF) / 255.0F;
        this.gCol = ((rgb >> 8) & 0xFF) / 255.0F;
        this.bCol = (rgb & 0xFF) / 255.0F;
        this.alpha = 1.0F;

        this.baseSize = size;
        this.spin = spin;
        this.lifetime = Math.max(1, lifetime);
        this.gravity = 0.0F;
        this.hasPhysics = false;

        this.xd = velocity.x;
        this.yd = velocity.y;
        this.zd = velocity.z;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.move(this.xd, this.yd, this.zd);
        // Ease out rather than travelling at a constant speed: the glyph arrives, then settles.
        this.xd *= 0.86D;
        this.yd *= 0.86D;
        this.zd *= 0.86D;

        this.oRoll = this.roll;
        this.roll += this.spin;

        float progress = (float) this.age / this.lifetime;
        this.alpha = progress < FADE_IN
                ? progress / FADE_IN
                : 1.0F - (progress - FADE_IN) / (1.0F - FADE_IN);
    }

    /** Grows slightly over its life, which reads as a pulse rather than a static decal. */
    private float quadSize(float partialTicks) {
        float progress = ((float) this.age + partialTicks) / this.lifetime;
        return this.baseSize * (0.75F + 0.45F * Mth.clamp(progress, 0.0F, 1.0F));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());

        Quaternionf rotation;
        if (this.roll == 0.0F) {
            rotation = camera.rotation();
        } else {
            rotation = new Quaternionf(camera.rotation());
            rotation.rotateZ(Mth.lerp(partialTicks, this.oRoll, this.roll));
        }

        Vector3f[] corners = new Vector3f[]{
                new Vector3f(-1.0F, -1.0F, 0.0F),
                new Vector3f(-1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, -1.0F, 0.0F)
        };
        float size = quadSize(partialTicks);
        for (Vector3f corner : corners) {
            corner.rotate(rotation);
            corner.mul(size);
            corner.add(px, py, pz);
        }

        int light = this.getLightColor(partialTicks);
        vertex(buffer, corners[0], this.u1, this.v1, light);
        vertex(buffer, corners[1], this.u1, this.v0, light);
        vertex(buffer, corners[2], this.u0, this.v0, light);
        vertex(buffer, corners[3], this.u0, this.v1, light);
    }

    private void vertex(VertexConsumer buffer, Vector3f at, float u, float v, int light) {
        buffer.vertex(at.x(), at.y(), at.z())
                .uv(u, v)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light)
                .endVertex();
    }
}
