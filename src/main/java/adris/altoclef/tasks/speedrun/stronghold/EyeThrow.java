package adris.altoclef.tasks.speedrun.stronghold;

/**
 * One eye-of-ender measurement.
 * For bots we can compute angle from the eye entity position exactly
 * (no human crosshair error). Ninjabrain still applies because
 * stronghold generation has discrete chunk candidates + rings.
 */
public final class EyeThrow {
    public final double x;
    public final double z;
    /** Minecraft yaw degrees: 0 = +Z (south), positive = west, range (-180, 180] */
    public final double yawDegrees;

    public EyeThrow(double x, double z, double yawDegrees) {
        this.x = x;
        this.z = z;
        this.yawDegrees = yawDegrees;
    }

    /**
     * Direction the eye is pointing in the XZ plane.
     * Minecraft: yaw 0 → +Z, yaw 90 → -X, yaw -90 → +X, yaw 180 → -Z
     */
    public double dirX() {
        double rad = Math.toRadians(yawDegrees);
        return -Math.sin(rad);
    }

    public double dirZ() {
        double rad = Math.toRadians(yawDegrees);
        return Math.cos(rad);
    }
}
