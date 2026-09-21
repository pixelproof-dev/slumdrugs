package dev.lucas.slumdrugs.mod;

/**
 * Tonic mode: the design's no-vice switch. The economy game stays whole and the condition
 * layer becomes tonic fatigue, with no dependence and no withdrawal. Words change with it,
 * through the language keys this class picks.
 */
public final class Tonic {

    private Tonic() {}

    public static boolean on() { return Tuning.loaded() && Tuning.TONIC_MODE.get(); }

    /** The tonic-mode variant of a language key when the mode is on, the key itself otherwise. */
    public static String key(String key) {
        if (!on()) return key;
        int dot = key.indexOf('.', key.indexOf('.') + 1);
        return dot < 0 ? key : key.substring(0, dot) + ".tonic" + key.substring(dot);
    }
}
