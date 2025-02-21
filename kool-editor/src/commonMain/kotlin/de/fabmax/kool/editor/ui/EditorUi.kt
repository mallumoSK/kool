package de.fabmax.kool.editor.ui

import de.fabmax.kool.Assets
import de.fabmax.kool.editor.KoolEditor
import de.fabmax.kool.math.Vec2d
import de.fabmax.kool.math.Vec3d
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockLayout
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.*
import kotlinx.serialization.json.Json

class EditorUi(val editor: KoolEditor) : Scene("EditorMenu") {

    val uiColors = mutableStateOf(EDITOR_THEME_COLORS)
    val uiSizes = mutableStateOf(Sizes.medium)

    val uiFont = mutableStateOf(MsdfFont.DEFAULT_FONT)
    val consoleFont = mutableStateOf(MsdfFont.DEFAULT_FONT)

    val dock = Dock()
    val statusBar = PanelSurface(colors = EDITOR_THEME_COLORS) {
        surface.colors = uiColors.use()
        surface.sizes = uiSizes.use()

        modifier
            .alignY(AlignmentY.Bottom)
            .size(Grow.Std, sizes.statusBarHeight)
            .backgroundColor(colors.backgroundMid)

        Column(width = Grow.Std, height = Grow.Std) {
            divider(UiColors.titleBg, horizontalMargin = Dp.ZERO)
            statusBar()
        }
    }

    val sceneView = SceneView(this)
    val sceneBrowser = SceneBrowser(this)
    val objectProperties = ObjectPropertyEditor(this)
    val assetBrowser = AssetBrowser(this)
    val materialBrowser = MaterialBrowser(this)
    val behaviorBrowser = BehaviorBrowser(this)
    val console = ConsolePanel(this)

    val appStateInfo = mutableStateOf("")

    val dndController = DndController(this)

    init {
        launchOnMainThread {
            val uiTex = Assets.loadTexture2d("assets/fonts/gidole/font-gidole-regular.png", MsdfFont.MSDF_TEX_PROPS)
            val uiMeta = Assets.loadBlobAsset("assets/fonts/gidole/font-gidole-regular.json")
            val uiFont = MsdfFont(MsdfFontData(uiTex, Json.decodeFromString(uiMeta.toArray().decodeToString())))
            this@EditorUi.uiFont.set(uiFont)

            val sz = uiSizes.value
            uiSizes.set(sz.copy(
                normalText = uiFont.copy(sizePts = sz.normalText.sizePts),
                largeText = uiFont.copy(sizePts = sz.largeText.sizePts),
            ))

            val consoleTex = Assets.loadTexture2d("assets/fonts/hack/font-hack-regular.png", MsdfFont.MSDF_TEX_PROPS)
            val consoleMeta = Assets.loadBlobAsset("assets/fonts/hack/font-hack-regular.json")
            val consoleFont = MsdfFont(MsdfFontData(consoleTex, Json.decodeFromString(consoleMeta.toArray().decodeToString())))
            this@EditorUi.consoleFont.set(consoleFont)
        }

        setupUiScene()

        addNode(statusBar)

        dock.apply {
            borderWidth.set(Dp.fromPx(1f))
            borderColor.set(UiColors.titleBg)
            dockingSurface.colors = EDITOR_THEME_COLORS
            dockingPaneComposable = Composable {
                Column(Grow.Std, Grow.Std) {
                    modifier.margin(bottom = sizes.statusBarHeight)
                    root()
                }
            }

            addDockableSurface(sceneView.windowDockable, sceneView.windowSurface)
            addDockableSurface(sceneBrowser.windowDockable, sceneBrowser.windowSurface)
            addDockableSurface(objectProperties.windowDockable, objectProperties.windowSurface)
            addDockableSurface(assetBrowser.windowDockable, assetBrowser.windowSurface)
            addDockableSurface(materialBrowser.windowDockable, materialBrowser.windowSurface)
            addDockableSurface(behaviorBrowser.windowDockable, behaviorBrowser.windowSurface)
            addDockableSurface(console.windowDockable, console.windowSurface)

            val restoredLayout = DockLayout.loadLayout("editor.ui.layout", this) { windowName ->
                when (windowName) {
                    sceneView.name -> sceneView.windowDockable
                    sceneBrowser.name -> sceneBrowser.windowDockable
                    objectProperties.name -> objectProperties.windowDockable
                    assetBrowser.name -> assetBrowser.windowDockable
                    materialBrowser.name -> materialBrowser.windowDockable
                    behaviorBrowser.name -> behaviorBrowser.windowDockable
                    console.name -> console.windowDockable
                    else -> {
                        logW { "Unable to restore layout - window not found: $windowName" }
                        null
                    }
                }
            }

            if (!restoredLayout) {
                logI { "Setting default window layout" }
                createNodeLayout(
                    listOf(
                        "0:row",
                        "0/0:col",
                        "0/0/0:row",
                        "0/0/0/0:leaf",
                        "0/0/0/1:leaf",
                        "0/0/1:leaf",
                        "0/1:leaf",
                    )
                )
                getLeafAtPath("0/0/0/0")?.dock(sceneBrowser.windowDockable)
                getLeafAtPath("0/0/0/1")?.dock(sceneView.windowDockable)
                getLeafAtPath("0/1")?.dock(objectProperties.windowDockable)
                getLeafAtPath("0/0/1")?.dock(assetBrowser.windowDockable)
                getLeafAtPath("0/0/1")?.dock(materialBrowser.windowDockable)
                getLeafAtPath("0/0/1")?.dock(behaviorBrowser.windowDockable)
                getLeafAtPath("0/0/1")?.dock(console.windowDockable)

                getLeafAtPath("0/0/1")?.bringToTop(assetBrowser.windowDockable)
            }
        }

        addNode(dock)
    }

