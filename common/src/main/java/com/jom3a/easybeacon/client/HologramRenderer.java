package com.jom3a.easybeacon.client;

import java.util.ArrayList;
import java.util.List;

import com.jom3a.easybeacon.EbpConfig;
import com.jom3a.easybeacon.beacon.BeaconMaterials;
import com.jom3a.easybeacon.beacon.PlacementPlan;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Draws the pyramid preview.
 *
 * <p>Slots that will be filled are drawn as a translucent ghost of the <em>actual</em> block that
 * is going to be placed, so the preview reads as the structure you are about to build rather
 * than as abstract markers.
 *
 * <p>Obstructions are drawn differently on purpose. They are almost always buried inside terrain,
 * and the whole point of showing them is to answer "what is in my way?" — so they use
 * {@link RenderTypes#textBackgroundSeeThrough()}, whose pipeline sets no depth-stencil state at
 * all and therefore draws through solid blocks. It takes the same {@code POSITION_COLOR} quads as
 * the ordinary debug box, so it is a straight swap.
 *
 * <p>26.2 note: {@code MultiBufferSource} is gone, so geometry is handed to a
 * {@link SubmitNodeCollector}. Both Fabric and NeoForge supply the same vanilla
 * {@code (PoseStack, SubmitNodeCollector)} pair, which is why this renderer is shared.
 */
public final class HologramRenderer {
	/** Packed lightmap coords for "fully lit" — block light 15, sky light 15. */
	private static final int FULL_BRIGHT = (15 << 4) | (15 << 20);

	private HologramRenderer() {
	}

	/** Called once per frame from each platform's level-render hook. */
	public static void render(PoseStack poseStack, SubmitNodeCollector collector) {
		PlacementPlan plan = EbpClient.currentPlan();
		Minecraft minecraft = Minecraft.getInstance();

		if (plan == null || minecraft.player == null) {
			return;
		}

		EbpConfig config = EbpConfig.get();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();

		Block previewBlock = BeaconMaterials.previewBlock(minecraft.player.getInventory());
		BlockState ghostState = (previewBlock == null ? Blocks.IRON_BLOCK : previewBlock).defaultBlockState();

		for (PlacementPlan.Slot slot : plan.slots()) {
			switch (slot.state()) {
				case PLACEABLE -> drawGhost(poseStack, collector, config, models, slot.pos(), camera,
						ghostState, config.placeableColor());

				// Drawn through terrain: these are the blocks the player needs to go dig out.
				// Note the collector batches geometry per render type, so submission order does
				// not decide what ends up on top between types - only alpha does.
				case OBSTRUCTED -> drawMarker(poseStack, collector, config, slot.pos(), camera,
						config.obstructedColor(), config.obstructionsThroughWalls);

				case MISSING_MATERIAL -> drawMarker(poseStack, collector, config, slot.pos(), camera,
						config.missingMaterialColor(), false);

				// Already correct - a faint outline is enough, a ghost would just add noise.
				case ALREADY_VALID -> drawOutlineOnly(poseStack, collector, config, slot.pos(), camera,
						config.alreadyValidColor());
			}
		}

		// The beacon's own outline carries the verdict. The pyramid is far too big to check by
		// eye and much of it sits off-screen, so the one thing the player is already looking at -
		// the beacon itself - is where the answer belongs.
		drawGhost(poseStack, collector, config, models, plan.beaconPos(), camera,
				Blocks.BEACON.defaultBlockState(), verdictColour(config, plan));
	}

	/**
	 * A translucent copy of a real block model, tinted to show what the slot means.
	 *
	 * <p>NeoForge deprecates the vanilla {@code collectParts} in favour of an overload taking its
	 * own {@code ModelData} (for connected textures and similar). Fabric has no such overload, and
	 * beacon base blocks are plain full cubes with no model data, so the vanilla call is both the
	 * correct one here and the only one that compiles on both platforms.
	 */
	@SuppressWarnings("deprecation")
	private static void drawGhost(PoseStack poseStack, SubmitNodeCollector collector, EbpConfig config,
			BlockStateModelSet models, BlockPos pos, Vec3 camera, BlockState state, int outlineColour) {
		List<BlockStateModelPart> parts = new ArrayList<>();
		models.get(state).collectParts(RandomSource.create(pos.asLong()), parts);

		if (parts.isEmpty()) {
			return;
		}

		QuadInstance quadInstance = new QuadInstance();
		// White, not the state colour: a previewed iron block should still look like iron. The
		// state is communicated by the outline drawn around it.
		quadInstance.setColor(config.ghostTint());
		quadInstance.setLightCoords(FULL_BRIGHT);
		quadInstance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

		poseStack.pushPose();
		translateToBlock(poseStack, pos, camera);
		shrinkAboutCentre(poseStack, 1.0F - (float) config.boxInset * 2.0F);

		collector.submitCustomGeometry(
				poseStack,
				RenderTypes.translucentMovingBlock(),
				(pose, consumer) -> emitModel(pose, consumer, parts, quadInstance));

		poseStack.popPose();

		drawOutlineOnly(poseStack, collector, config, pos, camera, opaque(outlineColour));
	}

	/**
	 * A plain coloured box. When {@code throughWalls} is set it is drawn with a render type that
	 * has no depth testing, so it stays visible inside solid terrain.
	 */
	private static void drawMarker(PoseStack poseStack, SubmitNodeCollector collector, EbpConfig config,
			BlockPos pos, Vec3 camera, int colour, boolean throughWalls) {
		if (isTransparent(colour)) {
			return;
		}

		poseStack.pushPose();
		translateToBlock(poseStack, pos, camera);

		float min = (float) config.boxInset;
		float max = 1.0F - (float) config.boxInset;

		collector.submitCustomGeometry(
				poseStack,
				throughWalls ? RenderTypes.textBackgroundSeeThrough() : RenderTypes.debugFilledBox(),
				(pose, consumer) -> emitBox(pose, consumer, min, max, colour));

		poseStack.popPose();

		if (config.drawOutlines) {
			drawOutlineOnly(poseStack, collector, config, pos, camera, opaque(colour));
		}
	}

	private static void drawOutlineOnly(PoseStack poseStack, SubmitNodeCollector collector, EbpConfig config,
			BlockPos pos, Vec3 camera, int colour) {
		if (!config.drawOutlines || isTransparent(colour)) {
			return;
		}

		poseStack.pushPose();
		translateToBlock(poseStack, pos, camera);
		collector.submitShapeOutline(poseStack, Shapes.block(), RenderTypes.lines(), colour, 1.0F, false);
		poseStack.popPose();
	}

	/** World geometry is drawn relative to the camera, not the world origin. */
	private static void translateToBlock(PoseStack poseStack, BlockPos pos, Vec3 camera) {
		poseStack.translate(
				pos.getX() - camera.x,
				pos.getY() - camera.y,
				pos.getZ() - camera.z);
	}

	/** Scales about the block centre so ghosts do not z-fight with each other or with terrain. */
	private static void shrinkAboutCentre(PoseStack poseStack, float scale) {
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.scale(scale, scale, scale);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
	}

	private static void emitModel(PoseStack.Pose pose, VertexConsumer consumer,
			List<BlockStateModelPart> parts, QuadInstance quadInstance) {
		for (BlockStateModelPart part : parts) {
			// Nothing culls these, so every face is emitted: the general quads plus each side.
			emitQuads(pose, consumer, part.getQuads(null), quadInstance);

			for (Direction direction : Direction.values()) {
				emitQuads(pose, consumer, part.getQuads(direction), quadInstance);
			}
		}
	}

	private static void emitQuads(PoseStack.Pose pose, VertexConsumer consumer,
			List<BakedQuad> quads, QuadInstance quadInstance) {
		for (BakedQuad quad : quads) {
			consumer.putBakedQuad(pose, quad, quadInstance);
		}
	}

	/** Emits the six faces of an axis-aligned box as quads. */
	private static void emitBox(PoseStack.Pose pose, VertexConsumer consumer, float a, float b, int colour) {
		// Down (-Y)
		consumer.addVertex(pose, a, a, a).setColor(colour);
		consumer.addVertex(pose, b, a, a).setColor(colour);
		consumer.addVertex(pose, b, a, b).setColor(colour);
		consumer.addVertex(pose, a, a, b).setColor(colour);

		// Up (+Y)
		consumer.addVertex(pose, a, b, b).setColor(colour);
		consumer.addVertex(pose, b, b, b).setColor(colour);
		consumer.addVertex(pose, b, b, a).setColor(colour);
		consumer.addVertex(pose, a, b, a).setColor(colour);

		// North (-Z)
		consumer.addVertex(pose, a, b, a).setColor(colour);
		consumer.addVertex(pose, b, b, a).setColor(colour);
		consumer.addVertex(pose, b, a, a).setColor(colour);
		consumer.addVertex(pose, a, a, a).setColor(colour);

		// South (+Z)
		consumer.addVertex(pose, a, a, b).setColor(colour);
		consumer.addVertex(pose, b, a, b).setColor(colour);
		consumer.addVertex(pose, b, b, b).setColor(colour);
		consumer.addVertex(pose, a, b, b).setColor(colour);

		// West (-X)
		consumer.addVertex(pose, a, a, a).setColor(colour);
		consumer.addVertex(pose, a, a, b).setColor(colour);
		consumer.addVertex(pose, a, b, b).setColor(colour);
		consumer.addVertex(pose, a, b, a).setColor(colour);

		// East (+X)
		consumer.addVertex(pose, b, b, a).setColor(colour);
		consumer.addVertex(pose, b, b, b).setColor(colour);
		consumer.addVertex(pose, b, a, b).setColor(colour);
		consumer.addVertex(pose, b, a, a).setColor(colour);
	}

	/**
	 * Green when the pyramid will reach the tier being previewed, amber when it will only manage
	 * a lower tier, red when it will not work at all.
	 */
	private static int verdictColour(EbpConfig config, PlacementPlan plan) {
		int effective = plan.effectiveTier();

		if (effective == plan.tier()) {
			return config.placeableColor();
		}

		return effective == 0 ? config.obstructedColor() : config.missingMaterialColor();
	}

	private static boolean isTransparent(int argb) {
		return (argb >>> 24) == 0;
	}

	/** Same hue as the fill, but fully opaque, so outlines stay readable against terrain. */
	private static int opaque(int argb) {
		return argb | 0xFF000000;
	}
}
