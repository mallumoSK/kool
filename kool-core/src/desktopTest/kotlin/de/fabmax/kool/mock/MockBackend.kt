package de.fabmax.kool.mock

import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ksl.KslComputeShader
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.generator.KslGenerator
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.backend.DepthRange
import de.fabmax.kool.pipeline.backend.RenderBackend
import de.fabmax.kool.pipeline.backend.gl.ComputeShaderCodeGl
import de.fabmax.kool.pipeline.backend.gl.GlslGenerator
import de.fabmax.kool.pipeline.backend.gl.ShaderCodeGl

class MockBackend(val shaderGen: KslGenerator = GlslGenerator(GlslGenerator.Hints("#version 330 core"))) : RenderBackend {

    override val name: String = "Mock backend"
    override val apiName: String = "MockAPI"
    override val deviceName: String = "Mock device"
    override val depthRange: DepthRange = DepthRange.NEGATIVE_ONE_TO_ONE
    override val canBlitRenderPasses: Boolean = false
    override val isOnscreenInfiniteDepthCapable: Boolean = false

    override fun renderFrame(ctx: KoolContext) { }

    override fun cleanup(ctx: KoolContext) { }

    override fun generateKslShader(shader: KslShader, pipeline: DrawPipeline): ShaderCode {
        val output = shaderGen.generateProgram(shader.program, pipeline) as KslGenerator.GeneratedSourceOutput
        return ShaderCodeGl(output.vertexSrc, output.fragmentSrc)
    }

    override fun generateKslComputeShader(shader: KslComputeShader, pipeline: ComputePipeline): ComputeShaderCode {
        val output = shaderGen.generateComputeProgram(shader.program, pipeline) as KslGenerator.GeneratedSourceOutput
        return ComputeShaderCodeGl(output.computeSrc)
    }

    override fun createOffscreenPass2d(parentPass: OffscreenRenderPass2d): OffscreenPass2dImpl {
        TODO("Not yet implemented")
    }

    override fun createOffscreenPassCube(parentPass: OffscreenRenderPassCube): OffscreenPassCubeImpl {
        TODO("Not yet implemented")
    }

    override fun uploadTextureToGpu(tex: Texture, data: TextureData) { }
}