package de.fabmax.kool.modules.ksl.blocks

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ksl.KslShaderListener
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.ShaderBase
import de.fabmax.kool.pipeline.UniformBindingMat4fv
import de.fabmax.kool.pipeline.drawqueue.DrawCommand
import de.fabmax.kool.util.ShadowMap
import de.fabmax.kool.util.SimpleShadowMap

fun KslProgram.shadowData(shadowCfg: ShadowConfig): ShadowData {
    return (dataBlocks.find { it is ShadowData } as? ShadowData) ?: ShadowData(shadowCfg, this)
}

class ShadowData(val shadowCfg: ShadowConfig, program: KslProgram) : KslDataBlock, KslShaderListener {
    override val name = NAME

    val shadowMapInfos: List<ShadowMapInfo>
    val subMaps: List<SimpleShadowMap>
    val numSubMaps: Int get() = subMaps.size

    val shadowMapViewProjMats: KslUniformMatrixArray<KslMat4, KslFloat4>
    val depthMaps: KslUniformArray<KslDepthSampler2d>

    private var uShadowMapViewProjMats: UniformBindingMat4fv? = null

    init {
        var i = 0
        val mapInfos = mutableListOf<ShadowMapInfo>()
        val maps = mutableListOf<SimpleShadowMap>()
        for (shadowMap in shadowCfg.shadowMaps) {
            val info = ShadowMapInfo(shadowMap.shadowMap, i, shadowMap.samplePattern)
            i = info.toIndexExcl
            mapInfos += info
            maps += shadowMap.shadowMap.subMaps
        }
        shadowMapInfos = mapInfos
        subMaps = maps

        // If shadowCfg is empty, uniforms are created with array size 0, which is kind of invalid. However, they are
        // also not referenced later on and therefore removed before shader is generated (again because shadowCfg is empty)
        shadowMapViewProjMats = program.uniformMat4Array(UNIFORM_NAME_SHADOW_VP_MATS, numSubMaps)
        depthMaps = program.depthTextureArray2d(SAMPLER_NAME_SHADOW_MAPS, numSubMaps)

        program.dataBlocks += this
        if (subMaps.isNotEmpty()) {
            program.shaderListeners += this
        }
    }

    override fun onShaderCreated(shader: ShaderBase<*>) {
        uShadowMapViewProjMats = shader.uniformMat4fv(UNIFORM_NAME_SHADOW_VP_MATS)
        val maps = shader.texture2dArray(SAMPLER_NAME_SHADOW_MAPS, subMaps.size)
        subMaps.forEachIndexed { i, shadowMap ->
            maps[i] = shadowMap.depthTexture
        }
    }

    override fun onUpdate(cmd: DrawCommand) {
        uShadowMapViewProjMats?.let { mats ->
            subMaps.forEachIndexed { i, shadowMap ->
                mats[i] = shadowMap.lightViewProjMat
            }
        }
    }

    class ShadowMapInfo(val shadowMap: ShadowMap, val fromIndexIncl: Int, val samplePattern: List<Vec2f>) {
        val subMaps: List<SimpleShadowMap> get() = shadowMap.subMaps
        val toIndexExcl = fromIndexIncl + shadowMap.subMaps.size
    }

    companion object {
        const val NAME = "ShadowData"

        const val UNIFORM_NAME_SHADOW_VP_MATS = "uShadowMapViewProjMats"
        const val SAMPLER_NAME_SHADOW_MAPS = "tDepthMaps"
    }
}