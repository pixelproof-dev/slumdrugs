package dev.lucas.slumdrugs.sim.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a person looks like and what they are called, chosen together so the two agree.
 *
 * <p>A look is a skin, known by its file name: the role it dresses comes first, and the name may
 * carry {@code female} or {@code male} and a crew — {@code resident_female_2},
 * {@code bruiser_male_ashfall}. A look without either word fits any name.
 *
 * <p>The look is picked first and the name follows it. The other way round a role with a single
 * skin, a policeman say, would be called Dessa half the time and still wear his moustache.
 */
public final class Looks {

    public enum Sex { FEMALE, MALE, ANY }

    public record Look(String id, String role, Sex sex) {

        /** Reads a skin's file name, without its extension. */
        public static Look of(String id) {
            String[] words = id.toLowerCase(Locale.ROOT).split("_");
            Sex sex = Sex.ANY;
            // Whole words only: "female" contains "male".
            for (int i = 1; i < words.length; i++) {
                if (words[i].equals("female")) sex = Sex.FEMALE;
                else if (words[i].equals("male")) sex = Sex.MALE;
            }
            return new Look(id, words[0], sex);
        }

        boolean fits(Sex wanted) {
            return wanted == Sex.ANY || sex == Sex.ANY || sex == wanted;
        }

        boolean worn(String crew) {
            if (crew.isBlank()) return false;
            String[] words = id.toLowerCase(Locale.ROOT).split("_");
            for (int i = 1; i < words.length; i++) if (words[i].equals(crew)) return true;
            return false;
        }
    }

    static final List<String> FEMALE = List.of(
            "Dessa", "Nen", "Vera", "Gret", "Hollow Jen", "Cass", "Trudy",
            "Jolene", "Mara", "Keisha", "Lina", "Roxy", "Dot", "Imani");

    static final List<String> MALE = List.of(
            "Marlo", "Kip", "Rusty", "Odd Tam", "Ovid", "Wick",
            "Dante", "Moss", "Tico", "Reuben", "Deacon", "Sully", "Big Lou", "Nico");

    /** Names that sit on anybody. */
    static final List<String> EITHER = List.of("Salt", "Pim", "Bramble", "Ash", "Remy", "Jules");

    private Looks() {}

    /** Whose name this is, as far as the lists know. A name from elsewhere fits anyone. */
    public static Sex sexOf(String name) {
        if (FEMALE.contains(name)) return Sex.FEMALE;
        if (MALE.contains(name)) return Sex.MALE;
        return Sex.ANY;
    }

    /** A name for somebody who looks like this. */
    public static String name(Sex sex, long seed) {
        List<String> pool = new ArrayList<>(EITHER);
        if (sex != Sex.MALE) pool.addAll(FEMALE);
        if (sex != Sex.FEMALE) pool.addAll(MALE);
        return pool.get((int) Math.floorMod(mix(seed), (long) pool.size()));
    }

    /**
     * A look for a role, or empty when there is none to give.
     *
     * <p>A crew's own look wins over the rest of the role's. A hand is a resident somebody hired,
     * so with no looks of their own they wear a resident's.
     *
     * @param sex who the look has to fit, {@link Sex#ANY} when there is no name yet
     */
    public static Optional<Look> pick(List<Look> all, Npc.Role role, String crew, Sex sex, long seed) {
        List<Look> choice = forRole(all, role.name().toLowerCase(Locale.ROOT), sex);
        if (choice.isEmpty() && role == Npc.Role.HAND) choice = forRole(all, "resident", sex);
        if (choice.isEmpty()) return Optional.empty();
        List<Look> theirs = choice.stream().filter(l -> l.worn(crew)).toList();
        if (!theirs.isEmpty()) choice = theirs;
        return Optional.of(choice.get((int) Math.floorMod(mix(seed), (long) choice.size())));
    }

    /**
     * A look for somebody who already has a name, falling back to the role's any look when none
     * fits it: better a mismatch than a person who is suddenly a villager again.
     */
    public static Optional<Look> forName(List<Look> all, Npc.Role role, String crew, String name, long seed) {
        Optional<Look> fitting = pick(all, role, crew, sexOf(name), seed);
        return fitting.isPresent() ? fitting : pick(all, role, crew, Sex.ANY, seed);
    }

    private static List<Look> forRole(List<Look> all, String role, Sex sex) {
        return all.stream().filter(l -> l.role().equals(role) && l.fits(sex)).toList();
    }

    /** Spreads nearby seeds apart: villager ids and world randoms both arrive as plain longs. */
    private static long mix(long h) {
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33;
        return h;
    }
}
