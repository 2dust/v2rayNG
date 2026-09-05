# Compose 规范 · 目录、命名与结构

适用环境：Compose BOM `2026.08.00`，Material3 `1.5.0-alpha26`（显式覆盖 BOM），
Kotlin 2.4.10 + `org.jetbrains.kotlin.plugin.compose`。

## 1. 目录

```
ui/base/       屏幕基座（BaseScreen / BaseActivity / BaseContract / …），不可随意扩充
ui/compose/    跨屏共享组件与主题
  Theme.kt        AppTheme、色板、AppSemanticColors、LocalAppColors、ThemeManager
  Components.kt   AppTopBar、AppListItem、ItemDivider、VersionInfoBlock、拖拽包装
  Dialog.kt       ConfirmDialog、DeleteConfirmDialog、InputDialog、QRCodeDialog、SelectListDialog
  FormFields.kt   FormTextField、FormDropdownField、appFieldColors、StringOptions
  SettingsItem.kt PreferenceGroupHeader、SettingsSwitchItem/ListItem/EditItem/MenuItem
  Menu.kt         AppDropdownMenuItems
  Scrollbar.kt    verticalScrollbar / horizontalScrollbar（M3 nonInteractiveScrollbar 封装）
  SnackBar.kt     AppSnackbarController / AppSnackbarHost / AppSnackbarManager / ToastType
ui/<feature>/  该屏专属
```

**放 `ui/compose/` 的门槛：被两个及以上 feature 使用。** 只有一个 feature 用的组件留在该
feature 目录，宁可以后再上提，不要预先泛化。

## 2. 文件与函数命名

| 文件 | 内容 |
| --- | --- |
| `XxxContract.kt` | `XxxUiState`、`XxxAction`、`XxxEvent`，以及该屏专用的 `@Immutable` 小模型 |
| `XxxScreen.kt` | `XxxScreen(viewModel, …)` 根 Composable + 少量私有子 Composable |
| `XxxViewModel.kt` | 一个类 |
| `XxxActivity.kt` | 一个类 |
| `XxxDialogs.kt` | `XxxDialog` 密封接口 + `XxxDialogs(...)` 宿主 |
| `XxxTopBar.kt` / `XxxBottomBar.kt` | 复杂栏区 |
| `XxxSections.kt` / `XxxRuleList.kt` 等 | 大表单/大列表的分块 |
| `XxxStateHolders.kt` | `@Stable` 的句柄类（切片访问器、滚动状态、Dialog 宿主） |

- Composable 函数 `PascalCase`，返回 `Unit`。返回值的（`rememberXxx`）用 `camelCase` + `remember` 前缀。
- 私有 Composable 一律 `private`；跨文件同 feature 用 `internal`。
- 单文件超过约 600 行就按上表拆分。`MainScreen`（10KB）+ `MainServerPager`（12KB）
  + `MainTopBar` / `MainBottomBar` / `MainDrawer` / `MainGroupTab` / `MainDialogs`
  / `MainImportMenu` / `MainStateHolders` 是标准拆法。

## 3. Composable 参数契约

顺序固定：

```kotlin
@Composable
fun ServerRow(
    item: ServerRowItem,          // 1. 必填数据
    isSelected: Boolean,
    callbacks: ServerRowCallbacks,// 2. 必填回调（多于 3 个就打包成 @Stable class）
    modifier: Modifier = Modifier,// 3. modifier，第一个可选参数
    showBadge: Boolean = true,    // 4. 其余可选参数
)
```

- 每个可复用组件必须有 `modifier: Modifier = Modifier`，且作用在**最外层**节点上。
- 组件内部不得给自己加外边距；间距由调用方通过 modifier 决定。
- 回调命名 `onXxx`；语义化的用 `onSelect` / `onEdit`，通用的用 `onClick`。
- 不传 `ViewModel` 给任何非根 Composable。根 Composable 收 `viewModel`，
  其余全部 `(state, onAction)` 或更窄的参数。

