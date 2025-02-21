package de.fabmax.kool.modules.gltf

import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.ibl.EnvironmentMaps
import de.fabmax.kool.util.ShadowMap

class GltfLoadConfig(
    val generateNormals: Boolean = false,
    val applyMaterials: Boolean = true,
    val materialConfig: GltfMaterialConfig = GltfMaterialConfig(),
    val setVertexAttribsFromMaterial: Boolean = false,
    val loadAnimations: Boolean = true,
    val applySkins: Boolean = true,
    val applyMorphTargets: Boolean = true,
    val applyTransforms: Boolean = false,
    val removeEmptyNodes: Boolean = true,
    val mergeMeshesByMaterial: Boolean = false,
    val sortNodesByAlpha: Boolean = true,
    val addInstanceAttributes: List<Attribute> = emptyList(),
    val pbrBlock: (KslPbrShader.Config.Builder.(GltfMesh.Primitive) -> Unit)? = null
)

class GltfMaterialConfig(
    val shadowMaps: List<ShadowMap> = emptyList(),
    val scrSpcAmbientOcclusionMap: Texture2d? = null,
    val environmentMaps: EnvironmentMaps? = null,
    val isDeferredShading: Boolean = false,
    val maxNumberOfLights: Int = 4,
    val maxNumberOfJoints: Int = 64
)
