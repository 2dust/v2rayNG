# Code Review Checklist

提交前自查 + Review 别人时逐条对照。分为"必须阻断（Blocker）"与"需要讨论（Discuss）"。

---

## A. 架构分层（Blocker）

- [ ] ViewModel 里没有 `import com.v2ray.ang.handler.*` / `core.*` / `service.*`
- [ ] ViewModel 里没有 `Context` / `Application` / `Resources` / `stringResource`
- [ ] ViewModel 里没有 `androidx.compose.*`（`@Immutable` / `@Stable` 注解除外）
- [ ] Repository 里没有 `import com.v2ray.ang.ui.*`
- [ ] Composable 里没有直接调 `MmkvManager` / `SettingsManager` / 其他 handler
- [ ] Composable 里没有 `LocalContext.current as XxxActivity`；平台能力走 `LocalPlatformActions`
- [ ] 新增的类放对了包：dto→`dto/`、屏专属模型→`XxxContract.kt`、
      数据门面→`repository/`、跨进程数据源→`handler/`、manifest 入口→`receiver/`
- [ ] 没有新增 SharedPreferences / DataStore / Room / 第二种持久化
- [ ] 没有引入 DI 框架、Navigation-Compose、material-icons-extended
- [ ] 没有新增 XML 业务布局、没有新增 `AndroidView`（除非 PR 中说明了不可替代的理由）

## B. SSOT（Blocker）

- [ ] 这一屏只有一个 `XxxUiState : BaseUiState`，`@Immutable`，全 `val`
- [ ] 状态修改只通过 `setState { copy(...) }`，没有第二个 `MutableStateFlow` 承载 UI 状态
      （列表切片、`ThemeManager` 属于既定例外）
- [ ] `isLoading` 没有被塞进 UiState
- [ ] 一次性效果（消息、导航、弹窗触发、Bitmap）没有进 UiState
- [ ] Composable 没有把 State 里的值 `remember` 成第二份可变副本
- [ ] 表单输入的唯一来源是 UiState，不是 Composable 局部 `mutableStateOf`
- [ ] 大列表数据没有塞进 UiState（用 per-key `StateFlow` 切片）

## C. UDF（Blocker）

- [ ] Composable 与 ViewModel 之间只有 `onAction(action)` 一条写通道
- [ ] 除 `uiState` / `isLoading` / `events` 的读取型订阅外，Composable 没调用任何 ViewModel 成员
- [ ] 没有把 `viewModel` 传给非根 Composable
- [ ] `onAction` 的 `when` 是穷尽的（没有 `else` 兜底）
- [ ] Action 命名表达用户意图，不是 setter
- [ ] 一次性效果走 `BaseEvent`；`BaseEvent.Platform` 由 Activity 翻译，屏内效果由 `onEvent` 截获
- [ ] 提示文案用 `BaseText` 描述，由 UI 渲染；ViewModel 没有直接弹 toast/Snackbar
- [ ] 子页结果通过 `BaseResult` 回传，父页用 `onResult` → Action 处理
- [ ] Activity 里没有业务判断，只有 VM 构造、平台能力翻译、系统回调

## D. 数据层（Blocker）

- [ ] Repository 继承 `BaseRepository`，所有阻塞调用在 `withIO { }` 内
- [ ] Repository 里没有 `launch`、没有自建 `CoroutineScope`
- [ ] 需要被 mock 的方法声明了 `open`
- [ ] 展示用字符串在 Repository 拼好，不在 Composable 里拼
- [ ] 带缓存的 Repository 有失效路径（`invalidate` / `dropFromCache`）和竞态防护（epoch 或 Mutex）
- [ ] 注册了 receiver / 打开了资源的 Repository 实现了 `Closeable`，
      且 ViewModel 在 `onCleared()` 里 `close()`
- [ ] 新增偏好项走了完整四步（AppConfig 常量 → `BoolPref`/`StringPref` → `isUiOnly` 分类 → UI 项）
- [ ] 需要重启内核才生效的设置项，`uiOnly` 判定正确

## E. 协程（Blocker）

- [ ] ViewModel 用 `launch(...)` 包装，没有裸 `viewModelScope.launch`
- [ ] `loading = true` 只用在用户可感知的阻塞操作上
- [ ] 长循环里有 `currentCoroutineContext().ensureActive()`
- [ ] 可被取代的 Job 存成字段并在启动前 `cancel()`；`onCleared()` 里清理干净
- [ ] 同 key 的连续写入做了串行化（`previous?.join()`）
- [ ] 必须落盘的写入用 `withContext(NonCancellable)` 包裹
- [ ] 没有 `GlobalScope`、`runBlocking`、`Thread`

## F. Compose 结构（Blocker）

- [ ] 文件按 `XxxActivity/Contract/Screen/ViewModel` 四件套组织，超长文件已拆分
- [ ] 每个可复用 Composable 有 `modifier: Modifier = Modifier` 且作用在最外层
- [ ] 参数顺序正确（必填数据 → 必填回调 → modifier → 可选参数）
- [ ] Content 层是无状态的，能被 Preview 直接调用
- [ ] 只放进 `ui/compose/` 的组件确实被 ≥2 个 feature 使用
- [ ] 复用了既有共享组件（`AppTopBar`/`ConfirmDialog`/`SelectListDialog`/`SettingsXxxItem`/
      `FormTextField`/`AppDropdownMenuItems`/`verticalScrollbar`），没有重复造轮子
