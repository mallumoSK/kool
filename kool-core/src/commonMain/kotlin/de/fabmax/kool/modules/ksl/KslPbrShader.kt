package de.fabmax.kool.modules.ksl

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.modules.ksl.blocks.*
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.DrawPipeline
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureCube
import de.fabmax.kool.pipeline.ibl.EnvironmentMaps
import de.fabmax.kool.scene.Mesh

open class KslPbrShader(cfg: Config, model: KslProgram = Model(cfg)) : KslLitShader(cfg, model) {

    constructor(block: Config.Builder.() -> Unit) : this(Config.Builder().apply(block).build())

    // basic material properties
    var roughness: Float by propertyUniform(cfg.roughnessCfg)
    var roughnessMap: Texture2d? by propertyTexture(cfg.roughnessCfg)
    var metallic: Float by propertyUniform(cfg.metallicCfg)
    var metallicMap: Texture2d? by propertyTexture(cfg.metallicCfg)

    val reflectionMaps = textureCubeArray("tReflectionMaps", 2)
    var reflectionMapWeights: Vec2f by uniform2f("uReflectionWeights")
    var reflectionStrength: Vec4f by uniform4f("uReflectionStrength", Vec4f(cfg.reflectionStrength, 0f))

    var brdfLut: Texture2d? by texture2d("tBrdfLut")

    val roughnessCfg = cfg.roughnessCfg
    val metallicCfg = cfg.metallicCfg

    var reflectionMap: TextureCube?
        get() = reflectionMaps[0]
        set(value) {
            reflectionMaps[0] = value
            reflectionMaps[1] = value
            reflectionMapWeights = Vec2f.X_AXIS
        }

    init {
        reflectionMap = cfg.reflectionMap
    }

    override fun createPipeline(mesh: Mesh, updateEvent: RenderPass.UpdateEvent): DrawPipeline {
        return super.createPipeline(mesh, updateEvent).also {
            if (brdfLut == null) {
                brdfLut = updateEvent.ctx.defaultPbrBrdfLut
            }
        }
    }

    open class Config(builder: Builder) : LitShaderConfig(builder) {
        val metallicCfg = builder.metallicCfg.build()
        val roughnessCfg = builder.roughnessCfg.build()

        val isTextureReflection = builder.isTextureReflection
        val reflectionStrength = builder.reflectionStrength
        val reflectionMap = builder.reflectionMap

        open class Builder : LitShaderConfig.Builder() {
            val metallicCfg = PropertyBlockConfig.Builder("metallic").apply { constProperty(0f) }
            val roughnessCfg = PropertyBlockConfig.Builder("roughness").apply { constProperty(0.5f) }

            var isTextureReflection = false
            var reflectionStrength = Vec3f.ONES
            var reflectionMap: TextureCube? = null
                set(value) {
                    field = value
                    isTextureReflection = value != null
                }

            fun metallic(block: PropertyBlockConfig.Builder.() -> Unit) {
                metallicCfg.propertySources.clear()
                metallicCfg.block()
            }

            fun metallic(value: Float): Builder {
                metallic { constProperty(value) }
                return this
            }

            fun roughness(block: PropertyBlockConfig.Builder.() -> Unit) {
                roughnessCfg.propertySources.clear()
                roughnessCfg.block()
            }

            fun roughness(value: Float): Builder {
                roughness { constProperty(value) }
                return this
            }

            fun enableImageBasedLighting(iblMaps: EnvironmentMaps): Builder {
                imageBasedAmbientColor(iblMaps.irradianceMap)
                reflectionMap = iblMaps.reflectionMap
                return this
            }

            override fun build(): Config {
                return Config(this)
            }
        }
    }

    class Model(cfg: Config) : LitShaderModel<Config>("PBR Shader") {
        init {
            createModel(cfg)
        }

        override fun KslScopeBuilder.createMaterial(
            cfg: Config,
            camData: CameraData,
            irradiance: KslExprFloat3,
            lightData: SceneLightData,
            shadowFactors: KslExprFloat1Array,
            aoFactor: KslExprFloat1,
            normal: KslExprFloat3,
            fragmentWorldPos: KslExprFloat3,
            baseColor: KslExprFloat4,
            emissionColor: KslExprFloat4
        ): KslExprFloat4 {

            val roughness = fragmentPropertyBlock(cfg.roughnessCfg).outProperty
            val metallic = fragmentPropertyBlock(cfg.metallicCfg).outProperty

            val ambientOri = uniformMat3("uAmbientTextureOri")
            val brdfLut = texture2d("tBrdfLut")
            val reflectionStrength = uniformFloat4("uReflectionStrength").rgb
            val reflectionMaps = if (cfg.isTextureReflection) {
                textureArrayCube("tReflectionMaps", 2).value
            } else {
                null
            }

            val material = pbrMaterialBlock(cfg.maxNumberOfLights, reflectionMaps, brdfLut) {
                inCamPos(camData.position)
                inNormal(normal)
                inFragmentPos(fragmentWorldPos)
                inBaseColor(baseColor)

                inRoughness(roughness)
                inMetallic(metallic)

                inIrradiance(irradiance)
                inAoFactor(aoFactor)
                inAmbientOrientation(ambientOri)

                inReflectionMapWeights(uniformFloat2("uReflectionWeights"))
                inReflectionStrength(reflectionStrength)

                setLightData(lightData, shadowFactors, cfg.lightStrength.const)
            }
            return float4Value(material.outColor + emissionColor.rgb, baseColor.a)
        }
    }
}

fun KslPbrShaderConfig(block: KslPbrShader.Config.Builder.() -> Unit): KslPbrShader.Config {
    val builder = KslPbrShader.Config.Builder()
    builder.block()
    return builder.build()
}
