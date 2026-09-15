package adris.altoclef.tasks.speedrun.stronghold;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simplified Ninjabrain-style stronghold calculator for Altoclef bots.
 *
 * Core ideas from Ninjabrain Bot (see triangulation.pdf):
 * 1. Eyes point at the stronghold chunk origin (after snapping).
 * 2. Angle measurements have noise → model with Gaussian on angle error.
 * 3. Strongholds only exist in discrete rings → use ring prior.
 * 4. Rank candidate chunks by posterior weight.
 *
 * Bot advantage: we can read the EyeOfEnderEntity position and compute
 * the true direction with essentially zero aim error. Sigma is kept
 * small but non-zero to account for entity motion / timing.
 *
 * This is NOT a full port of Ninjabrain Bot (no divine, no full multi-ring
 * Bayesian prior over all 128 holds). It is good enough for RSG first-ring
 * runs which cover ~99% of casual/bot speedruns.
 */
public class NinjabrainCalculator {

    // First ring (Java): ~1280–2816 blocks from origin, 3 strongholds
    private static final double RING1_MIN = 1200;
    private static final double RING1_MAX = 2900;

    // Second ring fallback
    private static final double RING2_MIN = 4300;
    private static final double RING2_MAX = 6000;

    /** Angle std-dev in degrees. Bots can use a tight value. */
    private double sigmaDegrees = 0.05;

    /** Optional divine fossil X (0-15). -1 = unused. Restricts angle sector. */
    private int divineFossilX = -1;

    private final List<EyeThrow> throwsList = new ArrayList<>();

    public void setSigmaDegrees(double sigma) {
        this.sigmaDegrees = Math.max(0.001, sigma);
    }

    public void setDivineFossilX(int fossilX) {
        this.divineFossilX = fossilX < 0 ? -1 : Math.floorMod(fossilX, 16);
    }

    public int getDivineFossilX() {
        return divineFossilX;
    }

    public void clearThrows() {
        throwsList.clear();
    }

    public void addThrow(EyeThrow t) {
        throwsList.add(t);
    }

    public int throwCount() {
        return throwsList.size();
    }

    public List<EyeThrow> getThrows() {
        return new ArrayList<>(throwsList);
    }

    /**
     * Rank candidate chunks. Returns best-first list (may be empty).
     */
    public List<StrongholdPrediction> predict(double playerX, double playerZ, int maxResults) {
        if (throwsList.isEmpty()) return List.of();

        List<int[]> candidates = generateCandidates(playerX, playerZ);
        List<Scored> scored = new ArrayList<>();

        double weightSum = 0;
        for (int[] c : candidates) {
            int cx = c[0], cz = c[1];
            // Eye points at chunk origin (0,0 of chunk)
            double ox = cx * 16.0;
            double oz = cz * 16.0;

            double logW = 0;
            boolean ok = true;
            for (EyeThrow t : throwsList) {
                double angleErr = angleErrorDegrees(t, ox, oz);
                // Gaussian likelihood on angle error
                double z = angleErr / sigmaDegrees;
                // log N(0, sigma): -0.5 * (err/sigma)^2  (drop constant)
                logW += -0.5 * z * z;

                // Must be roughly in front of the throw (not behind)
                double dx = ox - t.x;
                double dz = oz - t.z;
                double forward = dx * t.dirX() + dz * t.dirZ();
                if (forward < 50) { // stronghold should be ahead
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;

            // Distance prior: prefer closer candidates (nearest stronghold)
            double dist = Math.hypot(ox - playerX, oz - playerZ);
            // Soft distance penalty
            logW -= dist / 5000.0;

            // Ring prior boost for first ring
            double r = Math.hypot(ox, oz);
            if (r >= RING1_MIN && r <= RING1_MAX) {
                logW += 2.0; // strong boost
            } else if (r >= RING2_MIN && r <= RING2_MAX) {
                logW += 0.5;
            } else {
                logW -= 1.0;
            }

            // Divine sector prior: fossil X splits ring into 16 slices
            if (divineFossilX >= 0) {
                double angle = Math.atan2(-ox, oz); // match MC yaw-ish polar
                if (angle < 0) angle += 2 * Math.PI;
                int sector = (int) Math.floor(angle / (2 * Math.PI) * 16) % 16;
                // Accept matching sector ±1
                int diff = Math.min(Math.floorMod(sector - divineFossilX, 16),
                        Math.floorMod(divineFossilX - sector, 16));
                if (diff == 0) {
                    logW += 3.0;
                } else if (diff == 1) {
                    logW += 1.0;
                } else {
                    logW -= 2.0;
                }
            }

            double w = Math.exp(logW);
            weightSum += w;
            scored.add(new Scored(cx, cz, w, dist));
        }

        if (scored.isEmpty() || weightSum <= 0) return List.of();

        scored.sort(Comparator.comparingDouble((Scored s) -> -s.w));
        List<StrongholdPrediction> out = new ArrayList<>();
        int n = Math.min(maxResults, scored.size());
        for (int i = 0; i < n; i++) {
            Scored s = scored.get(i);
            out.add(new StrongholdPrediction(s.cx, s.cz, s.w / weightSum, s.dist));
        }
        return out;
    }

    public StrongholdPrediction best(double playerX, double playerZ) {
        List<StrongholdPrediction> list = predict(playerX, playerZ, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Angle between throw direction and vector to candidate, in degrees.
     */
    public static double angleErrorDegrees(EyeThrow t, double targetX, double targetZ) {
        double dx = targetX - t.x;
        double dz = targetZ - t.z;
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) return 180;
        dx /= len;
        dz /= len;
        double dot = dx * t.dirX() + dz * t.dirZ();
        dot = Math.max(-1, Math.min(1, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    /**
     * Generate candidate chunk coords along each throw ray within ring distances.
     */
    private List<int[]> generateCandidates(double playerX, double playerZ) {
        // Use a set of chunk keys
        java.util.LinkedHashMap<Long, int[]> map = new java.util.LinkedHashMap<>();

        for (EyeThrow t : throwsList) {
            // Sample points along the ray from min to max ring distance
            for (double dist = 800; dist <= 6500; dist += 16) {
                double px = t.x + t.dirX() * dist;
                double pz = t.z + t.dirZ() * dist;
                int cx = (int) Math.floor(px / 16.0);
                int cz = (int) Math.floor(pz / 16.0);
                // Also neighbors (snapping / biome search can shift a few chunks)
                for (int ox = -2; ox <= 2; ox++) {
                    for (int oz = -2; oz <= 2; oz++) {
                        int ccx = cx + ox;
                        int ccz = cz + oz;
                        long key = (((long) ccx) << 32) ^ (ccz & 0xffffffffL);
                        map.putIfAbsent(key, new int[]{ccx, ccz});
                    }
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    private static final class Scored {
        final int cx, cz;
        final double w, dist;
        Scored(int cx, int cz, double w, double dist) {
            this.cx = cx;
            this.cz = cz;
            this.w = w;
            this.dist = dist;
        }
    }
}