- [ ] 使用 `BaseScreen` 的屏：底部有 `NavigationBarsSpacer()` /
      `NavigationBarsBottomPadding()`，或底栏自身已处理 `WindowInsets.navigationBars`；
      没有用固定 dp 猜导航栏高度

## G. 性能与重组（Blocker）

- [ ] `uiState` 只被 `BaseScreen` 收集一次；栏区用了收窄切片（`map` + `distinctUntilChanged` + `remember`）
- [ ] `dispatch` 用 `remember(viewModel) { viewModel::onAction }` 固定引用
- [ ] 多于 3 个回调打包成了 `@Stable class`，并在列表外 `remember` 一次
- [ ] 所有 lazy 列表项有 `key`；异构列表有 `contentType`
- [ ] 列表项内部没有订阅大流、没有做计算
- [ ] composition 中没有正则、排序、过滤、IO、字符串重拼装
- [ ] 默认参数不是新建对象（用 `Companion.Default` 单例）
- [ ] 颜色/`TextFieldColors` 等对象没有在每次重组时重建
- [ ] 高频状态读取下沉到 lambda-based modifier（`offset {}` / `graphicsLayer {}` / `drawBehind {}`）
- [ ] `LaunchedEffect` 的 key 稳定；回调用 `rememberUpdatedState` 包裹
- [ ] `LaunchedEffect(Unit)` 只用于真正的一次性初始化
- [ ] 等待布局就绪的 effect 有 `withTimeoutOrNull` 兜底
- [ ] 一次性事件处理完清了标志（`finally { onXxxHandled() }`）
- [ ] `HorizontalPager` 有 `key`，`beyondViewportPageCount` 合理
- [ ] 按 key 缓存的 Map（滚动状态、切片流）有 `retain` / `removeAll` 清理路径
- [ ] 没有为了加 padding 而多包一层容器；层级没有无谓加深
- [ ] Bitmap 用完即弃，没有进 State 或缓存
- [ ] 若是性能改动，PR 描述里给出了改前/改后的重组次数或帧率对比

## H. 主题与资源（Blocker）

- [ ] 没有 `Color(0xFF…)` 字面量；用 `MaterialTheme.colorScheme` 或 `LocalAppColors`
- [ ] 没有新建全局 dimens/spacing 对象；尺寸是文件内 `private val` 且命名表达用途
- [ ] 没有硬编码用户可见文案；新文案进了 `res/values/strings.xml`
- [ ] 字体只用 `MaterialTheme.typography.*`
- [ ] 字符串数组用 `rememberStringOptions(@ArrayRes)`
- [ ] 图片有固定尺寸与 placeholder

## I. 无障碍（Blocker）

- [ ] 可点击图标有 `contentDescription`（描述动作，`acc_` 前缀资源）
- [ ] 装饰性图标 `contentDescription = null`
- [ ] 开关/单选用了 `toggleable` / `selectable` + 正确的 `role`，内层控件未重复注册回调
- [ ] 触控目标 ≥ 48.dp
- [ ] 长文本有 `maxLines` + `TextOverflow`，没有写死 `fontSize`
- [ ] 状态不只靠颜色表达

## J. 多进程与系统组件（Blocker）

- [ ] 新增的服务→UI 通知走了 `AppConfig.MSG_*` → `MainServiceEvent` → `MainViewModel` 这条唯一链路
- [ ] 没有在第二个 Repository 里注册 `BROADCAST_ACTION_ACTIVITY`
- [ ] 注册与反注册配对，反注册用 `runCatching` 包住
- [ ] Receiver 里只有翻译转发，没有业务逻辑和 IO
- [ ] `PendingIntent` 带 `FLAG_IMMUTABLE`
- [ ] 启停内核只经 `LauncherManager`
- [ ] API 版本判断用 `Build.VERSION_CODES.*` 常量，不用数字

## K. 通用工程（Blocker）

- [ ] 依赖版本走 `libs.versions.toml`，没有硬编码
- [ ] 日志用 `LogUtil` + `AppConfig.TAG`，没有 `Log.x` / `printStackTrace()`
- [ ] 没有提交调试代码（`composeCompiler` 报告块、临时打印、注释掉的实验代码）
- [ ] `./gradlew :app:compileFdroidReleaseKotlin` 通过
- [ ] `./gradlew test` 通过

## L. 需要讨论（Discuss，非阻断）

- [ ] 新增了 handler：是否真的跨进程复用？能否直接写进 Repository？
- [ ] 新增了 `ui/compose/` 共享组件：现在是否已有 ≥2 个使用方？
- [ ] 新增了 Action：是否与已有 Action 语义重叠？
- [ ] 新增了 dto：是否只服务一屏（应下沉到 `XxxContract.kt`）？
- [ ] 使用了 Material3 alpha API：是否有稳定替代？升级 `1.5.0-alpha26` 时会不会断？
- [ ] 缓存策略是否会在极端数据量（数千服务器）下退化？
- [ ] 新增的常量是否应该进 `AppConfig`（跨模块）还是留在文件内（局部）？
