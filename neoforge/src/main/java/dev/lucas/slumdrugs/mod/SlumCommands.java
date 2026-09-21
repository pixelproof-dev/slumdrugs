package dev.lucas.slumdrugs.mod;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.lucas.slumdrugs.sim.drug.Cultivation;
import dev.lucas.slumdrugs.sim.drug.Refining;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.player.Condition;
import dev.lucas.slumdrugs.sim.player.Progression;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;

/**
 * Everything the mod can do from a chat prompt. This exists as much for testing as for admins:
 * until a client has been launched, commands are the only way to exercise growth, condition,
 * refining and NPCs on a running server.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class SlumCommands {

    /**
     * 26.3 replaced integer permission levels with named permissions; gamemaster is the
     * closest equivalent to the old level 2 these commands used to want.
     */
    private static final PermissionCheck OP = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);
    private static final int DEFAULT_RADIUS = 8;

    private SlumCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("slum")
                .then(version())
                .then(give())
                .then(condition())
                .then(npc())
                .then(frame())
                .then(refine())
                .then(structure())
                .then(progress());
        event.getDispatcher().register(root);
        event.getDispatcher().register(Commands.literal("slumdrugs").redirect(event.getDispatcher().register(root)));
    }

    // ------------------------------------------------------------------ version

    private static LiteralArgumentBuilder<CommandSourceStack> version() {
        return Commands.literal("version").executes(ctx -> {
            reply(ctx, "SlumDrugs " + SlumDrugsMod.ID + " — " + ModItems.all().size()
                    + " items, " + ModItems.SUBSTANCES.size() + " substances");
            return 1;
        });
    }

    // ------------------------------------------------------------------ give

    private static LiteralArgumentBuilder<CommandSourceStack> give() {
        return Commands.literal("give").requires(Commands.hasPermission(OP))
                .then(Commands.argument("item", StringArgumentType.word())
                        .suggests((ctx, builder) ->
                                SharedSuggestionProvider.suggest(ModItems.all().keySet(), builder))
                        .executes(ctx -> giveItem(ctx, 1, 50))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> giveItem(ctx, IntegerArgumentType.getInteger(ctx, "count"), 50))
                                .then(Commands.argument("quality", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> giveItem(ctx,
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                IntegerArgumentType.getInteger(ctx, "quality"))))));
    }

    private static int giveItem(CommandContext<CommandSourceStack> ctx, int count, int quality) {
        String name = StringArgumentType.getString(ctx, "item");
        if (!ModItems.all().containsKey(name)) {
            ctx.getSource().sendFailure(Component.literal("No such item: " + name));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("This one needs a player"));
            return 0;
        }
        ItemStack stack = ModComponents.withQuality(
                new ItemStack(ModItems.get(name).get(), count), quality, player.getName().getString());
        if (!player.getInventory().add(stack))
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        reply(ctx, "Gave " + count + " x " + name + " at quality " + quality);
        return count;
    }

    // ------------------------------------------------------------------ condition

    private static LiteralArgumentBuilder<CommandSourceStack> condition() {
        return Commands.literal("condition")
                .then(Commands.literal("get")
                        .executes(ctx -> conditionGet(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(Commands.hasPermission(OP))
                                .executes(ctx -> conditionGet(ctx, EntityArgument.getPlayers(ctx, "targets")))))
                .then(conditionWrite("set", false))
                .then(conditionWrite("add", true))
                .then(Commands.literal("clear").requires(Commands.hasPermission(OP))
                        .executes(ctx -> conditionClear(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> conditionClear(ctx, EntityArgument.getPlayers(ctx, "targets")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> conditionWrite(String verb, boolean relative) {
        return Commands.literal(verb).requires(Commands.hasPermission(OP))
                .then(Commands.argument("field", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                List.of("intoxication", "tolerance", "dependence"), builder))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(relative ? -100 : 0, 100))
                                .executes(ctx -> conditionWrite(ctx,
                                        List.of(ctx.getSource().getPlayerOrException()), relative))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> conditionWrite(ctx,
                                                EntityArgument.getPlayers(ctx, "targets"), relative)))));
    }

    private static int conditionGet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        var settings = Condition.Settings.defaults();
        for (ServerPlayer player : targets) {
            Condition c = player.getData(ModAttachments.CONDITION.get());
            long now = player.level().getGameTime() * 50L;
            reply(ctx, String.format("%s — intoxication %.0f, tolerance %.0f, dependence %.0f%s",
                    player.getName().getString(), c.intoxication, c.tolerance, c.dependence,
                    c.withdrawalSeverity(now, settings) > 0
                            ? " (withdrawal " + c.withdrawalSeverity(now, settings) + ")"
                            : c.craving(now, settings) ? " (craving)" : ""));
        }
        return targets.size();
    }

    private static int conditionWrite(CommandContext<CommandSourceStack> ctx,
                                      Collection<ServerPlayer> targets, boolean relative) {
        String field = StringArgumentType.getString(ctx, "field");
        double value = DoubleArgumentType.getDouble(ctx, "value");
        for (ServerPlayer player : targets) {
            Condition c = player.getData(ModAttachments.CONDITION.get());
            switch (field) {
                case "intoxication" -> c.intoxication = clamp(relative ? c.intoxication + value : value);
                case "tolerance" -> c.tolerance = clamp(relative ? c.tolerance + value : value);
                case "dependence" -> c.dependence = clamp(relative ? c.dependence + value : value);
                default -> {
                    ctx.getSource().sendFailure(Component.literal("No such field: " + field));
                    return 0;
                }
            }
            player.syncData(ModAttachments.CONDITION.get());
        }
        reply(ctx, "Set " + field + " on " + targets.size() + " player(s)");
        return targets.size();
    }

    private static int conditionClear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            Condition c = player.getData(ModAttachments.CONDITION.get());
            c.intoxication = 0;
            c.tolerance = 0;
            c.dependence = 0;
            player.syncData(ModAttachments.CONDITION.get());
        }
        reply(ctx, "Cleared " + targets.size() + " player(s)");
        return targets.size();
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    // ------------------------------------------------------------------ npc

    private static LiteralArgumentBuilder<CommandSourceStack> npc() {
        return Commands.literal("npc").requires(Commands.hasPermission(OP))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(Npc.Role.values()).map(Enum::name).toList(), builder))
                                .executes(ctx -> spawn(ctx, "", null))
                                .then(Commands.argument("crew", StringArgumentType.word())
                                        .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "crew"), null))
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(ctx -> spawn(ctx,
                                                        StringArgumentType.getString(ctx, "crew"),
                                                        StringArgumentType.getString(ctx, "name")))))))
                .then(Commands.literal("list")
                        .executes(ctx -> listNpcs(ctx, 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> listNpcs(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("aggression")
                        .then(aggressionVerb("set"))
                        .then(aggressionVerb("provoke"))
                        .then(aggressionVerb("appease")))
                .then(Commands.literal("remove")
                        .executes(ctx -> removeNpcs(ctx, DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> removeNpcs(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> aggressionVerb(String verb) {
        return Commands.literal(verb)
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 100))
                        .executes(ctx -> aggression(ctx, verb, DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> aggression(ctx, verb,
                                        IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String crew, String name) {
        Npc.Role role;
        try {
            role = Npc.Role.valueOf(StringArgumentType.getString(ctx, "role").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            ctx.getSource().sendFailure(Component.literal("No such role"));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        String chosen = name != null ? name : Npcs.randomName(level);

        Villager villager = Npcs.spawn(level, pos, role, crew, chosen);
        if (villager == null) {
            ctx.getSource().sendFailure(Component.literal("Could not spawn"));
            return 0;
        }
        reply(ctx, "Spawned " + chosen + " (" + role.name().toLowerCase(java.util.Locale.ROOT)
                + (crew.isEmpty() ? "" : ", " + crew) + ")");
        return 1;
    }

    private static List<Villager> nearby(CommandSourceStack source, int radius) {
        AABB box = AABB.ofSize(source.getPosition(), radius * 2.0, radius * 2.0, radius * 2.0);
        return source.getLevel().getEntitiesOfClass(Villager.class, box, Npcs::isOurs);
    }

    private static int listNpcs(CommandContext<CommandSourceStack> ctx, int radius) {
        List<Villager> found = nearby(ctx.getSource(), radius);
        if (found.isEmpty()) {
            reply(ctx, "No NPCs of ours within " + radius + " blocks");
            return 0;
        }
        for (Villager villager : found) {
            NpcData data = Npcs.data(villager);
            reply(ctx, String.format("%s — %s%s, aggression %.0f (%s)",
                    villager.getName().getString(),
                    data.role().name().toLowerCase(java.util.Locale.ROOT),
                    data.crew().isEmpty() ? "" : "/" + data.crew(),
                    data.aggression(), data.stance().name().toLowerCase(java.util.Locale.ROOT)));
        }
        return found.size();
    }

    private static int aggression(CommandContext<CommandSourceStack> ctx, String verb, int radius) {
        double value = DoubleArgumentType.getDouble(ctx, "value");
        List<Villager> found = nearby(ctx.getSource(), radius);
        for (Villager villager : found) {
            NpcData data = Npcs.data(villager);
            double updated = switch (verb) {
                case "provoke" -> Npc.provoke(data.aggression(), value);
                case "appease" -> Npc.appease(data.aggression(), value);
                default -> Npc.clamp(value);
            };
            villager.setData(ModAttachments.NPC.get(), data.withAggression(updated));
        }
        reply(ctx, verb + " applied to " + found.size() + " NPC(s)");
        return found.size();
    }

    private static int removeNpcs(CommandContext<CommandSourceStack> ctx, int radius) {
        List<Villager> found = nearby(ctx.getSource(), radius);
        found.forEach(villager -> villager.discard());
        reply(ctx, "Removed " + found.size() + " NPC(s)");
        return found.size();
    }

    // ------------------------------------------------------------------ frame

    private static LiteralArgumentBuilder<CommandSourceStack> frame() {
        return Commands.literal("frame").requires(Commands.hasPermission(OP))
                .then(Commands.literal("info").executes(ctx -> frameInfo(ctx, DEFAULT_RADIUS)))
                .then(Commands.literal("grow").executes(ctx -> frameGrow(ctx, DEFAULT_RADIUS)))
                .then(Commands.literal("water")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 100000))
                                .executes(ctx -> frameWater(ctx, DEFAULT_RADIUS,
                                        IntegerArgumentType.getInteger(ctx, "seconds")))));
    }

    /** The nearest forcing frame to the source, or null. */
    private static BlockPos nearestFrame(CommandSourceStack source, int radius) {
        BlockPos origin = BlockPos.containing(source.getPosition());
        ServerLevel level = source.getLevel();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (!(level.getBlockEntity(pos) instanceof ForcingFrameBlockEntity)) continue;
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    private static int frameInfo(CommandContext<CommandSourceStack> ctx, int radius) {
        BlockPos pos = nearestFrame(ctx.getSource(), radius);
        if (pos == null) {
            ctx.getSource().sendFailure(Component.literal("No forcing frame within " + radius + " blocks"));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        var frame = (ForcingFrameBlockEntity) level.getBlockEntity(pos);
        var state = frame.state();
        if (state.drug == null) {
            reply(ctx, "Frame at " + pos.toShortString() + " — empty, soil "
                    + frame.soil(level, pos).name().toLowerCase(java.util.Locale.ROOT));
            return 1;
        }
        Cultivation.Inputs inputs = frame.inputs(level, pos);
        reply(ctx, String.format("Frame at %s — %s, %.0f%% grown, %.0fs water, soil %s, compost %d",
                pos.toShortString(), state.drug, state.progress * 100, state.waterSeconds,
                inputs.soil().name().toLowerCase(java.util.Locale.ROOT), frame.fertiliserCharges()));
        reply(ctx, String.format("  would yield %d units at quality %d",
                Cultivation.units(ForcingFrameBlockEntity.BASE_YIELD, inputs, Cultivation.quality(inputs)),
                Cultivation.quality(inputs)));
        return 1;
    }

    private static int frameGrow(CommandContext<CommandSourceStack> ctx, int radius) {
        BlockPos pos = nearestFrame(ctx.getSource(), radius);
        if (pos == null) {
            ctx.getSource().sendFailure(Component.literal("No forcing frame within " + radius + " blocks"));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        var frame = (ForcingFrameBlockEntity) level.getBlockEntity(pos);
        if (frame.state().drug == null) {
            ctx.getSource().sendFailure(Component.literal("That frame is empty"));
            return 0;
        }
        frame.state().progress = 1;
        frame.serverTick(level, pos, level.getBlockState(pos));
        reply(ctx, "Ripened the frame at " + pos.toShortString());
        return 1;
    }

    private static int frameWater(CommandContext<CommandSourceStack> ctx, int radius, int seconds) {
        BlockPos pos = nearestFrame(ctx.getSource(), radius);
        if (pos == null) {
            ctx.getSource().sendFailure(Component.literal("No forcing frame within " + radius + " blocks"));
            return 0;
        }
        var frame = (ForcingFrameBlockEntity) ctx.getSource().getLevel().getBlockEntity(pos);
        frame.state().waterSeconds = seconds;
        reply(ctx, "Set water to " + seconds + "s at " + pos.toShortString());
        return 1;
    }

    // ------------------------------------------------------------------ structure

    private static LiteralArgumentBuilder<CommandSourceStack> structure() {
        return Commands.literal("structure").requires(Commands.hasPermission(OP))
                .then(Commands.literal("place")
                        .then(Commands.argument("piece", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(List.of("trader_house"), builder))
                                .executes(ctx -> placeStructure(ctx, net.minecraft.world.level.block.Rotation.NONE))
                                .then(Commands.argument("rotation", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(net.minecraft.world.level.block.Rotation.values())
                                                        .map(Enum::name).toList(), builder))
                                        .executes(ctx -> placeStructure(ctx, rotation(ctx))))));
    }

    private static net.minecraft.world.level.block.Rotation rotation(CommandContext<CommandSourceStack> ctx) {
        try {
            return net.minecraft.world.level.block.Rotation.valueOf(
                    StringArgumentType.getString(ctx, "rotation").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return net.minecraft.world.level.block.Rotation.NONE;
        }
    }

    private static int placeStructure(CommandContext<CommandSourceStack> ctx,
                                      net.minecraft.world.level.block.Rotation rotation) {
        String name = StringArgumentType.getString(ctx, "piece");
        if (!name.equals("trader_house")) {
            ctx.getSource().sendFailure(Component.literal("No such piece: " + name));
            return 0;
        }
        var result = StructurePlacer.place(ctx.getSource().getLevel(),
                BlockPos.containing(ctx.getSource().getPosition()),
                StructurePlacer.TRADER_HOUSE, rotation);

        if (result instanceof StructurePlacer.Result.Placed placed) {
            reply(ctx, "Placed " + name + " at " + placed.origin().toShortString()
                    + " (" + placed.size().getX() + "x" + placed.size().getY() + "x" + placed.size().getZ() + ")");
            return 1;
        }
        if (result instanceof StructurePlacer.Result.Missing missing) {
            ctx.getSource().sendFailure(Component.literal(
                    "No structure file for " + missing.id() + " — drop the .nbt into "
                            + "data/slumdrugs/structure/ and reload"));
            return 0;
        }
        ctx.getSource().sendFailure(Component.literal(
                ((StructurePlacer.Result.Refused) result).reason()));
        return 0;
    }

    // ------------------------------------------------------------------ refine

    /** A dry run against the rules, so the numbers can be checked without building anything. */
    private static LiteralArgumentBuilder<CommandSourceStack> refine() {
        return Commands.literal("refine").requires(Commands.hasPermission(OP))
                .then(Commands.argument("method", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(Refining.Method.values()).map(Enum::name).toList(), builder))
                        .then(Commands.argument("quality", IntegerArgumentType.integer(0, 100))
                                .then(Commands.argument("units", IntegerArgumentType.integer(1, 64))
                                        .executes(SlumCommands::refineDryRun))));
    }

    private static int refineDryRun(CommandContext<CommandSourceStack> ctx) {
        Refining.Method method;
        try {
            method = Refining.Method.valueOf(
                    StringArgumentType.getString(ctx, "method").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            ctx.getSource().sendFailure(Component.literal("No such method"));
            return 0;
        }
        int quality = IntegerArgumentType.getInteger(ctx, "quality");
        int units = IntegerArgumentType.getInteger(ctx, "units");
        var result = Refining.run(method, new Refining.Batch(quality, units),
                CentrifugeBlockEntity.REAGENT_QUALITY, 0);
        reply(ctx, String.format("%s: %d units at %d -> %d units at %d (%d waste, compost %d, %ds)",
                method.name().toLowerCase(java.util.Locale.ROOT), units, quality,
                result.units(), result.quality(), result.wasteUnits(), result.compostQuality(),
                method.seconds(false)));
        return 1;
    }

    // ------------------------------------------------------------------ progress

    /** Where a player stands on the ladder, and a way to move them for testing. */
    private static LiteralArgumentBuilder<CommandSourceStack> progress() {
        return Commands.literal("progress")
                .then(Commands.literal("get")
                        .executes(ctx -> progressGet(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(Commands.hasPermission(OP))
                                .executes(ctx -> progressGet(ctx, EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("set").requires(Commands.hasPermission(OP))
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("units", "coin"), builder))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> progressSet(ctx, List.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> progressSet(ctx, EntityArgument.getPlayers(ctx, "targets")))))))
                .then(Commands.literal("reset").requires(Commands.hasPermission(OP))
                        .executes(ctx -> progressReset(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> progressReset(ctx, EntityArgument.getPlayers(ctx, "targets")))));
    }

    private static int progressGet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            Progression p = player.getData(ModAttachments.PROGRESSION.get());
            var gate = p.gate();
            String next = !gate.reachable() ? "the ladder ends here for now"
                    : gate.unitsNeeded() > 0 ? gate.unitsNeeded() + " more units to " + gate.next().label
                    : gate.coinNeeded() + " more coin to " + gate.next().label;
            reply(ctx, String.format("%s — %s: %d units sold, %d coin earned; %s",
                    player.getName().getString(), p.tier().label, p.unitsSold, p.coinEarned, next));
        }
        return targets.size();
    }

    private static int progressSet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        String field = StringArgumentType.getString(ctx, "field");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        if (!field.equals("units") && !field.equals("coin")) {
            ctx.getSource().sendFailure(Component.literal("No such field: " + field));
            return 0;
        }
        for (ServerPlayer player : targets) {
            Progression p = player.getData(ModAttachments.PROGRESSION.get());
            if (field.equals("units")) p.unitsSold = value; else p.coinEarned = value;
            player.syncData(ModAttachments.PROGRESSION.get());
        }
        reply(ctx, "Set " + field + " to " + value + " on " + targets.size() + " player(s)");
        return targets.size();
    }

    private static int progressReset(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            Progression p = player.getData(ModAttachments.PROGRESSION.get());
            p.unitsSold = 0;
            p.coinEarned = 0;
            player.syncData(ModAttachments.PROGRESSION.get());
        }
        reply(ctx, "Reset " + targets.size() + " player(s) to hand to mouth");
        return targets.size();
    }

    // ------------------------------------------------------------------ helpers

    private static void reply(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    }
}
