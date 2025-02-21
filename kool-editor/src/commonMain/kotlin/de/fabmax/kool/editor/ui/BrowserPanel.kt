package de.fabmax.kool.editor.ui

import de.fabmax.kool.KeyValueStore
import de.fabmax.kool.editor.AppAssetType
import de.fabmax.kool.editor.AppBehavior
import de.fabmax.kool.editor.AssetItem
import de.fabmax.kool.editor.data.MaterialData
import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

abstract class BrowserPanel(name: String, icon: IconProvider, ui: EditorUi) :
    EditorPanel(name, icon, ui, defaultWidth = Dp(600f), defaultHeight = Dp(300f)
) {

    protected val browserItems = mutableMapOf<String, BrowserItem>()
    protected val expandedDirTree = mutableListOf<BrowserDir>()
    val selectedDirectory = mutableStateOf<BrowserDir?>(null)

    private val treePanelSize = mutableStateOf(Dp(275f))

    init {
        val treeW = KeyValueStore.getFloat("editor.ui.[$name].treeSize", 275f)
        treePanelSize.set(Dp(treeW))
    }

    override val windowSurface = editorPanelWithPanelBar {
        Column(Grow.Std, Grow.Std) {
            editorTitleBar(windowDockable, icon) { titleBar() }
            Row(Grow.Std, Grow.Std) {
                refreshBrowserItems()

                treeView()
                treeWidthHandle()
                directoryContentView()
            }
        }
    }

    protected open fun UiScope.titleBar() { }

    private fun UiScope.treeWidthHandle() = Row(height = Grow.Std) {
        var startDragWidth by remember(treePanelSize.value)
        modifier
            .onHover {  PointerInput.cursorShape = CursorShape.H_RESIZE }
            .onDragStart { startDragWidth = treePanelSize.value }
            .onDrag { treePanelSize.set(startDragWidth + Dp.fromPx(it.pointer.dragDeltaX.toFloat())) }
            .onDragEnd { KeyValueStore.setFloat("editor.ui.[$name].treeSize", treePanelSize.value.value) }

        Box(width = sizes.smallGap, height = Grow.Std) {
            modifier.backgroundColor(colors.background)
        }
        Box(width = sizes.smallGap, height = Grow.Std) {
            modifier.backgroundColor(colors.backgroundVariant)
        }
    }

    private fun UiScope.refreshBrowserItems() {
        val travPaths = mutableSetOf<String>()

        expandedDirTree.clear()
        collectBrowserDirs(travPaths)
        browserItems.keys.retainAll(travPaths)

        selectedDirectory.value?.path?.let { selectedDir ->
            if (selectedDir !in browserItems.keys) {
                selectDefaultDir()
            }
        }
    }

    private fun selectDefaultDir() {
        val defaultDir = browserItems.values
            .filterIsInstance<BrowserDir>()
            .firstOrNull { it.level == 0 }
        selectedDirectory.set(defaultDir)
    }

    protected abstract fun UiScope.collectBrowserDirs(traversedPaths: MutableSet<String>)

    protected abstract fun makeItemPopupMenu(item: BrowserItem, isTreeItem: Boolean): SubMenuItem<BrowserItem>?

    protected open fun onItemDoubleClick(item: BrowserItem) { }

    fun UiScope.treeView() = Box(width = treePanelSize.use(), height = Grow.Std) {
        if (selectedDirectory.value == null) {
            selectDefaultDir()
        }

        val dirPopupMenu = remember { ContextPopupMenu<BrowserItem>() }
        dirPopupMenu()

        LazyList(
            containerModifier = { it.backgroundColor(colors.background) }
        ) {
            var hoveredIndex by remember(-1)
            itemsIndexed(expandedDirTree) { i, dir ->
                Row(width = Grow.Std, height = sizes.lineHeight) {
                    modifier
                        .onEnter { hoveredIndex = i }
                        .onExit { hoveredIndex = -1 }
                        .onClick { evt ->
                            if (evt.pointer.isLeftButtonClicked) {
                                selectedDirectory.set(dir)
                                if (evt.pointer.leftButtonRepeatedClickCount == 2 && dir.level > 0) {
                                    dir.isExpanded.set(!dir.isExpanded.value)
                                }
                            } else if (evt.pointer.isRightButtonClicked) {
                                makeItemPopupMenu(dir, true)?.let {
                                    dirPopupMenu.show(evt.screenPosition, it, dir)
                                }
                            }
                        }
                        .margin(horizontal = sizes.smallGap)

                    if (i == 0) modifier.margin(top = sizes.smallGap)
                    if (hoveredIndex == i) modifier.background(RoundRectBackground(colors.hoverBg, sizes.smallGap))
                    val fgColor = if (dir == selectedDirectory.value) colors.primary else colors.onBackground

                    // tree-depth based indentation
                    Box(width = sizes.treeIndentation * dir.level) { }

                    // expand / collapse arrow
                    Box {
                        modifier
                            .size(sizes.lineHeight, FitContent)
                            .alignY(AlignmentY.Center)
                        if (dir.isExpandable.use()) {
                            Arrow {
                                modifier
                                    .rotation(if (dir.isExpanded.use()) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_RIGHT)
                                    .align(AlignmentX.Center, AlignmentY.Center)
                                    .onClick { dir.isExpanded.set(!dir.isExpanded.value) }
                                    .onEnter { hoveredIndex = i }
                                    .onExit { hoveredIndex = -1 }
                            }
                        }
                    }

                    // directory icon
                    Image {
                        val ico = if (dir.isExpanded.use()) IconMap.small.FOLDER_OPEN else IconMap.small.FOLDER
                        modifier
                            .alignY(AlignmentY.Center)
                            .margin(end = sizes.smallGap)
                            .iconImage(ico, fgColor)
                    }

                    // directory name
                    Text(dir.name) {
                        modifier
                            .alignY(AlignmentY.Center)
                            .width(Grow.Std)
                            .textColor(fgColor)
                    }
                }
            }
        }
    }

    private fun UiScope.directoryContentView() = Box(width = Grow.Std, height = Grow.Std) {
        var areaWidth by remember(0f)
        val gridSize = sizes.baseSize * 3f

        val dir = selectedDirectory.use()
        val dirItems = dir?.children ?: emptyList()

        val popupMenu = remember { ContextPopupMenu<BrowserItem>() }
        popupMenu()

        if (dir != null) {
            modifier.onClick { evt ->
                if (evt.pointer.isRightButtonClicked) {
                    makeItemPopupMenu(dir, false)?.let { popupMenu.show(evt.screenPosition, it, dir) }
                }
            }
        }

        ScrollArea(containerModifier = {
            it.onPositioned { nd ->
                areaWidth = nd.widthPx - sizes.largeGap.px
            }
        }) {
            modifier.margin(sizes.gap)
            if (areaWidth > 0f) {
                val cols = max(1, floor(areaWidth / gridSize.px).toInt())

                Column {
                    for (i in dirItems.indices step cols) {
                        Row {
                            for (j in i until min(i + cols, dirItems.size)) {
                                browserItem(dirItems[j], gridSize, popupMenu)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun UiScope.browserItem(
        item: BrowserItem,
        gridSize: Dp,
        itemPopupMenu: ContextPopupMenu<BrowserItem>
    ) = Column(width = gridSize) {
        modifier.installDragAndDropHandler(dndCtx, null) { item.makeDndItem() }

        val color = when (item) {
            is BrowserDir -> MdColor.AMBER
            is BrowserAssetItem -> item.itemColor
            is BrowserMaterialItem -> MdColor.GREY
            is BrowserBehaviorItem -> MdColor.PURPLE
        }

        var isHovered by remember(false)
        if (isHovered) {
            modifier.background(RoundRectBackground(color.withAlpha(0.25f), sizes.smallGap))
        }

        modifier
            .onClick { evt ->
                if (evt.pointer.isLeftButtonClicked) {
                    if (evt.pointer.leftButtonRepeatedClickCount == 2) {
                        if (item is BrowserDir) {
                            selectedDirectory.value?.isExpanded?.set(true)
                            selectedDirectory.set(item)
                        } else {
                            onItemDoubleClick(item)
                        }
                    }
                } else if (evt.pointer.isRightButtonClicked) {
                    makeItemPopupMenu(item, false)?.let { itemPopupMenu.show(evt.screenPosition, it, item) }
                }
            }

        Box {
            modifier
                .size(sizes.baseSize * 2, sizes.baseSize * 2)
                .alignX(AlignmentX.Center)
                .margin(sizes.smallGap)
                .background(RoundRectBackground(color, sizes.gap))
                .onEnter { isHovered = true }
                .onExit { isHovered = false }
        }

        Text(item.name) {
            modifier
                .width(Grow.Std)
                .isWrapText(true)
                .textAlignX(AlignmentX.Center)
                .margin(sizes.smallGap)
        }
    }

    sealed class BrowserItem(val level: Int, val name: String, val path: String) {
        fun makeDndItem(): EditorDndItem<*>? {
            return when (this) {
                is BrowserDir -> DndItemFlavor.BROWSER_ITEM.itemOf(this)
                is BrowserAssetItem -> {
                    when (this.asset.type) {
                        AppAssetType.Unknown -> DndItemFlavor.BROWSER_ITEM.itemOf(this)
                        AppAssetType.Directory -> DndItemFlavor.BROWSER_ITEM.itemOf(this)
                        AppAssetType.Texture -> DndItemFlavor.BROWSER_ITEM_TEXTURE.itemOf(this)
                        AppAssetType.Model -> DndItemFlavor.BROWSER_ITEM_MODEL.itemOf(this)
                    }
                }
                is BrowserMaterialItem -> null
                is BrowserBehaviorItem -> null
            }
        }
    }

    class BrowserDir(level: Int, name: String, path: String) : BrowserItem(level, name, path) {
        val isExpanded = mutableStateOf(level == 0)
        val isExpandable = mutableStateOf(false)
        val children = mutableListOf<BrowserItem>()
    }

    class BrowserAssetItem(level: Int, val asset: AssetItem) : BrowserItem(level, asset.name, asset.path) {
        val itemColor: Color = when (asset.type) {
            AppAssetType.Unknown -> MdColor.PINK
            AppAssetType.Directory -> MdColor.AMBER
            AppAssetType.Texture -> MdColor.LIGHT_GREEN
            AppAssetType.Model -> MdColor.LIGHT_BLUE
        }
    }

    class BrowserMaterialItem(level: Int, val material: MaterialData) : BrowserItem(level, material.name, "/materials/${material.name}")

    class BrowserBehaviorItem(level: Int, val behavior: AppBehavior) : BrowserItem(level, behavior.prettyName, "/paths/${behavior.qualifiedName}")
}