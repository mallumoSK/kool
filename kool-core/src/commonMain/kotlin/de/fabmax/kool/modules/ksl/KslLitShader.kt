package de.fabmax.kool.modules.ksl

import de.fabmax.kool.math.Mat3f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ksl.blocks.*
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.shading.AlphaMode
import de.fabmax.kool.util.Color

abstract class KslLitShader(val cfg: LitShaderConfig, model: KslProgram) : KslShader(model, cfg.pipelineCfg) {

    var color: Color by colorUniform(cfg.colorCfg)
    var colorMap: Texture2d? by colorTexture(cfg.colorCfg)

    var normalMap: Texture2d? by texture2d(
        cfg.normalMapCfg.normalMapName,
        cfg.normalMapCfg.defaultNormalMap
    )
    var normalMapStrength: Float by propertyUniform(cfg.normalMapCfg.strengthCfg)

    var emission: Color by colorUniform(cfg.emissionCfg)
    var emissionMap: Texture2d? by colorTexture(cfg.emissionCfg)

    var ssaoMap: Texture2d? by texture2d("tSsaoMap", cfg.aoCfg.defaultSsaoMap)
    var materialAo: Float by propertyUniform(cfg.aoCfg.materialAo)
    var materialAoMap: Texture2d? by propertyTexture(cfg.aoCfg.materialAo)

    var displacement: Float by propertyUniform(cfg.vertexCfg.displacementCfg)
    var displacementMap: Texture2d? by propertyTexture(cfg.vertexCfg.displacementCfg)

    var ambientFactor: Color by uniformColor("uAmbientColor")
    var ambientMapOrientation: Mat3f by uniformMat3f("uAmbientTextureOri")
    // if ambient color is image based
    var ambientMap: TextureCube? by textureCube("tAmbientTexture")
    // if ambient color is dual image based
    val ambientMaps = textureCubeArray("tAmbientTextures", 2)
    var ambientMapWeights by uniform2f("tAmbientWeights", Vec2f.X_AXIS)

    val ambientCfg: AmbientColor get() = cfg.ambientColor
    val colorCfg: ColorBlockConfig get() = cfg.colorCfg
    val emissionCfg: ColorBlockConfig get() = cfg.emissionCfg
    val materialAoCfg: PropertyBlockConfig get() = cfg.aoCfg.materialAo
    val displacementCfg: PropertyBlockConfig get() = cfg.vertexCfg.displacementCfg
    val isNormalMapped: Boolean get() = cfg.normalMapCfg.isNormalMapped
    val isSsao: Boolean get() = cfg.aoCfg.isSsao

    /**
     * Read-only list of shadow maps used by this shader. To modify the shadow maps, the shader has to be re-created.
     */
    val shadowMaps = cfg.shadowCfg.shadowMaps.map { it.shadowMap }

    init {
        when (val ac = ambientCfg) {
            is AmbientColor.Uniform -> ambientFactor = ac.color
            is AmbientColor.ImageBased -> {
                ambientMap = ac.ambientMap
                ambientFactor = ac.ambientFactor
            }
            is AmbientColor.DualImageBased -> {
                ambientFactor = ac.colorFactor
            }
        }
    }

    sealed class AmbientColor {
        class Uniform(val color: Color) : AmbientColor()
        class ImageBased(val ambientMap: TextureCube?, val ambientFactor: Color) : AmbientColor()
        class DualImageBased(val colorFactor: Color) : AmbientColor()
    }

    open class LitShaderConfig(builder: Builder) {
        val vertexCfg: BasicVertexConfig = builder.vertexCfg.build()
        val colorCfg: ColorBlockConfig = builder.colorCfg.build()
        val normalMapCfg: NormalMapConfig = builder.normalMapCfg.build()
        val aoCfg: AmbientOcclusionConfig = builder.aoCfg.build()
        val pipelineCfg: PipelineConfig = builder.pipelineCfg.build()
        val shadowCfg: ShadowConfig = builder.shadowCfg.build()
        val emissionCfg: ColorBlockConfig = builder.emissionCfg.build()

        val ambientColor: AmbientColor = builder.ambientColor
        val colorSpaceConversion = builder.colorSpaceConversion
        val maxNumberOfLights = builder.maxNumberOfLights
        val lightStrength = builder.lightStrength
        val alphaMode: AlphaMode = builder.alphaMode

        val modelCustomizer: (KslProgram.() -> Unit)? = builder.modelCustomizer

        open class Builder {
            val vertexCfg = BasicVertexConfig.Builder()
            val colorCfg = ColorBlockConfig.Builder("baseColor").constColor(Color.GRAY)
            val normalMapCfg = NormalMapConfig.Builder()
            val aoCfg = AmbientOcclusionConfig.Builder()
            val pipelineCfg = PipelineConfig.Builder()
            val shadowCfg = ShadowConfig.Builder()
            val emissionCfg = ColorBlockConfig.Builder("emissionColor").constColor(Color(0f, 0f, 0f, 0f))