    private fun UiScope.statusBar() = Row(width = Grow.Std, height = Grow.Std) {
        Box(width = Grow.Std) {  }

        divider(colors.strongDividerColor, marginStart = sizes.gap, marginEnd = sizes.gap)

        Box(width = sizes.baseSize * 6f, height = Grow.Std) {
            Text(appStateInfo.use()) {
                modifier.alignY(AlignmentY.Center)
            }
        }
    }

    companion object {
        private val EDITOR_THEME_COLORS = Colors.darkColors(
            background = Color("232933ff"),
            backgroundVariant = Color("161a20ff"),
            onBackground = Color("dbe6ffff"),
            secondary = Color("7786a5ff"),
            secondaryVariant = Color("4d566bff"),
            onSecondary = Color.WHITE
        )
    }

    private val Sizes.statusBarHeight: Dp get() = lineHeightLarger
}


val Sizes.baseSize: Dp get() = largeGap * 2f
val Sizes.treeIndentation: Dp get() = gap * 1.5f
val Sizes.lineHeight: Dp get() = baseSize * (2f/3f)
val Sizes.lineHeightLarger: Dp get() = baseSize * 0.9f
val Sizes.lineHeightTitle: Dp get() = baseSize
val Sizes.smallTextFieldPadding: Dp get() = smallGap * 0.75f

val Sizes.boldText: MsdfFont get() = (normalText as MsdfFont).copy(weight = 0.075f)
val Sizes.italicText: MsdfFont get() = (normalText as MsdfFont).copy(italic = MsdfFont.ITALIC_STD)

// weak hovered background: hovered list items, hovered collapsable panel header
val Colors.hoverBg: Color get() = secondaryVariantAlpha(0.35f)

val Colors.weakComponentBg: Color get() = secondaryAlpha(0.15f)
val Colors.weakComponentBgHovered: Color get() = secondaryAlpha(0.25f)

// text fields, combo-boxes, right side slider track
val Colors.componentBg: Color get() = secondaryAlpha(0.25f)
// focused text field, hovered combo-boxes
val Colors.componentBgHovered: Color get() = secondaryAlpha(0.5f)

// buttons, combo-box expander, left side slider track
val Colors.elevatedComponentBg: Color get() = secondaryVariant
// hovered buttons / cb expander
val Colors.elevatedComponentBgHovered: Color get() = secondary
val Colors.elevatedComponentClickFeedback: Color get() = UiColors.secondaryBright

val Colors.dndAcceptableBg: Color get() = MdColor.GREEN.withAlpha(0.3f)
val Colors.dndAcceptableBgHovered: Color get() = MdColor.GREEN.withAlpha(0.5f)

val Colors.backgroundMid: Color get() = background.mix(backgroundVariant, 0.5f)

val Colors.weakDividerColor: Color get() = secondaryVariantAlpha(0.75f)
val Colors.strongDividerColor: Color get() = secondaryAlpha(0.75f)

object UiColors {
    val border = Color("0f1114ff")
    val titleBg = Color("343a49ff")
    val titleText = Color("dbe6ffff")
    val secondaryBright = Color("a0b3d8ff")
}

object DragChangeRates {
    const val RANGE_0_TO_1 = 0.005
    const val POSITION = 0.01
    const val SCALE = 0.01
    const val ROTATION = 0.1
    const val SIZE = 0.01

    val SIZE_VEC2 = Vec2d(SIZE)
    val SIZE_VEC3 = Vec3d(SIZE)
    val POSITION_VEC3 = Vec3d(POSITION)
    val SCALE_VEC3 = Vec3d(SCALE)
    val ROTATION_VEC3 = Vec3d(ROTATION)
}
