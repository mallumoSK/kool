package de.fabmax.kool.modules.ksl.blocks

import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.Attribute

fun KslScopeBuilder.vertexDisplacementBlock(cfg: PropertyBlockConfig): VertexDisplacementBlock {
    val displacementBlock = VertexDisplacementBlock(cfg, this)
    ops += displacementBlock
    return displacementBlock
}

class VertexDisplacementBlock(
    private val cfg: PropertyBlockConfig,
    parentScope: KslScopeBuilder
) : KslBlock(cfg.propertyName, parentScope) {

    val outProperty = outFloat1(parentScope.nextName("${opName}_outProperty"))
    val outSamplerValues = mutableMapOf<Pair<String, Attribute>, KslVectorExpression<KslFloat4, KslFloat1>>()

    val textures = mutableMapOf<PropertyBlockConfig.TextureProperty, KslUniform<KslColorSampler2d>>()

    init {
        body.apply {
            check(parentStage is KslVertexStage) { "VertexDisplacementBlock can only be added to KslVertexStage" }

            if (cfg.propertySources.isEmpty() || cfg.propertySources.first().blendMode != PropertyBlockConfig.BlendMode.Set) {
                outProperty set 0f.const
            }

            cfg.propertySources.forEach { source ->
                val propertyValue: KslScalarExpression<KslFloat1> = when (source) {
                    is PropertyBlockConfig.ConstProperty -> source.value.const
                    is PropertyBlockConfig.UniformProperty -> parentStage.program.uniformFloat1(source.uniformName)
                    is PropertyBlockConfig.VertexProperty -> parentStage.vertexAttribFloat1(source.propertyAttrib.name)
                    is PropertyBlockConfig.InstanceProperty -> parentStage.instanceAttribFloat1(source.propertyAttrib.name)
                    is PropertyBlockConfig.TextureProperty ->  {
                        var sampleValue = findExistingSampleValue(source.textureName, source.coordAttribute, parentStage)
                        if (sampleValue == null) {
                            val tex = parentStage.program.texture2d(source.textureName).also { textures[source] = it }
                            sampleValue = parentScope.run {
                                val texCoords = this@apply.parentStage.vertexAttribFloat2(source.coordAttribute.name)
                                float4Var(sampleTexture(tex, texCoords)).also {
                                    outSamplerValues[source.textureName to source.coordAttribute] = it
                                }
                            }
                        }
                        when (source.channel) {
                            0 -> sampleValue.r
                            1 -> sampleValue.g
                            2 -> sampleValue.b
                            3 -> sampleValue.a
                            else -> throw IllegalArgumentException("Invalid TextureProperty channel: ${source.channel}")
                        }
                    }
                }
                mixValue(source.blendMode, propertyValue)
            }
        }
    }

    private fun texCoordBlock(parentStage: KslVertexStage): TexCoordAttributeBlock {
        return parentStage.findBlock() ?: parentStage.main.run { texCoordAttributeBlock() }
    }

    private fun findExistingSampleValue(texName: String, attrib: Attribute, parentStage: KslVertexStage): KslExprFloat4? {
        return parentStage.main.getBlocks(null, mutableListOf())
            .filterIsInstance<VertexDisplacementBlock>()
            .map { it.outSamplerValues[texName to attrib] }
            .find { it != null }
    }

    private fun KslScopeBuilder.mixValue(blendMode: PropertyBlockConfig.BlendMode, value: KslExprFloat1) {
        when (blendMode) {
            PropertyBlockConfig.BlendMode.Set -> outProperty set value
            PropertyBlockConfig.BlendMode.Multiply -> outProperty *= value
            PropertyBlockConfig.BlendMode.Add -> outProperty += value
            PropertyBlockConfig.BlendMode.Subtract -> outProperty -= value
        }
    }
}