            var ambientColor: AmbientColor = AmbientColor.Uniform(Color(0.2f, 0.2f, 0.2f).toLinear())
            var colorSpaceConversion = ColorSpaceConversion.LINEAR_TO_sRGB_HDR
            var maxNumberOfLights = 4
            var lightStrength = 1f
            var alphaMode: AlphaMode = AlphaMode.Blend

            var modelCustomizer: (KslProgram.() -> Unit)? = null

            fun enableSsao(ssaoMap: Texture2d? = null): Builder {
                aoCfg.enableSsao(ssaoMap)
                return this
            }

            inline fun ao(block: AmbientOcclusionConfig.Builder.() -> Unit) {
                aoCfg.block()
            }

            inline fun color(block: ColorBlockConfig.Builder.() -> Unit) {
                colorCfg.colorSources.clear()
                colorCfg.block()
            }

            inline fun emission(block: ColorBlockConfig.Builder.() -> Unit) {
                emissionCfg.colorSources.clear()
                emissionCfg.block()
            }

            fun uniformAmbientColor(color: Color = Color(0.2f, 0.2f, 0.2f).toLinear()): Builder {
                ambientColor = AmbientColor.Uniform(color)
                return this
            }

            fun imageBasedAmbientColor(ambientTexture: TextureCube? = null, colorFactor: Color = Color.WHITE): Builder {
                ambientColor = AmbientColor.ImageBased(ambientTexture, colorFactor)
                return this
            }

            fun dualImageBasedAmbientColor(colorFactor: Color = Color.WHITE): Builder {
                ambientColor = AmbientColor.DualImageBased(colorFactor)
                return this
            }

            inline fun normalMapping(block: NormalMapConfig.Builder.() -> Unit) {
                normalMapCfg.block()
            }

            inline fun pipeline(block: PipelineConfig.Builder.() -> Unit) {
                pipelineCfg.block()
            }

            inline fun shadow(block: ShadowConfig.Builder.() -> Unit) {
                shadowCfg.block()
            }

            inline fun vertices(block: BasicVertexConfig.Builder.() -> Unit) {
                vertexCfg.block()
            }

            open fun build() = LitShaderConfig(this)
        }
    }

    abstract class LitShaderModel<T: LitShaderConfig>(name: String) : KslProgram(name) {

