package de.fabmax.kool.pipeline.backend.gl

import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.*


fun TexFormat.glInternalFormat(gl: GlApi): Int = when(this) {
    TexFormat.R -> gl.R8
    TexFormat.RG -> gl.RG8
    TexFormat.RGB -> gl.RGB8
    TexFormat.RGBA -> gl.RGBA8

    TexFormat.R_F16 -> gl.R16F
    TexFormat.RG_F16 -> gl.RG16F
    TexFormat.RGB_F16 -> gl.RGB16F
    TexFormat.RGBA_F16 -> gl.RGBA16F

    TexFormat.R_F32 -> gl.R32F
    TexFormat.RG_F32 -> gl.RG32F
    TexFormat.RGB_F32 -> gl.RGB32F
    TexFormat.RGBA_F32 -> gl.RGBA32F

    TexFormat.R_I32 -> gl.R32I
    TexFormat.RG_I32 -> gl.RG32I
    TexFormat.RGB_I32 -> gl.RGB32I
    TexFormat.RGBA_I32 -> gl.RGBA32I

    TexFormat.R_U32 -> gl.R32UI
    TexFormat.RG_U32 -> gl.RG32UI
    TexFormat.RGB_U32 -> gl.RGB32UI
    TexFormat.RGBA_U32 -> gl.RGBA32UI
}

fun TexFormat.glType(gl: GlApi): Int = when(this) {
    TexFormat.R -> gl.UNSIGNED_BYTE
    TexFormat.RG -> gl.UNSIGNED_BYTE
    TexFormat.RGB -> gl.UNSIGNED_BYTE
    TexFormat.RGBA -> gl.UNSIGNED_BYTE

    TexFormat.R_F16 -> gl.FLOAT
    TexFormat.RG_F16 -> gl.FLOAT
    TexFormat.RGB_F16 -> gl.FLOAT
    TexFormat.RGBA_F16 -> gl.FLOAT

    TexFormat.R_F32 -> gl.FLOAT
    TexFormat.RG_F32 -> gl.FLOAT
    TexFormat.RGB_F32 -> gl.FLOAT
    TexFormat.RGBA_F32 -> gl.FLOAT

    TexFormat.R_I32 -> gl.INT
    TexFormat.RG_I32 -> gl.INT
    TexFormat.RGB_I32 -> gl.INT
    TexFormat.RGBA_I32 -> gl.INT

    TexFormat.R_U32 -> gl.UNSIGNED_INT
    TexFormat.RG_U32 -> gl.UNSIGNED_INT
    TexFormat.RGB_U32 -> gl.UNSIGNED_INT
    TexFormat.RGBA_U32 -> gl.UNSIGNED_INT
}

fun TexFormat.glFormat(gl: GlApi): Int = when(this) {
    TexFormat.R -> gl.RED
    TexFormat.RG -> gl.RG
    TexFormat.RGB -> gl.RGB
    TexFormat.RGBA -> gl.RGBA

    TexFormat.R_F16 -> gl.RED
    TexFormat.RG_F16 -> gl.RG
    TexFormat.RGB_F16 -> gl.RGB
    TexFormat.RGBA_F16 -> gl.RGBA

    TexFormat.R_F32 -> gl.RED
    TexFormat.RG_F32 -> gl.RG
    TexFormat.RGB_F32 -> gl.RGB
    TexFormat.RGBA_F32 -> gl.RGBA

    TexFormat.R_I32 -> gl.RED_INTEGER
    TexFormat.RG_I32 -> gl.RG_INTEGER
    TexFormat.RGB_I32 -> gl.RGB_INTEGER
    TexFormat.RGBA_I32 -> gl.RGBA_INTEGER

    TexFormat.R_U32 -> gl.RED_INTEGER
    TexFormat.RG_U32 -> gl.RG_INTEGER
    TexFormat.RGB_U32 -> gl.RGB_INTEGER
    TexFormat.RGBA_U32 -> gl.RGBA_INTEGER
}

val TexFormat.pxSize: Int get() = when(this) {
    TexFormat.R -> 1
    TexFormat.RG -> 2
    TexFormat.RGB -> 3
    TexFormat.RGBA -> 4

    TexFormat.R_F16 -> 2
    TexFormat.RG_F16 -> 4
    TexFormat.RGB_F16 -> 6
    TexFormat.RGBA_F16 -> 8

    TexFormat.R_F32 -> 4
    TexFormat.RG_F32 -> 8
    TexFormat.RGB_F32 -> 12
    TexFormat.RGBA_F32 -> 16

    TexFormat.R_I32 -> 4
    TexFormat.RG_I32 -> 8
    TexFormat.RGB_I32 -> 12
    TexFormat.RGBA_I32 -> 16

    TexFormat.R_U32 -> 4
    TexFormat.RG_U32 -> 8
    TexFormat.RGB_U32 -> 12
    TexFormat.RGBA_U32 -> 16
}

fun DepthCompareOp.glOp(gl: GlApi): Int = when(this) {
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

fun StorageAccessType.glAccessType(gl: GlApi): Int = when(this) {
    StorageAccessType.READ_ONLY -> gl.READ_ONLY
    StorageAccessType.WRITE_ONLY -> gl.WRITE_ONLY
    StorageAccessType.READ_WRITE -> gl.READ_WRITE
}

fun KslNumericType.glFormat(gl: GlApi): Int = when(this) {
    is KslFloat1 -> gl.R32F
    is KslFloat2 -> gl.RG32F
    is KslFloat3 -> gl.RGB32F
    is KslFloat4 -> gl.RGBA32F
    is KslInt1 -> gl.R32I
    is KslInt2 -> gl.RG32I
    is KslInt3 -> gl.RGB32I
    is KslInt4 -> gl.RGBA32I
    is KslUint1 -> gl.R32UI
    is KslUint2 -> gl.RG32UI
    is KslUint3 -> gl.RGB32UI
    is KslUint4 -> gl.RGBA32UI
    else -> throw IllegalStateException("Invalid format type $this")
}

val VertexLayout.VertexAttribute.locationSize: Int get() = when(attribute.type) {
    GpuType.MAT2 -> 2
    GpuType.MAT3 -> 3
    GpuType.MAT4 -> 4
    else -> 1
}

fun VertexLayout.getAttribLocations() = buildMap {
    bindings
        .flatMap { it.vertexAttributes }
        .sortedBy { it.index }
        .fold(0) { pos, attr ->
            put(attr, pos)
            pos + attr.locationSize
        }
}