package de.fabmax.kool.pipeline.backend.gl

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.backend.stats.BackendStats
import de.fabmax.kool.util.Float32Buffer
import de.fabmax.kool.util.Time

class QueueRenderer(val backend: RenderBackendGl) {

    private val glAttribs = GlAttribs()

    private val colorBufferClearVal = Float32Buffer(4)

    private val ctx: KoolContext = backend.ctx
    private val gl: GlApi = backend.gl

    fun renderViews(renderPass: RenderPass, mipLevel: Int = 0) {
        for (i in renderPass.views.indices) {
            renderView(renderPass.views[i], mipLevel)
        }
    }

    fun renderView(view: RenderPass.View, mipLevel: Int = 0) = view.apply {
        val rpHeight = renderPass.height shr mipLevel
        val viewportY = rpHeight - viewport.y - viewport.height
        gl.viewport(viewport.x, viewportY, viewport.width, viewport.height)
        gl.scissor(viewport.x, viewportY, viewport.width, viewport.height)

        gl.depthMask(true)
        gl.clearDepth(if (renderPass.isReverseDepth) 0f else 1f)

        val rp = renderPass
        if (rp is OffscreenRenderPass) {
            for (i in 0 until rp.numColorAttachments) {
                clearColors[i]?.let { color ->
                    colorBufferClearVal.clear()
                    color.putTo(colorBufferClearVal)
                    gl.clearBufferfv(gl.COLOR, i, colorBufferClearVal)
                }
            }
            if (clearDepth) {
                gl.clear(gl.DEPTH_BUFFER_BIT)
            }

        } else {
            clearColor?.let { gl.clearColor(it.r, it.g, it.b, it.a) }
            val clearMask = clearMask()
            if (clearMask != 0) {
                gl.clear(clearMask)
            }
        }

        for (cmd in drawQueue.commands) {
            val t = Time.precisionTime

            if (cmd.geometry.numIndices == 0) continue
            val pipeline = cmd.pipeline ?: continue

            val drawInfo = backend.shaderMgr.bindDrawShader(cmd)
            if (!drawInfo.isValid || drawInfo.numIndices == 0) continue

            glAttribs.setupPipelineAttribs(pipeline, renderPass.isReverseDepth)

            val insts = cmd.mesh.instances
            if (insts == null) {
                gl.drawElements(drawInfo.primitiveType, drawInfo.numIndices, drawInfo.indexType)
                BackendStats.addDrawCommands(1, cmd.geometry.numPrimitives)
            } else if (insts.numInstances > 0) {
                gl.drawElementsInstanced(drawInfo.primitiveType, drawInfo.numIndices, drawInfo.indexType, insts.numInstances)
                BackendStats.addDrawCommands(1, cmd.geometry.numPrimitives * insts.numInstances)
            }

            cmd.mesh.drawTime = Time.precisionTime - t
        }
    }

    private inner class GlAttribs {
        var actIsWriteDepth = true
        var actDepthTest: DepthCompareOp? = null
        var actCullMethod: CullMethod? = null
        var lineWidth = 0f

        fun setupPipelineAttribs(pipeline: DrawPipeline, isReversedDepth: Boolean) {
            setBlendMode(pipeline.blendMode)
            setDepthTest(pipeline.depthCompareOp, isReversedDepth && pipeline.autoReverseDepthFunc)
            setWriteDepth(pipeline.isWriteDepth)
            setCullMethod(pipeline.cullMethod)
            if (lineWidth != pipeline.lineWidth) {
                lineWidth = pipeline.lineWidth
                gl.lineWidth(pipeline.lineWidth)
            }
        }

        private fun setCullMethod(cullMethod: CullMethod) {
            if (this.actCullMethod != cullMethod) {
                this.actCullMethod = cullMethod
                when (cullMethod) {
                    CullMethod.CULL_BACK_FACES -> {
                        gl.enable(gl.CULL_FACE)
                        gl.cullFace(gl.BACK)
                    }
                    CullMethod.CULL_FRONT_FACES -> {
                        gl.enable(gl.CULL_FACE)
                        gl.cullFace(gl.FRONT)
                    }
                    CullMethod.NO_CULLING -> gl.disable(gl.CULL_FACE)
                }
            }
        }

        private fun setWriteDepth(enabled: Boolean) {
            if (actIsWriteDepth != enabled) {
                actIsWriteDepth = enabled
                gl.depthMask(enabled)
            }
        }

        private fun setDepthTest(depthCompareOp: DepthCompareOp, isReversedDepth: Boolean) {
            val newDepthOp = if (!isReversedDepth) {
                depthCompareOp
            } else {
                when (depthCompareOp) {
                    DepthCompareOp.LESS -> DepthCompareOp.GREATER
                    DepthCompareOp.LESS_EQUAL -> DepthCompareOp.GREATER_EQUAL
                    DepthCompareOp.GREATER -> DepthCompareOp.LESS
                    DepthCompareOp.GREATER_EQUAL -> DepthCompareOp.LESS_EQUAL
                    else -> depthCompareOp
                }
            }

            if (actDepthTest != newDepthOp) {
                actDepthTest = newDepthOp
                if (newDepthOp == DepthCompareOp.DISABLED) {
                    gl.disable(gl.DEPTH_TEST)
                } else {
                    gl.enable(gl.DEPTH_TEST)
                    gl.depthFunc(newDepthOp.glOp)
                }
            }
        }

        private fun setBlendMode(blendMode: BlendMode) {
            when (blendMode) {
                BlendMode.DISABLED -> gl.disable(gl.BLEND)
                BlendMode.BLEND_ADDITIVE -> {
                    gl.blendFunc(gl.ONE, gl.ONE)
                    gl.enable(gl.BLEND)
                }
                BlendMode.BLEND_MULTIPLY_ALPHA -> {
                    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)
                    gl.enable(gl.BLEND)
                }
                BlendMode.BLEND_PREMULTIPLIED_ALPHA -> {
                    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA)
                    gl.enable(gl.BLEND)
                }
            }
        }
    }

    private fun RenderPass.View.clearMask(): Int {
        var mask = 0
        if (clearDepth) {
            mask = gl.DEPTH_BUFFER_BIT
        }
        if (clearColor != null) {
            mask = mask or gl.COLOR_BUFFER_BIT
        }
        return mask
    }

    private val DepthCompareOp.glOp: Int
        get() = when(this) {
            DepthCompareOp.DISABLED -> 0
            DepthCompareOp.ALWAYS -> gl.ALWAYS
            DepthCompareOp.NEVER -> gl.NEVER
            DepthCompareOp.LESS -> gl.LESS
            DepthCompareOp.LESS_EQUAL -> gl.LEQUAL
            DepthCompareOp.GREATER -> gl.GREATER
            DepthCompareOp.GREATER_EQUAL -> gl.GEQUAL
            DepthCompareOp.EQUAL -> gl.EQUAL
            DepthCompareOp.NOT_EQUAL -> gl.NOTEQUAL
        }
}