package de.fabmax.kool.math

import de.fabmax.kool.scene.Node

class RayTest {
    val ray = RayF()

    val hitPositionGlobal = MutableVec3f()
    val hitNormalGlobal = MutableVec3f()

    private val tmpRay = RayF()
    private val tmpHitPoint = MutableVec3f()

    var maxDistance = Float.MAX_VALUE
    var hitNode: Node? = null
        private set
    var hitDistanceSqr = Float.MAX_VALUE
        private set
    val isHit: Boolean
        get() = hitDistanceSqr < Float.MAX_VALUE

    fun clear(maxDistance: Float = Float.MAX_VALUE) {
        this.maxDistance = maxDistance
        hitPositionGlobal.set(Vec3f.ZERO)
        hitNormalGlobal.set(Vec3f.ZERO)
        hitNode = null
        hitDistanceSqr = Float.MAX_VALUE
    }

    fun setHit(node: Node, hitDistanceGlobal: Float, hitNormalGlobal: Vec3f? = null) {
        if (hitDistanceGlobal < maxDistance) {
            hitPositionGlobal.set(ray.direction).mul(hitDistanceGlobal).add(ray.origin)
            setHit(node, hitPositionGlobal, hitNormalGlobal)
        }
    }

    fun setHit(node: Node, hitPositionGlobal: Vec3f, hitNormalGlobal: Vec3f? = null) {
        val dist = hitPositionGlobal.distance(ray.origin)
        if (dist < maxDistance) {
            this.hitPositionGlobal.set(hitPositionGlobal)
            this.hitNormalGlobal.set(hitNormalGlobal ?: Vec3f.ZERO)
            hitNode = node
            hitDistanceSqr = dist * dist
        }
    }

    /**
     * Returns true if this [ray] hits the [node]'s bounding sphere AND the hit is closer than any previous hit.
     */
    fun isIntersectingBoundingSphere(node: Node) = isIntersectingBoundingSphere(node.globalCenter, node.globalRadius)

    /**
     * Returns true if this [ray] hits the specified bounding sphere (in global coordinates) AND the hit is closer than
     * any previous hit.
     */
    fun isIntersectingBoundingSphere(globalCenter: Vec3f, globalRadius: Float): Boolean {
        val isSphereHit = ray.sphereIntersection(globalCenter, globalRadius, tmpHitPoint)
        return isSphereHit && tmpHitPoint.sqrDistance(ray.origin) <= hitDistanceSqr
    }

    fun getRayTransformed(matrix: Mat4f): RayF {
        return ray.transformBy(matrix, tmpRay)
    }

    fun getRayTransformed(matrix: Mat4d): RayF {
        return ray.transformBy(matrix, tmpRay)
    }
}