        open fun createModel(cfg: T) {
            val camData = cameraData()
            val positionWorldSpace = interStageFloat3("positionWorldSpace")
            val normalWorldSpace = interStageFloat3("normalWorldSpace")
            val projPosition = interStageFloat4("projPosition")
            var tangentWorldSpace: KslInterStageVector<KslFloat4, KslFloat1>? = null

            val texCoordBlock: TexCoordAttributeBlock
            val shadowMapVertexStage: ShadowBlockVertexStage

            vertexStage {
                main {
                    val vertexBlock = vertexTransformBlock(cfg.vertexCfg) {
                        inModelMat(modelMatrix().matrix)
                        inLocalPos(vertexAttribFloat3(Attribute.POSITIONS.name))
                        inLocalNormal(vertexAttribFloat3(Attribute.NORMALS.name))

                        if (cfg.normalMapCfg.isNormalMapped) {
                            // if normal mapping is enabled, the input vertex data is expected to have a tangent attribute
                            inLocalTangent(vertexAttribFloat4(Attribute.TANGENTS.name))
                        }
                    }

                    // world position and normal are made available via ports for custom models to modify them
                    val worldPos = float3Port("worldPos", vertexBlock.outWorldPos)
                    val worldNormal = float3Port("worldNormal", vertexBlock.outWorldNormal)

                    positionWorldSpace.input set worldPos
                    normalWorldSpace.input set worldNormal
                    projPosition.input set (camData.viewProjMat * float4Value(worldPos, 1f))
                    outPosition set projPosition.input

                    if (cfg.normalMapCfg.isNormalMapped) {
                        tangentWorldSpace = interStageFloat4().apply { input set vertexBlock.outWorldTangent }
                    }

                    // texCoordBlock is used by various other blocks to access texture coordinate vertex
                    // attributes (usually either none, or Attribute.TEXTURE_COORDS but there can be more)
                    texCoordBlock = texCoordAttributeBlock()

                    // project coordinates into shadow map / light space
                    shadowMapVertexStage = vertexShadowBlock(cfg.shadowCfg) {
                        inPositionWorldSpace(worldPos)
                        inNormalWorldSpace(worldNormal)
                    }
                }
            }

            fragmentStage {
                val lightData = sceneLightData(cfg.maxNumberOfLights)

                main {
                    // determine main color (albedo)
                    val colorBlock = fragmentColorBlock(cfg.colorCfg)
                    val baseColorPort = float4Port("baseColor", colorBlock.outColor)

                    val baseColor = float4Var(baseColorPort)
                    when (val alphaMode = cfg.alphaMode) {
                        is AlphaMode.Blend -> { }
                        is AlphaMode.Opaque -> baseColor.a set 1f.const
                        is AlphaMode.Mask -> {
                            `if`(baseColorPort.a lt alphaMode.cutOff.const) {
                                discard()
                            }
                            baseColor.a set 1f.const
                        }
                    }

                    val emissionBlock = fragmentColorBlock(cfg.emissionCfg)
                    val emissionColorPort = float4Port("emissionColor", emissionBlock.outColor)

                    val vertexNormal = float3Var(normalize(normalWorldSpace.output))
                    if (cfg.pipelineCfg.cullMethod.isBackVisible && cfg.vertexCfg.isFlipBacksideNormals) {
                        `if`(!inIsFrontFacing) {
                            vertexNormal *= (-1f).const3
                        }
                    }

                    // do normal map computations (if enabled)
                    val bumpedNormal = if (cfg.normalMapCfg.isNormalMapped) {
                        val normalMapStrength = fragmentPropertyBlock(cfg.normalMapCfg.strengthCfg).outProperty
                        normalMapBlock(cfg.normalMapCfg) {
                            inTangentWorldSpace(tangentWorldSpace!!.output)
                            inNormalWorldSpace(vertexNormal)
                            inStrength(normalMapStrength)
                            inTexCoords(texCoordBlock.getAttributeCoords(cfg.normalMapCfg.coordAttribute))
                        }.outBumpNormal
                    } else {
                        vertexNormal
                    }
                    // make final normal value available to model customizer
                    val normal = float3Port("normal", bumpedNormal)
                    val worldPos = float3Port("worldPos", positionWorldSpace.output)

                    // create an array with light strength values per light source (1.0 = full strength)
                    val shadowFactors = float1Array(lightData.maxLightCount, 1f.const)
                    // adjust light strength values by shadow maps
                    fragmentShadowBlock(shadowMapVertexStage, shadowFactors)

                    val aoFactor = float1Var(fragmentPropertyBlock(cfg.aoCfg.materialAo).outProperty)
                    if (cfg.aoCfg.isSsao) {
                        val aoMap = texture2d("tSsaoMap")
                        val aoUv = float2Var(projPosition.output.xy / projPosition.output.w * 0.5f.const + 0.5f.const)
                        aoFactor *= sampleTexture(aoMap, aoUv).x
                    }

                    val irradiance = when (cfg.ambientColor) {
                        is AmbientColor.Uniform -> uniformFloat4("uAmbientColor").rgb
                        is AmbientColor.ImageBased -> {
                            val ambientOri = uniformMat3("uAmbientTextureOri")
                            val ambientTex = textureCube("tAmbientTexture")
                            (sampleTexture(ambientTex, ambientOri * normal) * uniformFloat4("uAmbientColor")).rgb
                        }
                        is AmbientColor.DualImageBased -> {
                            val ambientOri = uniformMat3("uAmbientTextureOri")
                            val ambientTexs = textureArrayCube("tAmbientTextures", 2)
                            val ambientWeights = uniformFloat2("tAmbientWeights")
                            val ambientColor = float4Var(sampleTexture(ambientTexs[0], ambientOri * normal) * ambientWeights.x)
                            `if`(ambientWeights.y gt 0f.const) {
                                ambientColor += float4Var(sampleTexture(ambientTexs[1], ambientOri * normal) * ambientWeights.y)
                            }
                            (ambientColor * uniformFloat4("uAmbientColor")).rgb
                        }
                    }

                    // main material block
                    val materialColor = createMaterial(
                        cfg = cfg,
                        camData = camData,
                        irradiance = irradiance,
                        lightData = lightData,
                        shadowFactors = shadowFactors,
                        aoFactor = aoFactor,
                        normal = normal,
                        fragmentWorldPos = worldPos,
                        baseColor = baseColor,
                        emissionColor = emissionColorPort
                    )

                    val materialColorPort = float4Port("materialColor", materialColor)

                    // set fragment stage output color
                    val outRgb = float3Var(materialColorPort.rgb)
                    if (cfg.pipelineCfg.blendMode == BlendMode.BLEND_PREMULTIPLIED_ALPHA) {
                        outRgb set outRgb * materialColorPort.a
                    }
                    outRgb set convertColorSpace(outRgb, cfg.colorSpaceConversion)

                    when (cfg.alphaMode) {
                        is AlphaMode.Blend -> colorOutput(outRgb, materialColorPort.a)
                        is AlphaMode.Mask -> colorOutput(outRgb, 1f.const)
                        is AlphaMode.Opaque -> colorOutput(outRgb, 1f.const)
                    }
                }
            }

            cfg.modelCustomizer?.invoke(this)
        }

        protected abstract fun KslScopeBuilder.createMaterial(
            cfg: T,
            camData: CameraData,
            irradiance: KslExprFloat3,
            lightData: SceneLightData,
            shadowFactors: KslExprFloat1Array,
            aoFactor: KslExprFloat1,
            normal: KslExprFloat3,
            fragmentWorldPos: KslExprFloat3,
            baseColor: KslExprFloat4,
            emissionColor: KslExprFloat4,
        ): KslExprFloat4
    }
}