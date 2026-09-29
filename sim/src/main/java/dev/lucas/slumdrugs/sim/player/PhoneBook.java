package dev.lucas.slumdrugs.sim.player;

import dev.lucas.slumdrugs.sim.npc.Loyalty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * The burner phone's memory: the regulars who have the player's number, and what they have
 * asked for.
 *
 * <p>A customer the player has sold to keeps the number. Now and then one of them rings with an
 * order — their substance, a quantity, a time — and a delivery on time pays over a street sale
 * and raises their loyalty more; one missed is a regular let down. That turns the town from a
 * place to stand in into a round to run: the phone rings, the player has stock or does not, and
 * the clock is on.
 *
 * <p>Times are game ticks, twenty to the second, so the rules hold whatever the server's clock.
 */
public final class PhoneBook {

    /** A regular with the number. Position and loyalty are as they were at the last sale. */
    public record Contact(String id, String name, String substance, double loyalty, int x, int y, int z) {}

    /** What a regular asked for and by when. */
    public record Order(String contactId, String name, String substance, int units, long placed, long due,
                        int x, int y, int z) {
        public long ticksLeft(long now) { return Math.max(0, due - now); }
    }

    public enum Delivery { NO_ORDER, WRONG_GOODS, SHORT, DELIVERED }

    /** Numbers a phone keeps. Past this, the coldest regular without an order drops off. */
    public static final int MAX_CONTACTS = 12;

    /** Orders open at once. More than this and a player cannot do anything but run. */
    public static final int MAX_OPEN = 3;

    /** Ticks between the times the phone might ring: two minutes. */
    public static final long RING_EVERY = 20 * 120;

    /**
     * How far from a regular the player may be for them to ring. A phone reaches anywhere, but
     * an order the player cannot possibly get to in time is only a way to lose a customer.
     */
    public static final int RING_RANGE = 600;

    /** What a delivery pays on top of a street sale at the same loyalty. */
    public static final double PREMIUM = 1.4;

    /** Loyalty a delivery on time adds, on top of what the sale itself does. */
    public static final double ON_TIME = 6;

    /** Loyalty lost when an order runs out. Twice what a delivery earns: people remember. */
    public static final double STOOD_UP = -12;

    /**
     * Loyalty lost for saying no straight away. A third of letting them wait it out: a regular
     * would rather hear "not today" than stand in a doorway for ten minutes.
     */
    public static final double DECLINED = -4;

    /**
     * A handover by arrangement is quieter than hawking: the suspicion a delivery draws, as a
     * share of what the same units sold on the street would.
     */
    public static final double DISCRETION = 0.5;

    private final List<Contact> contacts = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    /** Loyalty owed to regulars who were let down while nobody was near them, by contact id. */
    private final Map<String, Double> owed = new HashMap<>();

    public PhoneBook() {}

    public PhoneBook(List<Contact> contacts, List<Order> orders, Map<String, Double> owed) {
        this.contacts.addAll(contacts);
        this.orders.addAll(orders);
        this.owed.putAll(owed);
    }

    public List<Contact> contacts() { return List.copyOf(contacts); }

    public List<Order> orders() { return List.copyOf(orders); }

    public Map<String, Double> owed() { return Map.copyOf(owed); }

    /**
     * Chance one regular rings when the phone might ring. None from a customer who is lost, one
     * in twelve from an indifferent one, one in four from somebody devoted.
     */
    public static double callChance(double loyalty) {
        if (Loyalty.lost(loyalty)) return 0;
        return 0.02 + 0.23 * Math.pow(Loyalty.clamp(loyalty) / 100.0, 1.5);
    }

    /** Units a regular asks for: two to eight, more from better friends. */
    public static int units(double loyalty, long seed) {
        int base = 2 + (int) (Loyalty.clamp(loyalty) / 100.0 * 5);
        return Math.min(8, base + new SplittableRandom(seed).nextInt(2));
    }

    /** Ticks a regular waits: six minutes, and a minute more for every unit. */
    public static long window(int units) {
        return 20L * 60 * (6 + units);
    }

    public static boolean inReach(long dx, long dz) {
        return dx * dx + dz * dz <= (long) RING_RANGE * RING_RANGE;
    }

