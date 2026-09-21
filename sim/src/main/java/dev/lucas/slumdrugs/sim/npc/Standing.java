package dev.lucas.slumdrugs.sim.npc;

import java.util.Map;

/**
 * Where a player stands with each crew, -100 to 100, and what moves it. Standing is zero-sum
 * between opposed pairs: every point gained with one is a point lost with the crew that hates
 * them. It feeds {@link Npc#restingAggression} for every member the player meets.
 */
public final class Standing {

    /** The four crews of the design, and who each one cannot stand. */
    public enum Crew {
        ASHFALL("ashfall", "Ashfall Crew"),
        TIDEWATER("tidewater", "Tidewater Boatmen"),
        CHOIR("choir", "The Glass Choir"),
        QUARRY("quarry", "The Quarry Kings");

        public final String id;
        public final String label;

        Crew(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public Crew opposed() {
            return switch (this) {
                case ASHFALL -> CHOIR;
                case CHOIR -> ASHFALL;
                case TIDEWATER -> QUARRY;
                case QUARRY -> TIDEWATER;
            };
        }

        /** The crew with this id, or null for a crew the design does not know. */
        public static Crew byId(String id) {
            if (id == null) return null;
            for (Crew c : values()) if (c.id.equals(id)) return c;
            return null;
        }
    }

    public static final double MIN = -100;
    public static final double MAX = 100;

    /** Hitting one of theirs. */
    public static final double HIT = -5;

    /** Killing one of theirs. The quarter remembers. */
    public static final double KILL = -25;

    /** The most one tribute can buy. */
    public static final double TRIBUTE_CAP = 10;

    /** Below this a crew is at war with the player. */
    public static final double WAR_AT = -60;

    private Standing() {}

    public static double clamp(double v) { return Math.max(MIN, Math.min(MAX, v)); }

    /** What a tribute is worth: a point a shilling, up to the cap. */
    public static double tribute(long pence) { return tribute(pence, TRIBUTE_CAP); }

    public static double tribute(long pence, double cap) {
        return Math.min(Math.max(0, cap), Math.max(0, pence) / 12.0);
    }

    public static boolean atWar(double standing) { return standing <= WAR_AT; }

    /**
     * Moves standing with a crew, and the opposite way with the crew opposed to it. A crew
     * the design does not know has no opposite, and moves alone.
     */
    public static void apply(Map<String, Double> standings, String crew, double delta) {
        if (crew == null || crew.isBlank()) return;
        standings.merge(crew, delta, (a, b) -> clamp(a + b));
        standings.computeIfPresent(crew, (k, v) -> clamp(v));
        Crew known = Crew.byId(crew);
        if (known != null) {
            String other = known.opposed().id;
            standings.merge(other, -delta, (a, b) -> clamp(a + b));
            standings.computeIfPresent(other, (k, v) -> clamp(v));
        }
    }

    public static double of(Map<String, Double> standings, String crew) {
        if (crew == null || standings == null) return 0;
        return clamp(standings.getOrDefault(crew, 0.0));
    }
}
