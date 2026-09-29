package dev.lucas.slumdrugs.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lucas.slumdrugs.mod.PhoneNet;
import dev.lucas.slumdrugs.mod.StreetSales;
import dev.lucas.slumdrugs.sim.world.TownSites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The burner phone, held up to the face: a green monochrome display and a keypad.
 *
 * <p>It is a burner, not a smartphone (MOD-GDD.md §1), so it does three things — the orders
 * regulars have rung in, the contacts that have the number, and finding the nearest town — and
 * it works the way such a phone does: up and down to choose, OK to open, back to leave. Arrow
 * keys, Enter and Backspace do the same from the keyboard, and everything can be clicked.
 *
 * <p>It shows what the server sent ({@link PhoneNet.State}) and asks it to act; it decides
 * nothing itself. While it is open it asks again every few seconds, so an order rung in or run
 * out shows without closing it.
 */
public final class PhoneScreen extends Screen {

    // The phone body, in GUI pixels.
    private static final int BODY_W = 132;
    private static final int BODY_H = 246;
    private static final int LCD_W = 108;
    private static final int LCD_H = 112;
    private static final int ROW_H = 10;
    private static final int LIST_TOP = 26;
    private static final int VISIBLE_ROWS = 7;

    // A four-green display, as the cheap handsets had.
    private static final int LCD_BG = 0xFF8BAC0F;
    private static final int LCD_INK = 0xFF0F380F;
    private static final int LCD_MID = 0xFF306230;
    private static final int LCD_LIT = 0xFF9BBC0F;
    private static final int CASE = 0xFF1B1E23;
    private static final int CASE_EDGE = 0xFF343A43;
    private static final int BEZEL = 0xFF0B0C0E;
    private static final int KEY = 0xFF2A2F37;
    private static final int KEY_HOVER = 0xFF3D4550;
    private static final int KEY_TEXT = 0xFFB9C2CC;

    /** Ticks between the screen asking the server what has changed. */
    private static final int REFRESH_TICKS = 100;

    private enum Page { HOME, ORDERS, ORDER, CONTACTS, CONTACT, TOWN }

    /** A line on the display: text, maybe a figure at the right, maybe something it does. */
    private record Row(String text, String right, Runnable action) {
        Row(String text) { this(text, "", null); }
        boolean selectable() { return action != null; }
    }

