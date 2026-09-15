package com.jom3a.easybeacon.client;

import com.jom3a.easybeacon.EasyBeaconPlacement;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * The mod's own render types.
 *
 * <p>Only one, and only because 26.3 took the stock one away. Up to 26.2 the hologram drew
 * obstructions with {@code RenderTypes.textBackgroundSeeThrough()}: untextured
 * {@code POSITION_COLOR} quads with no depth test, which is exactly what "show me what is buried
 * in the terrain" needs. 26.3 moved rendering onto the renderpearl backend and gave text
 * backgrounds no pipeline of their own, so that render type is gone and nothing stock replaces
 * it - every remaining see-through type is textured, and every remaining untextured one depth
 * tests.
 *
 * <p>So it is rebuilt here. The pipeline is a copy of vanilla's {@code DEBUG_FILLED_BOX} - same
 * shaders, same bind groups, same vertex format and topology - with the depth-stencil state left
 * empty, which is how 26.3's own see-through types spell "draw regardless of depth". That keeps
 * the geometry identical to the depth-tested box, so {@link HologramRenderer} emits one kind of
 * quad either way.
 *
 * <p>Pipelines are compiled on first use rather than from a registry, so this does not need to
 * be registered anywhere; it only needs to be built once, hence the static field.
 */
public final class EbpRenderTypes {
	private static final RenderPipeline SEE_THROUGH_FILLED_BOX_PIPELINE = RenderPipeline.builder()
			.withLocation(EasyBeaconPlacement.id("pipeline/see_through_filled_box"))
			.withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			// The see-through bit: no depth-stencil state at all.
			.withDepthStencilState(Optional.empty())
			.withCull(false)
			.build();

	private static final RenderType SEE_THROUGH_FILLED_BOX = RenderType.create(
			"easy_beacon_placement:see_through_filled_box",
			RenderSetup.builder(SEE_THROUGH_FILLED_BOX_PIPELINE).createRenderSetup());

	/** A filled box that ignores depth, so it stays visible inside solid terrain. */
	public static RenderType seeThroughFilledBox() {
		return SEE_THROUGH_FILLED_BOX;
	}

	private EbpRenderTypes() {
	}
}
