package de.fabmax.kool.pipeline.backend.gl

import de.fabmax.kool.KoolContext
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.configJs
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ksl.lang.xy
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.backend.RenderBackendJs
import de.fabmax.kool.pipeline.backend.webgpu.GPUPowerPreference
import de.fabmax.kool.platform.JsContext
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addTextureMesh
import de.fabmax.kool.util.Viewport
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement

class RenderBackendGlImpl(ctx: KoolContext, canvas: HTMLCanvasElement) : RenderBackendGl(GlImpl, ctx), RenderBackendJs {
    override val deviceName = "WebGL"

    override val glslGeneratorHints: GlslGenerator.Hints = GlslGenerator.Hints(
        glslVersionStr = "#version 300 es",
    )

    init {
        val options = if (KoolSystem.configJs.powerPreference == GPUPowerPreference.highPerformance) {
            js(" { antialias: true, stencil: false, powerPreference: 'high-performance' } ")
        } else {
            js(" { antialias: true, stencil: false, powerPreference: 'low-power' } ")
        }

        val webGlCtx = (canvas.getContext("webgl2", options) ?: canvas.getContext("experimental-webgl2", options)) as WebGL2RenderingContext?
        check(webGlCtx != null) {
            val txt = "Unable to initialize WebGL2 context. Your browser may not support it."
            js("alert(txt)")
            txt
        }
        GlImpl.initWebGl(webGlCtx)
        setupGl()
    }

    override suspend fun startRenderLoop() {
        window.requestAnimationFrame { t -> (ctx as JsContext).renderFrame(t) }
    }

    override fun cleanup(ctx: KoolContext) {
        // for now, we leave the cleanup to the system...
    }

    override fun renderFrame(ctx: KoolContext) {
        super.renderFrame(ctx)
        GlImpl.gl.finish()
    }

    override fun blitFrameBuffers(
        src: OffscreenRenderPass2d,
        dst: OffscreenRenderPass2dGl?,
        srcViewport: Viewport,
        dstViewport: Viewport,
        mipLevel: Int
    ) {
        if (dst == null && numSamples > 1) {
            // on WebGL blitting frame-buffers does not work if target frame-buffer is multi-sampled
            //  -> use a non-multi-sampled texture frame-buffer as conversion helper and then render the texture
            //     using a shader. This means some overhead, but apparently it's the only thing we can do.
            val ctx = KoolSystem.requireContext()
            blitTempFrameBuffer.blitRenderPass = src
            blitTempFrameBuffer.setSize(src.width, src.height)
            blitTempFrameBuffer.impl.draw(ctx)

            blitScene.mainRenderPass.renderPass.update(ctx)
            blitScene.mainRenderPass.renderPass.collectDrawCommands(ctx)
            queueRenderer.renderView(blitScene.mainRenderPass.screenView)
        } else {
            super.blitFrameBuffers(src, dst, srcViewport, dstViewport, mipLevel)
        }
    }

    private val blitTempFrameBuffer: OffscreenRenderPass2d by lazy {
        OffscreenRenderPass2d(Node(), renderPassConfig {
            colorTargetTexture(TexFormat.RGBA)
        })
    }

    private val blitScene: Scene by lazy {
        Scene().apply {
            addTextureMesh {
                generate {
                    centeredRect {
                        size.set(2f, 2f)
                    }
                }
                shader = KslUnlitShader {
                    color { textureColor(blitTempFrameBuffer.colorTexture, gamma = 1f) }
                    modelCustomizer = {
                        vertexStage {
                            main {
                                outPosition set float4Value(vertexAttribFloat3(Attribute.POSITIONS).xy, 0f, 1f)
                            }
                        }
                    }
                }
            }
        }
    }
}