    /** A rectangle that does something when clicked. */
    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
        boolean contains(double x, double y) { return x >= x0 && x < x1 && y >= y0 && y < y1; }
    }

    private PhoneNet.State state;
    private long receivedAt;
    private Page page = Page.HOME;
    private String focus = "";
    private int cursor;
    private int scroll;
    private int sinceRefresh;
    private boolean confirmDelete;
    private boolean searching;
    private final List<Hit> hits = new ArrayList<>();

    private int left;
    private int top;
    /** On a short window the number keys go, so the display and the keys that work still fit. */
    private boolean compact;

    public PhoneScreen(PhoneNet.State state) {
        super(Component.translatable("item.slumdrugs.burner_phone"));
        accept(state);
    }

    /** Where the network hands a new state: opens the phone, or refreshes it if it is open. */
    public static void receive(PhoneNet.State state) {
        Minecraft minecraft = Minecraft.getInstance();
        PhoneNav.prune(state);
        if (minecraft.gui.screen() instanceof PhoneScreen open) open.accept(state);
        else if (state.open()) minecraft.gui.setScreen(new PhoneScreen(state));
    }

    private void accept(PhoneNet.State next) {
        state = next;
        receivedAt = gameTime();
        searching = false;
        // A detail page whose order or contact has gone falls back to its list.
        if (page == Page.ORDER && order(focus) == null) open(Page.ORDERS);
        if (page == Page.CONTACT && contact(focus) == null) open(Page.CONTACTS);
        settleCursor(0);
    }

    @Override
    protected void init() {
        compact = height < BODY_H + 12;
        left = (width - BODY_W) / 2;
        top = Math.max(12, (height - bodyHeight()) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        // Orders run on the clock; the phone does not stop it.
        return false;
    }

    @Override
    public void tick() {
        if (++sinceRefresh >= REFRESH_TICKS) {
            sinceRefresh = 0;
            send(PhoneNet.Verb.REFRESH, "");
        }
    }

    // ------------------------------------------------------------------ what each page shows

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        switch (page) {
            case HOME -> {
                rows.add(new Row(tr("phone.slumdrugs.menu.orders"), String.valueOf(state.orders().size()), () -> open(Page.ORDERS)));
                rows.add(new Row(tr("phone.slumdrugs.menu.contacts"), String.valueOf(state.contacts().size()), () -> open(Page.CONTACTS)));
                rows.add(new Row(tr("phone.slumdrugs.menu.town"), "", this::locate));
                if (PhoneNav.active()) rows.add(new Row(tr("phone.slumdrugs.menu.nav_off"), "", () -> {
                    PhoneNav.clear();
                    settleCursor(0);
                }));
            }
            case ORDERS -> {
                if (state.orders().isEmpty()) rows.add(new Row(tr("phone.slumdrugs.screen.no_orders")));
                for (PhoneNet.OrderRow o : state.orders())
                    rows.add(new Row(o.name() + " " + o.units() + "×" + substance(o.substance()),
                            minutesLeft(o) + "'", () -> { focus = o.id(); open(Page.ORDER); }));
            }
            case ORDER -> {
                PhoneNet.OrderRow o = order(focus);
                if (o == null) break;
                rows.add(new Row(o.name()));
                rows.add(new Row(o.units() + " × " + substance(o.substance())));
                rows.add(new Row(tr("phone.slumdrugs.screen.left", minutesLeft(o))));
                rows.add(new Row(where(o.x(), o.z())));
                rows.add(new Row(o.x() + " " + o.y() + " " + o.z()));
                rows.add(new Row(tr(PhoneNav.headingTo(o.id()) ? "phone.slumdrugs.screen.navigating" : "phone.slumdrugs.screen.navigate"),
                        "", () -> {
                            // Navigation is for walking: the phone goes back in the pocket.
                            PhoneNav.to(o.id(), o.name(), o.x(), o.z());
                            onClose();
                        }));
                rows.add(new Row(tr("phone.slumdrugs.screen.decline"), "", () -> {
                    send(PhoneNet.Verb.DECLINE, o.id());
                    open(Page.ORDERS);
                }));
            }
            case CONTACTS -> {
                if (state.contacts().isEmpty()) rows.add(new Row(tr("phone.slumdrugs.screen.no_contacts")));
                for (PhoneNet.ContactRow c : state.contacts())
                    rows.add(new Row((c.waiting() ? "! " : "") + c.name(), substance(c.substance()),
                            () -> { focus = c.id(); confirmDelete = false; open(Page.CONTACT); }));
            }
            case CONTACT -> {
                PhoneNet.ContactRow c = contact(focus);
                if (c == null) break;
                rows.add(new Row(c.name()));
                rows.add(new Row(substance(c.substance())));
                rows.add(new Row(Component.translatable(StreetSales.loyaltyWord(c.loyalty())).getString()));
                if (c.waiting()) rows.add(new Row(tr("phone.slumdrugs.screen.waiting")));
                rows.add(new Row(tr(confirmDelete ? "phone.slumdrugs.screen.delete_sure" : "phone.slumdrugs.screen.delete"), "", () -> {
                    if (!confirmDelete) {
                        confirmDelete = true;
                        return;
                    }
                    send(PhoneNet.Verb.FORGET, c.id());
                    open(Page.CONTACTS);
                }));
            }
            case TOWN -> {
                PhoneNet.TownStatus status = searching ? PhoneNet.TownStatus.NOT_ASKED : state.town();
                switch (status) {
                    case NOT_ASKED -> rows.add(new Row(tr("phone.slumdrugs.screen.searching")));
                    case NONE -> {
                        rows.add(new Row(tr("phone.slumdrugs.screen.no_signal")));
                        rows.add(new Row(tr("phone.slumdrugs.screen.no_town")));
                    }
                    case NO_NETWORK -> rows.add(new Row(tr("phone.slumdrugs.screen.no_network")));
                    case FOUND -> {
                        rows.add(new Row(tr("phone.slumdrugs.screen.town")));
                        rows.add(new Row(where(state.townX(), state.townZ())));
                        rows.add(new Row("x " + state.townX() + "  z " + state.townZ()));
                        rows.add(new Row(tr(PhoneNav.headingTo("") ? "phone.slumdrugs.screen.navigating" : "phone.slumdrugs.screen.navigate"),
                                "", () -> {
                                    PhoneNav.to("", tr("phone.slumdrugs.screen.town"), state.townX(), state.townZ());
                                    onClose();
                                }));
                    }
                }
                if (!searching) rows.add(new Row(tr("phone.slumdrugs.screen.search_again"), "", this::locate));
            }
        }
        return rows;
    }

    private String title() {
        return switch (page) {
            case HOME -> tr("phone.slumdrugs.menu.title");
            case ORDERS, ORDER -> tr("phone.slumdrugs.menu.orders");
            case CONTACTS, CONTACT -> tr("phone.slumdrugs.menu.contacts");
            case TOWN -> tr("phone.slumdrugs.menu.town");
        };
    }

    // ------------------------------------------------------------------ doing things

    private void open(Page next) {
        page = next;
        cursor = 0;
        scroll = 0;
        settleCursor(0);
    }

    private void back() {
        switch (page) {
            case HOME -> onClose();
            case ORDER -> open(Page.ORDERS);
            case CONTACT -> open(Page.CONTACTS);
            default -> open(Page.HOME);
        }
    }

    private void locate() {
        searching = true;
        open(Page.TOWN);
        send(PhoneNet.Verb.LOCATE, "");
    }

    private void activate() {
        List<Row> rows = rows();
        if (cursor >= 0 && cursor < rows.size() && rows.get(cursor).selectable()) rows.get(cursor).action().run();
    }

    /** Moves the cursor to the next row that does something, in the given direction. */
    private void settleCursor(int step) {
        List<Row> rows = rows();
        if (rows.isEmpty()) {
            cursor = 0;
            return;
        }
        int at = Math.max(0, Math.min(rows.size() - 1, cursor + step));
        int dir = step < 0 ? -1 : 1;
        int probe = at;
        while (probe >= 0 && probe < rows.size() && !rows.get(probe).selectable()) probe += dir;
        if (probe < 0 || probe >= rows.size()) {
            // Nothing that way: search the other way from where we were.
            probe = at;
            while (probe >= 0 && probe < rows.size() && !rows.get(probe).selectable()) probe -= dir;
        }
        cursor = probe >= 0 && probe < rows.size() ? probe : at;
        if (cursor < scroll) scroll = cursor;
        if (cursor >= scroll + VISIBLE_ROWS) scroll = cursor - VISIBLE_ROWS + 1;
        // Info rows above the first action stay in view when the page opens.
        if (step == 0 && cursor < VISIBLE_ROWS) scroll = 0;
    }

    private void send(PhoneNet.Verb verb, String contactId) {
        ClientPacketDistributor.sendToServer(new PhoneNet.Action(verb, contactId));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.25f));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isUp()) { click(); settleCursor(-1); return true; }
        if (event.isDown()) { click(); settleCursor(1); return true; }
        if (event.isConfirmation() || event.isRight()) { click(); activate(); return true; }
        if (event.isLeft() || event.key() == InputConstants.KEY_BACKSPACE) { click(); back(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Hit hit : List.copyOf(hits)) {
            if (hit.contains(event.x(), event.y())) {
                click();
                hit.action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        settleCursor(scrollY > 0 ? -1 : 1);
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        hits.clear();
        int x = left, y = top;

        // The handset: antenna stub, body, speaker slit, the bezel round the display.
        g.fill(x + BODY_W - 28, y - 10, x + BODY_W - 16, y + 2, CASE);
        g.fill(x, y, x + BODY_W, y + bodyHeight(), CASE);
        g.outline(x, y, BODY_W, bodyHeight(), CASE_EDGE);
        g.fill(x + BODY_W / 2 - 14, y + 7, x + BODY_W / 2 + 14, y + 9, BEZEL);
        g.fill(x + 8, y + 14, x + BODY_W - 8, y + 18 + LCD_H + 4, BEZEL);

        int lx = x + 12, ly = y + 18;
        g.fill(lx, ly, lx + LCD_W, ly + LCD_H, LCD_BG);
        drawStatusBar(g, lx, ly);

        String title = title();
        g.text(font, title, lx + (LCD_W - font.width(title)) / 2, ly + 13, LCD_INK, false);
        g.horizontalLine(lx + 2, lx + LCD_W - 3, ly + 23, LCD_MID);

        List<Row> rows = rows();
        for (int i = scroll; i < Math.min(rows.size(), scroll + VISIBLE_ROWS); i++) {
            Row row = rows.get(i);
            int ry = ly + LIST_TOP + (i - scroll) * ROW_H;
            boolean selected = i == cursor && row.selectable();
            if (selected) g.fill(lx + 1, ry - 1, lx + LCD_W - 1, ry + ROW_H - 1, LCD_MID);
            int ink = selected ? LCD_LIT : row.selectable() ? LCD_INK : LCD_MID;
            int rightWidth = row.right().isEmpty() ? 0 : font.width(row.right()) + 4;
            String text = font.plainSubstrByWidth(row.text(), LCD_W - 6 - rightWidth);
            g.text(font, text, lx + 3, ry, ink, false);
            if (!row.right().isEmpty()) g.text(font, row.right(), lx + LCD_W - 3 - font.width(row.right()), ry, ink, false);
            if (row.selectable()) {
                int index = i;
                hits.add(new Hit(lx, ry - 1, lx + LCD_W, ry + ROW_H - 1, () -> {
                    cursor = index;
                    activate();
                }));
            }
        }
        if (scroll > 0) g.text(font, "▲", lx + LCD_W - 8, ly + LIST_TOP - 9, LCD_MID, false);
        if (scroll + VISIBLE_ROWS < rows.size()) g.text(font, "▼", lx + LCD_W - 8, ly + LCD_H - 20, LCD_MID, false);

        // Soft-key labels along the bottom of the display.
        g.horizontalLine(lx + 2, lx + LCD_W - 3, ly + LCD_H - 12, LCD_MID);
        String ok = tr("phone.slumdrugs.key.select");
        String back = tr(page == Page.HOME ? "phone.slumdrugs.key.close" : "phone.slumdrugs.key.back");
        g.text(font, ok, lx + 3, ly + LCD_H - 10, LCD_INK, false);
        g.text(font, back, lx + LCD_W - 3 - font.width(back), ly + LCD_H - 10, LCD_INK, false);

        drawKeypad(g, x, ly + LCD_H + 12, mouseX, mouseY);
    }

    private void drawStatusBar(GuiGraphicsExtractor g, int lx, int ly) {
        // Signal: four bars, all of them, because the network is the one thing that works.
        for (int i = 0; i < 4; i++) g.fill(lx + 3 + i * 3, ly + 8 - i * 2, lx + 5 + i * 3, ly + 9, LCD_INK);
        String clock = clock();
        g.text(font, clock, lx + LCD_W - 3 - font.width(clock), ly + 2, LCD_INK, false);
        if (!state.orders().isEmpty()) g.text(font, "!" + state.orders().size(), lx + 20, ly + 2, LCD_INK, false);
    }

    private void drawKeypad(GuiGraphicsExtractor g, int x, int ky, int mouseX, int mouseY) {
        // Soft keys under the display's labels.
        key(g, x + 12, ky, 30, 11, "—", mouseX, mouseY, this::activate);
        key(g, x + BODY_W - 42, ky, 30, 11, "—", mouseX, mouseY, this::back);

        // The navigation pad.
        int cx = x + BODY_W / 2, cy = ky + 18;
        key(g, cx - 8, cy - 17, 16, 10, "▲", mouseX, mouseY, () -> settleCursor(-1));
        key(g, cx - 8, cy + 7, 16, 10, "▼", mouseX, mouseY, () -> settleCursor(1));
        key(g, cx - 24, cy - 6, 13, 12, "◀", mouseX, mouseY, this::back);
        key(g, cx + 11, cy - 6, 13, 12, "▶", mouseX, mouseY, this::activate);
        key(g, cx - 9, cy - 6, 18, 12, "OK", mouseX, mouseY, this::activate);

        // The number keys: there to be a phone, not to be pressed.
        if (compact) return;
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        int gx = x + 16, gy = ky + 36;
        for (int i = 0; i < labels.length; i++) {
            int kx = gx + (i % 3) * 34, kyy = gy + (i / 3) * 15;
            key(g, kx, kyy, 30, 12, labels[i], mouseX, mouseY, null);
        }
    }

    private void key(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mouseX, int mouseY, Runnable action) {
        boolean over = action != null && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, over ? KEY_HOVER : KEY);
        g.horizontalLine(x, x + w - 1, y, CASE_EDGE);
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, KEY_TEXT, false);
        if (action != null) hits.add(new Hit(x, y, x + w, y + h, action));
    }

    // ------------------------------------------------------------------ helpers

    private int bodyHeight() {
        return compact ? BODY_H - 62 : BODY_H;
    }

    private PhoneNet.OrderRow order(String id) {
        return state.orders().stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
    }

    private PhoneNet.ContactRow contact(String id) {
        return state.contacts().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    private long gameTime() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime()
                : Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
    }

    private int minutesLeft(PhoneNet.OrderRow o) {
        long left = Math.max(0, o.ticksLeft() - (gameTime() - receivedAt));
        return (int) Math.max(0, Math.ceil(left / 1200.0));
    }

    private String where(int tx, int tz) {
        var player = minecraft.player;
        if (player == null) return "";
        long dx = tx - player.getBlockX(), dz = tz - player.getBlockZ();
        long metres = Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        String bearing = TownSites.bearing(dx, dz).name().toLowerCase(Locale.ROOT);
        String distance = metres >= 1000 ? String.format(Locale.ROOT, "%.1f km", metres / 1000.0) : metres + " m";
        return distance + " " + tr("phone.slumdrugs.bearing." + bearing);
    }

    private String clock() {
        if (minecraft.level == null) return "--:--";
        long day = Math.floorMod(minecraft.level.getOverworldClockTime() + 6000, 24000L);
        return String.format(Locale.ROOT, "%02d:%02d", day / 1000, day % 1000 * 60 / 1000);
    }

    private static String substance(String drug) {
        return tr("item.slumdrugs.product_" + drug);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
