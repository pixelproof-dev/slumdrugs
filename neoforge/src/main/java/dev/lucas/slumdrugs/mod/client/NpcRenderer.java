package dev.lucas.slumdrugs.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lucas.slumdrugs.mod.ModAttachments;
import dev.lucas.slumdrugs.mod.NpcData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Draws our villagers as people and everybody else's as villagers.
 *
 * <p>It replaces the villager renderer, because the game picks a renderer by entity type and
 * ours are ordinary villagers underneath. A villager without our data goes through the vanilla
 * renderer untouched, so a village still looks like a village. One of ours is drawn with the
 * player model in a skin from {@link NpcSkins}.
 *
 * <p>The state handed back to the game is still a villager's. The dispatcher uses it for the
 * shadow, the fire and the hitbox, which are the same either way; the person's own state rides
 * inside it and only this class ever sees it. It must not be a player state on the outside: the
 * dispatcher sends any player state to the player renderer, which expects a player.
 */
public final class NpcRenderer extends VillagerRenderer {

    private final Person wide;
    private final Person slim;
    private final NpcSkins skins;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = new Person(context, false);
        this.slim = new Person(context, true);
        this.skins = NpcSkins.load(context.getResourceManager());
    }

    static final class State extends VillagerRenderState {
        AvatarRenderState person;
        Person renderer;
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Villager villager, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(villager, state, partialTicks);
        if (!(state instanceof State ours) || !villager.hasData(ModAttachments.NPC.get())) return;
        NpcData data = villager.getData(ModAttachments.NPC.get());
        PlayerSkin skin = skins.pick(data, villager.getUUID());
        Person renderer = skin.model() == PlayerModelType.SLIM ? slim : wide;
        AvatarRenderState person = renderer.createRenderState(villager, partialTicks);
        person.skin = skin;
        ours.person = person;
        ours.renderer = renderer;
    }

    @Override
    public void submit(VillagerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state instanceof State ours && ours.person != null)
            ours.renderer.submit(ours.person, poseStack, collector, camera);
        else
            super.submit(state, poseStack, collector, camera);
    }

    /** A villager in a player's body. Never registered: only {@link NpcRenderer} calls it. */
    static final class Person extends HumanoidMobRenderer<Villager, AvatarRenderState, PlayerModel> {

        Person(EntityRendererProvider.Context context, boolean slim) {
            super(context, new PlayerModel(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
        }

        @Override
        public AvatarRenderState createRenderState() {
            return new AvatarRenderState();
        }

        @Override
        public Identifier getTextureLocation(AvatarRenderState state) {
            return state.skin.body().texturePath();
        }

        /** The player renderer's own shrink, so one of ours stands as tall as a player. */
        @Override
        protected void scale(AvatarRenderState state, PoseStack poseStack) {
            poseStack.scale(0.9375F, 0.9375F, 0.9375F);
        }

        /** Something in hand is held out, as a player holds it, rather than dangled. */
        @Override
        protected HumanoidModel.ArmPose getArmPose(Villager villager, HumanoidArm arm) {
            HumanoidModel.ArmPose pose = super.getArmPose(villager, arm);
            return pose == HumanoidModel.ArmPose.EMPTY && !villager.getItemHeldByArm(arm).isEmpty()
                    ? HumanoidModel.ArmPose.ITEM : pose;
        }
    }
}
