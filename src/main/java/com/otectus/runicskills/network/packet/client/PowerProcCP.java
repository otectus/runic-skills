package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.PacketBounds;
import com.otectus.runicskills.event.PowerProcEvent;
import com.otectus.runicskills.network.ServerNetworking;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Server to clients: a Power fired, and here is everything needed to present it.
 *
 * <p>What this replaced was a placeholder that had outlived its own comment. It carried a Power
 * name and a game-time stamp; the client used neither to decide anything, ignoring the stamp
 * entirely and spawning eight randomly placed vanilla {@code ENCHANT} particles around
 * <em>the local player</em>, whatever the Power was and wherever it had actually happened. It was
 * sent only to the caster, so a proc was invisible to everyone else in the fight, and the name was
 * decoded with the {@code readUtf} default of 32,767 characters for a value that is never more
 * than about thirty.
 *
 * <h2>Wire format</h2>
 * The id is the only string; school, tier, icon, palette, glyph and sound are all resolved from it
 * on the client against the catalog both sides already share, so none of them travel. Numeric
 * fields are the smallest thing that can carry them: entity ids as varints, {@code variant} and
 * {@code intensity} as unsigned bytes, presentation booleans packed into one flag byte.
 *
 * <p>{@code intensity} is normalised 0-255 and is <b>never raw damage</b>. Sending damage would
 * publish balance numbers to every client in tracking range, and would make the visual scale with
 * the player's gear rather than with the Power that fired.
 *
 * <h2>This channel is presentation-only</h2>
 * Nothing it carries may reach gameplay. The handler resolves entities and coordinates defensively
 * and discards anything implausible: an unknown Power id, a missing entity, a non-finite or absurd
 * origin. A malformed packet costs a dropped visual and nothing else.
 */
public class PowerProcCP {

    /** Presentation flags. Deliberately not gameplay state. */
    public static final int FLAG_CRITICAL = 1;
    public static final int FLAG_OWNER_ONLY = 1 << 1;

    /**
     * How far the origin may sit from the caster before it is replaced by the caster's own
     * position. Generous enough for any area effect this mod has, small enough that a corrupt or
     * hostile coordinate cannot make a client render at the far edge of the world.
     */
    private static final double MAX_ORIGIN_DISTANCE = 64.0;

    /** Radius the proc is broadcast within when it is not owner-only. */
    private static final double PRESENTATION_RADIUS = 48.0;

    private final String powerId;
    private final int sourceEntityId;
    private final int targetEntityId;
    private final double x;
    private final double y;
    private final double z;
    private final int variant;
    private final int intensity;
    private final int seed;
    private final int flags;

    public PowerProcCP(String powerId, int sourceEntityId, int targetEntityId,
                       Vec3 origin, int variant, int intensity, int seed, int flags) {
        this.powerId = powerId;
        this.sourceEntityId = sourceEntityId;
        this.targetEntityId = targetEntityId;
        this.x = origin.x;
        this.y = origin.y;
        this.z = origin.z;
        this.variant = variant & 0xFF;
        this.intensity = intensity & 0xFF;
        this.seed = seed;
        this.flags = flags & 0xFF;
    }

    public PowerProcCP(FriendlyByteBuf buffer) {
        this.powerId = buffer.readUtf(PacketBounds.MAX_CONTENT_ID_CHARS);
        if (!PacketBounds.isContentIdValid(this.powerId)) {
            throw new DecoderException("PowerProcCP: malformed power id");
        }
        this.sourceEntityId = buffer.readVarInt();
        this.targetEntityId = buffer.readVarInt();
        this.x = buffer.readDouble();
        this.y = buffer.readDouble();
        this.z = buffer.readDouble();
        if (!Double.isFinite(this.x) || !Double.isFinite(this.y) || !Double.isFinite(this.z)) {
            throw new DecoderException("PowerProcCP: non-finite origin");
        }
        this.variant = buffer.readUnsignedByte();
        this.intensity = buffer.readUnsignedByte();
        this.seed = buffer.readInt();
        this.flags = buffer.readUnsignedByte();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.powerId, PacketBounds.MAX_CONTENT_ID_CHARS);
        buffer.writeVarInt(this.sourceEntityId);
        buffer.writeVarInt(this.targetEntityId);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeByte(this.variant);
        buffer.writeByte(this.intensity);
        buffer.writeInt(this.seed);
        buffer.writeByte(this.flags);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientHandler.present(this));
        context.setPacketHandled(true);
    }

    public String powerId() {
        return powerId;
    }

    public int sourceEntityId() {
        return sourceEntityId;
    }

    public int targetEntityId() {
        return targetEntityId;
    }

    public Vec3 origin() {
        return new Vec3(x, y, z);
    }

    public int variant() {
        return variant;
    }

    public int intensity() {
        return intensity;
    }

    public int seed() {
        return seed;
    }

    public boolean critical() {
        return (flags & FLAG_CRITICAL) != 0;
    }

    public boolean ownerOnly() {
        return (flags & FLAG_OWNER_ONLY) != 0;
    }

    /**
     * Builds and sends the presentation for a committed proc.
     *
     * <p>Owner-only procs go to the caster alone; everything else goes to every client tracking the
     * area, which is what makes another player's Power visible at all. The origin is validated
     * here, on the way out, so a bug in a dispatcher cannot become a rendering artefact on somebody
     * else's screen.
     */
    public static void send(PowerProcEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)) return;
        if (event.getPower() == null) return;

        Vec3 candidate = event.getOrigin();
        if (candidate == null || !Double.isFinite(candidate.x) || !Double.isFinite(candidate.y)
                || !Double.isFinite(candidate.z)
                || candidate.distanceToSqr(caster.position())
                        > MAX_ORIGIN_DISTANCE * MAX_ORIGIN_DISTANCE) {
            candidate = caster.position();
        }
        final Vec3 origin = candidate;

        Entity target = event.getTarget();
        int flags = (event.isCritical() ? FLAG_CRITICAL : 0)
                | (event.isOwnerOnly() ? FLAG_OWNER_ONLY : 0);
        // Seeded from state both sides agree is arbitrary but stable for this one proc, so the
        // particle scatter and the pitch come out identical on every client that renders it.
        int seed = (int) (caster.getUUID().getLeastSignificantBits() * 31L
                + (long) event.getPower().getName().hashCode() * 17L
                + caster.level().getGameTime());

        PowerProcCP packet = new PowerProcCP(
                event.getPower().key.toString(),
                caster.getId(),
                target != null ? target.getId() : -1,
                origin,
                event.getVariant(),
                event.getIntensity(),
                seed,
                flags);

        if (event.isOwnerOnly()) {
            ServerNetworking.sendToPlayer(packet, caster);
            return;
        }
        ServerNetworking.sendNear(packet, origin, PRESENTATION_RADIUS, caster.level().dimension());
        // A caster outside the radius of an origin they caused (a long-range area proc) would
        // otherwise miss their own feedback. The client coalesces the duplicate.
        if (caster.position().distanceToSqr(origin) > PRESENTATION_RADIUS * PRESENTATION_RADIUS) {
            ServerNetworking.sendToPlayer(packet, caster);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void present(PowerProcCP packet) {
            try {
                com.otectus.runicskills.client.vfx.PowerProcVfxManager.accept(packet);
            } catch (Throwable t) {
                // Presentation must never take the client down. A descriptor that cannot resolve,
                // a Power that only exists on the server, an entity that has already despawned --
                // each is a dropped visual, not an error.
                RunicSkills.getLOGGER().debug("Power proc presentation failed: {}", t.toString());
            }
        }
    }
}