## 4. 屏幕脚手架

根 Composable 的固定骨架：

```kotlin
@Composable
fun XxxScreen(viewModel: XxxViewModel) {
    BaseScreen(
        viewModel = viewModel,
        topBar = { AppTopBar(title = stringResource(R.string.title_xxx), onBack = …) },
        onEvent = { event -> /* 只处理本屏专属 Event，返回 true 表示已消费 */ false },
        onResult = { result -> viewModel.onAction(XxxAction.ResultReceived(result)) },
    ) { state, onAction ->
        XxxContent(state = state, onAction = onAction)
    }
}
```

- `BaseScreen` 是**唯一**允许收集 `uiState` 的地方。
- 需要在 topBar/bottomBar 里用状态时，**在槽位内部单独收窄订阅**，
  不要把整个 `state` 从 content lambda 传出去（详见 `performance.md` §2）。
- 不需要全屏 loading 遮罩的屏传 `showLoading = false`，然后在需要的槽位里自己收
  `viewModel.isLoading`（`MainScreen` 的做法）。
- 有抽屉/侧栏时把 `BaseScreen` 包在 `ModalNavigationDrawer` 里，不要反过来。
- **insets 职责**：`BaseScreen` 已固定 `Scaffold.contentWindowInsets = WindowInsets(0)`
  （inset0），所以**内容不会被自动内边距保护**。每屏负责自己的底部 inset，
  统一使用 `ui/compose/compoents.kt` 提供的 `NavigationBarsSpacer()` /
  `NavigationBarsBottomPadding()`，不得内联固定 dp 硬编码导航栏高度。

## 5. Content 层

- `XxxContent(state, onAction)` 必须是**无状态**的：能被 Preview 直接调用。
- 允许的局部状态只有纯 UI 的：展开/折叠、滚动位置、输入焦点、当前弹窗。
  凡是需要在进程重建后保留的，用 `rememberSaveable`。
- 业务数据一律来自 `state`。

## 6. 共享组件用法

| 需求 | 用什么 |
| --- | --- |
| 顶栏（含搜索、进度、返回） | `AppTopBar` + `AppSearchState` |
| 确认/删除确认 | `ConfirmDialog` / `DeleteConfirmDialog` |
| 单选列表弹窗 | `SelectListDialog<T>`（带 `optionKey` / `contentType`） |
| 文本输入弹窗 | `InputDialog` |
| 二维码展示 | `QRCodeDialog` |
| 下拉菜单项 | `AppDropdownMenuItems(items, labelRes, onSelected, enabled)` |
| 设置项 | `SettingsSwitchItem` / `SettingsListItem` / `SettingsEditItem` / `SettingsMenuItem` |
| 分组标题 | `PreferenceGroupHeader` / `CollapsiblePreferenceGroupHeader` |
| 分割线 | `ItemDivider` / `AppDivider` |
| 滚动条 | `Modifier.verticalScrollbar(state)` |
| 拖拽排序 | `ReorderableListItem` / `ReorderableGridItem` + `Modifier.dragHandle()` |
| 提示 | 走 `BaseEvent.Message`；非 UI 层才用 `AppSnackbarManager.show` |

要新增共享组件时，先确认上表里没有能改造复用的。

## 7. 禁止事项

- 禁止 `AndroidView` 包 `RecyclerView` / `ViewPager` / 旧控件。
  唯一在用的 `AndroidView` 是 `AboutScreen` 里的 `WebView`（渲染 OSS 许可证），
  它已关闭 JavaScript 与文件访问；新增 `AndroidView` 需要在 PR 里说明为什么无法 Compose 化。
- 禁止 `LocalContext.current as XxxActivity`；用 `LocalPlatformActions`。
- 禁止在 Composable 里读写 MMKV、调 `handler/`、起网络请求。
- 禁止 `GlobalScope`、`runBlocking`。
- 禁止新增 XML 业务布局。
