# Compose 规范 · 主题、尺寸与资源

## 1. 颜色

只有两个来源：

1. `MaterialTheme.colorScheme.*` —— M3 语义色，`Theme.kt` 里已定义完整的
   `LightColor` / `DarkColor`（含 `surfaceContainer*` 全系列）。
2. `LocalAppColors.current` —— `AppSemanticColors`（`pingBad`、`fabInactive`、`divider`、
   `toastBackground/Success/Error/Info/Content`），随深浅色自动切换。

规则：

- **Composable 里禁止出现 `Color(0xFF…)` 字面量。** 需要新语义色时加进 `AppSemanticColors`
  并同时补 `LightSemanticColors` / `DarkSemanticColors`。
- 顶层 `val colorPing` / `colorConfigType` / `colorFabActive` / `colorOnCameraPreview`
  是不随主题变化的品牌色/覆盖层色，只在既有位置沿用，不要扩充这一组。
- 需要知道当前是否深色时用 `LocalDarkTheme.current`，不要重复调 `isSystemInDarkTheme()`
  （那会忽略用户在设置里选的 Light/Dark 覆盖）。
- 主题切换：`ThemeManager.setMode(AppThemeMode)`，它同时写 `ThemeRepository` 并更新
  `MutableStateFlow`；`resolveDarkTheme()` 负责三态解析。不要自己读 pref 判断深浅色。

## 2. 尺寸：不建立全局 dimens

**本项目不设 `Dimens` / `Spacing` / `AppTheme.dimens` 这类全局尺寸令牌对象。**
理由：v2rayNG 只有一套手机端布局，没有多端/多密度主题化需求，全局令牌只会让每个尺寸
多一次跳转、并诱导出"为了统一而统一"的错误复用。

正确做法——**文件内私有常量**，命名表达用途：

```kotlin
private val ItemPad = 16.dp
private val GroupHeaderTopPad = 16.dp
private val IconSize = 24.dp
private const val DisabledAlpha = 0.38f
```

- 只在本文件用 → `private val` 放文件顶部。
- 同 feature 多文件用 → `internal val` 放该 feature 的一个文件里。
- 跨 feature 用 → 才放 `ui/compose/` 对应组件文件里（如 `Components.kt` 的
  `AppIconSize`、`ItemHorizontalPad`）。
- 数值不得内联在布局代码中间（`padding(16.dp)` 只在一次性、无语义的场合允许）。
- 触控目标最小 48.dp，见 `accessibility.md`。

## 3. 字体

只用 `MaterialTheme.typography.*`。需要变体时 `.copy(fontWeight = …)`，
不要自定义 `TextStyle` 常量池。等宽场景（日志、JSON 编辑）用
`fontFamily = FontFamily.Monospace`。

## 4. 字符串

- 全部经 `stringResource(R.string.xxx)`；带参数用 `stringResource(id, arg)`。
- 复数用 `pluralStringResource`。
- ViewModel 侧用 `BaseText`（见 `state-events.md` §4）。
- 字符串数组用 `rememberStringOptions(@ArrayRes id)`（内部基于 `LocalResources`，
  语言切换会自动失效重取），不要自己 `LocalContext.current.resources.getStringArray`。
- 新增文案只改 `res/values/strings.xml`；其他语言由翻译流程补，不要手填机翻。

## 5. 图标与图片

- 矢量图标放 `res/drawable/ic_*_24dp.xml`，用
  `painterResource(R.drawable.ic_xxx_24dp)`。
- **不要引入 `material-icons-extended`**（体积大且与现有图标风格不一致）。
- 应用图标（分应用代理）用 Coil + `AppIconFetcher`，必须给固定尺寸
  （`AppIconSize = 40.dp`）与 `placeholder`，避免列表滚动时布局跳动。
- Bitmap（二维码）只在弹窗里展示，用完即随弹窗销毁，不缓存进 State。

## 6. 形状与高度

- 圆角用 `MaterialTheme.shapes.*`，特例才 `RoundedCornerShape(x.dp)`
  （如 `ToastCornerRadius = 24.dp`）。
- 阴影克制：列表项拖拽时 `DragElevation = 4.dp`，Snackbar `shadowElevation = 0.dp`。
- 深色下不要用 `elevation` 表达层级，用 `surfaceContainer*` 色阶。

## 7. 动画

- 只用于状态转换的即时反馈（拖拽抬起、展开折叠、Toast 淡入淡出）。
- 用 `animateFloatAsState` / `animateDpAsState` / `AnimatedVisibility`，
  时长走 Material 默认，不要自定 spec 除非有明确理由。
- 列表项**不要**加 `animateItem()` 之外的入场动画；大列表逐项动画会显著掉帧。
- 拖拽反馈用 `HapticFeedback`（`Modifier.dragHandle()` 已封装）。

## 8. Edge-to-edge 与 insets

- `BaseActivity` 已 `enableEdgeToEdge()`，状态栏/导航栏图标颜色由 `AppTheme` 里的
  `WindowCompat.getInsetsController` 跟随深浅色。
- 内容内边距由 `Scaffold` 的 `innerPadding` 提供（`BaseScreen` 已应用）。
- 需要自己处理时用 `Modifier.windowInsetsPadding(WindowInsets.navigationBars)`
  （`AppSnackbarHost` 的做法），不要用固定 dp 猜导航栏高度。
- **inset0 约定**：`BaseScreen` 的 `Scaffold` 使用
  `contentWindowInsets = WindowInsets(0)`，即**不自动吸收系统栏 inset**。
  因此每一屏都必须自行处理底部导航栏 inset：
  - 有底栏/悬浮内容的屏：底栏自身调用 `navigationBarsPadding()` /
    `windowInsetsPadding(WindowInsets.navigationBars)`（样板：`MainBottomBar`、
    `AppSnackbarHost`），此时滚动内容仍需额外留出与底栏的间距；
  - 无底栏的屏：在滚动容器 `contentPadding` 或列表末尾显式放置
    `NavigationBarsSpacer()` 或对整个容器使用 `NavigationBarsBottomPadding()`。
- 禁止用固定 dp（如 `padding(bottom = 90.dp)`）猜测导航栏高度，但是不包含用于适配手机圆角舒适度的content bottom高度；只通过
  `WindowInsets.navigationBars` 取真实高度。
