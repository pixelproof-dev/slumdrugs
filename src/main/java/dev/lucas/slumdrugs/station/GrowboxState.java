package dev.lucas.slumdrugs.station;

/** A bounded, elapsed-time simulation: growth stops when the water runs out. */
public final class GrowboxState {
    public String drug;
    public String grower = "unknown";
    public double progress;
    public double waterSeconds;
    public int fertilizer;
    public boolean lamp = true;
    public long updatedAt = System.currentTimeMillis();

    public void advance(long now, double growthSeconds) {
        double elapsed = Math.max(0, now - updatedAt) / 1000.0;
        updatedAt = Math.max(updatedAt, now);
        if (drug == null || !lamp || progress >= 1 || growthSeconds <= 0) return;
        double active = Math.min(elapsed, Math.max(0, waterSeconds));
        double rate = (1 + Math.max(0, Math.min(3, fertilizer)) * 0.15) / growthSeconds;
        active = Math.min(active, (1 - progress) / rate);
        progress = Math.min(1, progress + active * rate);
        waterSeconds = Math.max(0, waterSeconds - active);
    }
    public int stage() { return drug == null ? 0 : progress >= 1 ? 4 : 1 + (int)(progress * 3); }
    public void clear() { drug = null; progress = 0; fertilizer = 0; }
}
