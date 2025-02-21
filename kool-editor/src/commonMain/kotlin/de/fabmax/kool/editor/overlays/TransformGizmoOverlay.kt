package de.fabmax.kool.editor.overlays

import de.fabmax.kool.KoolContext
import de.fabmax.kool.editor.EditorState
import de.fabmax.kool.editor.KoolEditor
import de.fabmax.kool.editor.actions.SetTransformAction
import de.fabmax.kool.editor.data.TransformData
import de.fabmax.kool.editor.data.Vec3Data
import de.fabmax.kool.editor.data.Vec4Data
import de.fabmax.kool.editor.model.SceneNodeModel
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.math.*
import de.fabmax.kool.scene.MatrixTransformF
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Transform
import de.fabmax.kool.util.Gizmo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class TransformGizmoOverlay(private val editor: KoolEditor) : Node("Transform gizmo") {

    var transformMode = TransformMode.MOVE
        set(value) {
            field = value
            applyTransformMode()
        }

    private val selection = mutableListOf<NodeTransformData>()
    private var hasTransformAuthority = false

    private val gizmo = Gizmo()
    private val globalGizmoPos = MutableVec3d()
    private val globalGizmoOrientation = MutableQuatF()
    private var gizmoScale = 1f
    private val gizmoToGlobal = MutableMat4d()

    private val gizmoListener = object : Gizmo.GizmoListener {
        override fun onDragAxis(axis: Vec3f, distance: Float, targetTransform: Transform, ctx: KoolContext) {
            if (transformMode == TransformMode.MOVE) {
                targetTransform.translate(axis.x * distance, axis.y * distance, axis.z * distance)
                val t = gizmoToGlobal.transform(MutableVec3d().set(axis).mul(distance.toDouble()), 0.0)
                translateSelection(t)

            } else if (transformMode == TransformMode.SCALE) {
                applyGizmoScale(axis, distance)
            }
        }

        override fun onDragPlane(
            planeNormal: Vec3f,
            dragPosition: Vec3f,
            targetTransform: Transform,
            ctx: KoolContext
        ) {
            targetTransform.translate(dragPosition)
            val t = gizmoToGlobal.transform(MutableVec3d().set(dragPosition), 0.0)
            translateSelection(t)
        }

        override fun onDragRotate(rotationAxis: Vec3f, angle: Float, targetTransform: Transform, ctx: KoolContext) {
            targetTransform.rotate(angle.deg, rotationAxis)
            val ax = gizmoToGlobal.transform(MutableVec3d().set(rotationAxis), 0.0)
            rotateSelection(ax, angle.toDouble())
        }

        override fun onDragStart(ctx: KoolContext) {
            captureSelectionTransform()
            hasTransformAuthority = true
        }

        override fun onDragFinished(ctx: KoolContext) {
            gizmo.properties.setAxesLengths(1f)
            gizmo.updateMesh()
            hasTransformAuthority = false
            applySelectionTransform(true)
        }
    }

    init {
        addNode(gizmo)
        hideGizmo()

        gizmo.gizmoListener = gizmoListener
        gizmo.onUpdate {
            if (selection.isNotEmpty() && !hasTransformAuthority) {
                updateGizmoTransformFromSelection()
            }
        }
    }

    private fun applyGizmoScale(axis: Vec3f, distance: Float) {
        val axX = abs(axis.x).toDouble()
        val axY = abs(axis.y).toDouble()
        val axZ = abs(axis.z).toDouble()

        when {
            isFuzzyEqual(1.0, axX) -> {
                gizmo.properties.axisLenX = 1f + distance / gizmoScale
                gizmo.properties.axisLenNegX = 1f + distance / gizmoScale
            }

            isFuzzyEqual(1.0, axY) -> {
                gizmo.properties.axisLenY = 1f + distance / gizmoScale
                gizmo.properties.axisLenNegY = 1f + distance / gizmoScale
            }

            isFuzzyEqual(1.0, axZ) -> {
                gizmo.properties.axisLenZ = 1f + distance / gizmoScale
                gizmo.properties.axisLenNegZ = 1f + distance / gizmoScale
            }
        }
        gizmo.updateMesh()

        val ori = gizmoToGlobal.transform(MutableVec3d())
        val base = gizmoToGlobal.transform(MutableVec3d().set(axis))
        val scaled = gizmoToGlobal.transform(
            MutableVec3d().set(axis)
                .mul(distance / gizmoScale.toDouble())
                .add(MutableVec3d().set(axis))
        )
        val f = ori.distance(scaled) / ori.distance(base)
        val s = MutableVec3d(axX, axY, axZ)
            .mul(f)
            .add(Vec3d(1.0 - axX, 1.0 - axY, 1.0 - axZ))
        scaleSelection(s, f)
    }

    private fun applyTransformMode() {
        when (transformMode) {
            TransformMode.MOVE -> {
                gizmo.properties.axesHandleShape = Gizmo.AxisHandleShape.ARROW
                gizmo.properties.setAxisHandlesEnabled(true)
                gizmo.properties.setPlaneHandlesEnabled(true)
                gizmo.properties.setRotationHandlesEnabled(false)
            }

            TransformMode.ROTATE -> {
                gizmo.properties.setAxisHandlesEnabled(false)
                gizmo.properties.setPlaneHandlesEnabled(false)
                gizmo.properties.setRotationHandlesEnabled(true)
            }

            TransformMode.SCALE -> {
                gizmo.properties.axesHandleShape = Gizmo.AxisHandleShape.SPHERE
                gizmo.properties.setAxisHandlesEnabled(true)
                gizmo.properties.setPlaneHandlesEnabled(false)
                gizmo.properties.setRotationHandlesEnabled(false)
            }
        }
        gizmo.updateMesh()
    }

    private fun captureSelectionTransform() {
        selection.forEach {
            it.updateTransform()
        }
    }

    private fun applySelectionTransform(withUndo: Boolean) {
        val transformNodes = mutableListOf<SceneNodeModel>()
        val undoTransforms = mutableListOf<TransformData>()
        val applyTransforms = mutableListOf<TransformData>()

        selection.forEach {
            transformNodes += it.nodeModel
            undoTransforms += TransformData(
                Vec3Data(it.startPosition),
                Vec4Data(it.startRotation),
                Vec3Data(it.startScale)
            )
            applyTransforms += TransformData(
                Vec3Data(it.dragPosition),
                Vec4Data(it.dragRotation),
                Vec3Data(it.dragScale)
            )
        }
        val action = SetTransformAction(transformNodes, undoTransforms, applyTransforms)
        if (withUndo) {
            action.apply()
        } else {
            action.doAction()
        }
    }

    private fun translateSelection(globalTranslation: Vec3d) {
        val t = MutableVec3d()
        selection.forEach { node ->
            val translationInParentFrame = node.globalToParent.transform(t.set(globalTranslation), 0.0)
            node.dragPosition.set(node.startPosition).add(translationInParentFrame)
        }
        applySelectionTransform(false)
    }

    private fun rotateSelection(globalAxis: Vec3d, angle: Double) {
        val m = MutableMat3d()
        val ax = MutableVec3d()
        val globalRot = MutableMat4d().rotate(angle.deg, globalAxis)
        selection.forEach { node ->
            val axisInParentFrame = node.globalToNode.transform(ax.set(globalAxis), 0.0)
            m.setIdentity().rotate(node.startRotation)
                .rotate(angle.deg, axisInParentFrame)
                .decompose(node.dragRotation)

            if (selection.size > 1) {
                // in case of multi selection, gizmo position is not the same as the node position -> node needs
                // to be translated as well
                val globalPos = MutableVec3d()
                node.nodeToGlobal.decompose(globalPos)
                globalPos.subtract(globalGizmoPos)
                globalRot.transform(globalPos)
                globalPos.add(globalGizmoPos)
                node.globalToParent.transform(globalPos, 1.0, node.dragPosition)
            }
        }

        applySelectionTransform(false)
    }

    private fun scaleSelection(scale: Vec3d, singleScale: Double) {
        val globalScale = MutableMat4d()
        if (!KeyboardInput.isAltDown) {
            globalScale.scale(singleScale)
        }

        selection.forEach { node ->
            if (node.nodeModel.transform.isFixedScaleRatio.value) {
                node.dragScale.set(node.startScale).mul(singleScale)
            } else {
                node.dragScale.set(node.startScale).mul(scale)
            }

            if (selection.size > 1) {
                // in case of multi selection, gizmo position is not the same as the node position -> node needs
                // to be translated as well
                val globalPos = MutableVec3d()
                node.nodeToGlobal.decompose(globalPos)
                globalPos.subtract(globalGizmoPos)
                globalScale.transform(globalPos)
                globalPos.add(globalGizmoPos)
                node.globalToParent.transform(globalPos, 1.0, node.dragPosition)
            }
        }
        applySelectionTransform(false)
    }

    private fun hideGizmo() {
        gizmo.isVisible = false
        editor.editorInputContext.pointerListeners -= gizmo
    }

    private fun showGizmo() {
        gizmo.isVisible = true
        if (gizmo !in KoolEditor.instance.editorInputContext.pointerListeners) {
            editor.editorInputContext.pointerListeners += gizmo
        }
    }

    fun setTransformObject(nodeModel: SceneNodeModel?) {
        if (nodeModel != null) {
            setTransformObjects(listOf(nodeModel))
        } else {
            setTransformObjects(emptyList())
        }
    }

    fun setTransformObjects(nodeModels: List<SceneNodeModel>) {
        selection.clear()
        nodeModels.forEach { selection += NodeTransformData(it) }
        if (selection.isNotEmpty()) {
            updateGizmoTransformFromSelection()
            showGizmo()
        } else {
            hideGizmo()
        }
    }

    private fun updateGizmoTransformFromSelection() {
        var parentOrientation = QuatD.IDENTITY
        var radius = 0f
        var isSameParent = true

        // determine gizmo position and size
        globalGizmoPos.set(Vec3d.ZERO)
        selection.forEach { globalGizmoPos.add(it.nodeModel.drawNode.globalCenter.toVec3d()) }
        globalGizmoPos.mul(1.0 / selection.size)

        selection.forEach {
            val d = it.nodeModel.drawNode.globalCenter.toVec3d().distance(globalGizmoPos).toFloat()
            radius = max(radius, it.nodeModel.drawNode.globalRadius + d)

            isSameParent = isSameParent && it.nodeModel.drawNode.parent == selection[0].nodeModel.drawNode.parent
            if (isSameParent) {
                val q = MutableQuatD()
                it.nodeModel.drawNode.parent?.modelMatD?.decompose(rotation = q)
                parentOrientation = q
            }
        }

        // determine gizmo orientation
        globalGizmoOrientation.set(QuatD.IDENTITY)
        if (EditorState.transformMode.value == EditorState.TransformOrientation.LOCAL) {
            if (selection.size == 1) {
                // use local orientation of single selected object
                selection[0].nodeModel.drawNode.modelMatF.decompose(rotation = globalGizmoOrientation)

            } else if (isSameParent) {
                // local orientation is undefined for multiple selected objects, use parent as fallback if all selected
                // objects have the same parent (or global orientation if not)
                globalGizmoOrientation.set(parentOrientation)
            }
        } else if (EditorState.transformMode.value == EditorState.TransformOrientation.PARENT && isSameParent) {
            // use parent orientation if selected objects all have the same parent
            globalGizmoOrientation.set(parentOrientation)
        }

        // apply gizmo transform
        gizmoScale = sqrt(radius) + 0.5f
        (gizmo.transform as MatrixTransformF).apply {
            matrixF.setIdentity().rotate(globalGizmoOrientation)
            matrixF.apply {
                m03 = globalGizmoPos.x.toFloat()
                m13 = globalGizmoPos.y.toFloat()
                m23 = globalGizmoPos.z.toFloat()
            }
            markDirty()
        }
        gizmo.setFixedScale(gizmoScale)
        gizmoToGlobal.set(gizmo.transform.matrixF)
    }

    private class NodeTransformData(val nodeModel: SceneNodeModel) {
        val nodeToGlobal = MutableMat4d()
        val globalToParent = MutableMat4d()
        val globalToNode = MutableMat4d()

        val startPosition = MutableVec3d()
        val startRotation = MutableQuatD()
        val startScale = MutableVec3d()

        val dragPosition = MutableVec3d()
        val dragRotation = MutableQuatD()
        val dragScale = MutableVec3d()

        init {
            updateTransform()
        }

        fun updateTransform() {
            nodeToGlobal.set(nodeModel.drawNode.modelMatD)
            nodeToGlobal.invert(globalToNode)
            globalToParent.setIdentity()
            nodeModel.drawNode.parent?.modelMatD?.invert(globalToParent)

            nodeModel.transform.transformState.value.position.toVec3d(startPosition)
            nodeModel.transform.transformState.value.rotation.toQuatD(startRotation)
            nodeModel.transform.transformState.value.scale.toVec3d(startScale)

            dragPosition.set(startPosition)
            dragRotation.set(startRotation)
            dragScale.set(startScale)
        }
    }

    enum class TransformMode {
        MOVE,
        ROTATE,
        SCALE
    }
}