    /**
     * Saves a regular, or refreshes one already saved. A full phone forgets the coldest regular
     * who has no order open; if every one of them has, the new number is not kept.
     *
     * @return true if the number is new
     */
    public boolean save(Contact contact) {
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).id().equals(contact.id())) {
                contacts.set(i, contact);
                return false;
            }
        }
        if (contacts.size() >= MAX_CONTACTS) {
            Optional<Contact> coldest = contacts.stream()
                    .filter(c -> orderFrom(c.id()).isEmpty())
                    .min(Comparator.comparingDouble(Contact::loyalty));
            if (coldest.isEmpty()) return false;
            contacts.remove(coldest.get());
            owed.remove(coldest.get().id());
        }
        contacts.add(contact);
        return true;
    }

    public Optional<Order> orderFrom(String contactId) {
        return orders.stream().filter(o -> o.contactId().equals(contactId)).findFirst();
    }

    /**
     * The phone might ring. Each regular in reach without an order open gets their chance, until
     * {@link #MAX_OPEN} orders are open. Returns the new orders.
     *
     * @param px where the player stands, for {@link #inReach}
     */
    public List<Order> ring(long now, long seed, int px, int pz) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Order> placed = new ArrayList<>();
        for (Contact c : contacts) {
            if (orders.size() >= MAX_OPEN) break;
            if (orderFrom(c.id()).isPresent()) continue;
            if (!inReach(c.x() - px, c.z() - pz)) continue;
            if (random.nextDouble() >= callChance(c.loyalty())) continue;
            call(c.id(), now, random.nextLong()).ifPresent(placed::add);
        }
        return placed;
    }

    /**
     * One regular rings now, whatever the odds — the ring above once the dice say so, and the
     * test command. Nothing if they are not saved, already waiting, or the phone is full.
     */
    public Optional<Order> call(String contactId, long now, long seed) {
        if (orders.size() >= MAX_OPEN || orderFrom(contactId).isPresent()) return Optional.empty();
        Optional<Contact> found = contacts.stream().filter(c -> c.id().equals(contactId)).findFirst();
        if (found.isEmpty()) return Optional.empty();
        Contact c = found.get();
        int units = units(c.loyalty(), seed);
        Order order = new Order(c.id(), c.name(), c.substance(), units, now, now + window(units), c.x(), c.y(), c.z());
        orders.add(order);
        return Optional.of(order);
    }

    /**
     * Closes the orders that ran out. Each costs the regular's loyalty, owed until the player
     * next meets them, and marks their saved loyalty down too so they ring less.
     */
    public List<Order> expire(long now) {
        List<Order> gone = new ArrayList<>();
        for (var it = orders.iterator(); it.hasNext(); ) {
            Order o = it.next();
            if (now < o.due()) continue;
            it.remove();
            gone.add(o);
            owed.merge(o.contactId(), STOOD_UP, Double::sum);
            for (int i = 0; i < contacts.size(); i++) {
                Contact c = contacts.get(i);
                if (c.id().equals(o.contactId()))
                    contacts.set(i, new Contact(c.id(), c.name(), c.substance(), Loyalty.clamp(c.loyalty() + STOOD_UP),
                            c.x(), c.y(), c.z()));
            }
        }
        return gone;
    }

    /**
     * The player hands a regular goods. Delivered only with an open order, the right substance
     * and at least the units asked for; the order closes then, and only then.
     */
    public Delivery deliver(String contactId, String substance, int units) {
        Optional<Order> order = orderFrom(contactId);
        if (order.isEmpty()) return Delivery.NO_ORDER;
        if (!order.get().substance().equals(substance)) return Delivery.WRONG_GOODS;
        if (units < order.get().units()) return Delivery.SHORT;
        orders.remove(order.get());
        return Delivery.DELIVERED;
    }

    /** The player turns an order down. Costs {@link #DECLINED}, owed like a missed one. */
    public boolean decline(String contactId) {
        Optional<Order> order = orderFrom(contactId);
        if (order.isEmpty()) return false;
        orders.remove(order.get());
        owed.merge(contactId, DECLINED, Double::sum);
        for (int i = 0; i < contacts.size(); i++) {
            Contact c = contacts.get(i);
            if (c.id().equals(contactId))
                contacts.set(i, new Contact(c.id(), c.name(), c.substance(), Loyalty.clamp(c.loyalty() + DECLINED),
                        c.x(), c.y(), c.z()));
        }
        return true;
    }

    /**
     * The player deletes a number. An order still open goes with it, as a refusal, and what the
     * regular is owed stays owed: deleting somebody does not make them forget.
     */
    public boolean forget(String contactId) {
        decline(contactId);
        return contacts.removeIf(c -> c.id().equals(contactId));
    }

    /** Loyalty owed to a regular from orders missed while they were out of sight; cleared on reading. */
    public double settleOwed(String contactId) {
        Double due = owed.remove(contactId);
        return due == null ? 0 : due;
    }
}
