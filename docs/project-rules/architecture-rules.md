# 架构规范：MVVM / SSOT / UDF 在 v2rayNG 的落地

参考的是 Android 官方 Guide to app architecture（UI layer / Domain / Data layer、
UDF、UI State as single source of truth）。本文件只写"在本仓库怎么做"。

## 1. 分层与依赖方向

```
┌───────────────────────────── UI Layer ─────────────────────────────┐
│ ui/<feature>/XxxScreen.kt      纯 Composable，无状态，读 UiState     │
│ ui/<feature>/XxxActivity.kt    Compose 宿主 + 平台能力翻译            │
│ ui/<feature>/XxxViewModel.kt   状态持有者，产出 UiState / 消费 Action │
│ ui/<feature>/XxxContract.kt    UiState / Action / Event 类型定义      │
│ ui/base/ ui/compose/           基座与共享组件                        │
└───────────────────────────────┬────────────────────────────────────┘
                                │ 只能调用 suspend / 普通函数
┌───────────────────────────────▼──── Data Layer ────────────────────┐
│ repository/  UI 进程的数据门面：缓存、聚合、线程收敛、事件流           │
│ handler/     进程无关的数据源与领域逻辑（MmkvManager 为唯一持久层）    │
│ dto/ enums/ fmt/ util/ extension/  纯数据与纯函数                    │
└───────────────────────────────┬────────────────────────────────────┘
                                │
┌───────────────────────────────▼──── Platform ──────────────────────┐
│ core/ service/ root/ receiver/  内核、前台服务、Root、系统入口        │
└────────────────────────────────────────────────────────────────────┘
```

依赖只能向下。硬性禁止：

- `ui/**/*ViewModel.kt` 不得 import `com.v2ray.ang.handler.*`、`com.v2ray.ang.core.*`、
  `com.v2ray.ang.service.*`、`android.content.Context`、`android.app.Application`、
  `android.widget.*`、任何 `androidx.compose.*`（`@Immutable` / `@Stable` 注解除外）。
- `repository/` 不得 import `ui/`（`ui.base.BaseResult` 也不行——结果类型由 ViewModel 组装）。
- `handler/`、`core/`、`service/` 不得 import `repository/` 与 `ui/`。
- Composable 不得 import `handler/`、`repository/`、`core/`
  （`ui.compose.Theme` 对 `ThemeRepository` 的引用是唯一历史例外，不要新增同类）。

## 2. 包归属决策（本仓库的最终答案）

### 2.1 DTO 与 Repository 保持在 `com.v2ray.ang.dto` / `com.v2ray.ang.repository`

**不迁入 `ang/data/`。** 理由：

- 官方那套 `data/{repository,datasource,model}` 是为"多数据源（网络 + 数据库 + 缓存）+ 依赖注入"
  准备的。本项目只有 MMKV 一个持久层，网络只在订阅/更新检查两处，没有 DAO、没有 Entity Mapper，
  也没有 DI 容器。再套一层目录只会增加路径深度和 import 噪音。
- `dto/` 已经内含 `dto/entities/`（`ProfileItem`、`SubscriptionCache` 等持久化实体），
  与纯传输结构（`ServerRowItem`、`GroupMapItem`、`CheckUpdateResult`）天然分开，
  这已经是"model 分层"的效果。
- 迁移会触碰几乎每个文件的 import，与任何功能改动混在一起会让 diff 不可 review。

**规则：**

| 放哪 | 放什么 |
| --- | --- |
| `dto/` | 跨层传递的不可变数据类。UI 直接消费的行模型（`ServerRowItem`、`GroupMapItem`、`AppInfo`）也在这里 |
| `dto/entities/` | 会被 MMKV 序列化落盘的实体（`ProfileItem`、`SubscriptionCache`…） |
| `ui/<feature>/XxxContract.kt` | **只有这一屏用**的 UI 模型（`MainStatus`、`SettingsPrefs` 的 UI 投影等） |
| `repository/` | 每屏一个 Repository（或一组屏共用一个），继承 `BaseRepository` |

新增 dto 时先问：会被第二个 feature 用到吗？会被xxxreposipory用到吗？不会就留在该屏的 `XxxContract.kt`，别污染 `dto/`。

### 2.2 `receiver/` 保持独立，不放进 `ui/`

**不迁入 `ui/`。** `BootReceiver`、`TaskerReceiver`、`WidgetProvider` 的共同特征：

- manifest 静态注册，是系统进程唤起 App 的入口，生命周期与任何 Activity/Composition 无关；
- 没有 ViewModel、没有 Composition，`WidgetProvider` 用的是 `RemoteViews`，不是 Compose；
- 直接依赖 `core/LauncherManager` 与 `core/CoreServiceManager`。放进 `ui/` 会让 UI 包
  反向依赖 core/service，破坏第 1 节的依赖方向。

**规则：**

- manifest 注册的 `BroadcastReceiver` / `AppWidgetProvider` → `receiver/`。
- 代码注册、生命周期绑定某个数据流的 `BroadcastReceiver` → **写成 Repository 的私有成员**，
  对外只暴露 Flow。样板是 `MainRepository`：内部匿名 receiver 把 `AppConfig.MSG_*`
  翻译成 `MainServiceEvent`，通过 `serviceEvents: SharedFlow<MainServiceEvent>` 出去，
  并实现 `Closeable`，在 `ViewModel.onCleared()` 里 `repo.close()` 反注册。
- Receiver 里禁止写业务逻辑，只允许"翻译 + 转发"：转给 `LauncherManager`、
  转给 Service、或 `tryEmit` 到 Flow。

### 2.3 `handler/` 不解散，与 `repository/` 分工明确

**handler 不彻底融入 repository。** 原因：`MmkvManager`、`SettingsManager`、
`AngConfigManager`、`SubscriptionUpdater`、`NotificationManager` 被
`CoreVpnService`、`CoreTestService`、`SubscriptionUpdateService`、`RealPingWorkerService`、
`QSTileService`、`BootReceiver` 等**跑在 `:RunSoLibV2RayDaemon` / `:bg` 进程**的组件直接调用。
那些进程里没有 ViewModel，强行让它们走 Repository 只是给非 UI 代码加一层空壳。

**职责切分：**

| 层 | 定位 | 允许被谁调用 |
| --- | --- | --- |
| `handler/` | 数据源 + 领域逻辑，进程无关，`object` 或无状态类，方法多为同步阻塞 | `repository/`、`service/`、`receiver/`、`core/`、`root/` |
| `repository/` | UI 进程的数据门面：`suspend` 化、线程收敛、内存缓存、聚合多个 handler、暴露 Flow | 只有 ViewModel |

**因此的硬规则：**

1. ViewModel 只能调 Repository，一个 handler 调用都不许出现在 ViewModel 里。
2. 只被一个 Repository 用、且不跨进程的逻辑，**不要新建 handler**，直接写进 Repository。
3. handler 里禁止出现 `StateFlow` / `MutableStateFlow` 形式的 UI 状态；
   跨进程状态用 MMKV + 广播，UI 状态归 ViewModel。
   （`SettingsChangeManager` 只做 key 分类，不持有状态，是允许的形态。）
4. handler 的方法保持同步、无 Dispatcher 假设；切线程是 `BaseRepository.withIO { }` 的责任。

### 2.4 `helper/` 与 `util/`

- `helper/` = 需要 Activity / Context 的能力封装（`FileChooserHelper`、`PermissionHelper`、
  `QRCodeScannerHelper`、`NotificationHelper`、`MessageHelper`）。
  Activity 侧能力必须经 `BaseHelperActivity` → `PlatformActions` → `LocalPlatformActions` 下发。
- `util/` = 无状态纯函数或极薄的系统 API 包装，可在任何进程调用。

## 3. SSOT：状态的唯一来源

1. 一屏一个 `XxxUiState : BaseUiState`，标 `@Immutable`，全部 `val`。
2. `BaseViewModel` 内的 `_uiState` 是唯一可变来源；`setState { copy(...) }` 是唯一写法，
   禁止在 ViewModel 里另建 `MutableStateFlow` 承载"也算 UI 状态"的东西——
   除非它是**列表切片**（见下）。
3. **列表切片例外（重要）**：`MainViewModel` 用
   `servers(groupId): StateFlow<List<ServerRowItem>>` 与 `serverCount(groupId): StateFlow<Int>`
   把每个分组的行数据独立出去，不进 `MainUiState`。这是刻意的性能设计——
   几千行的 List 放进 UiState 会让任何一次状态变更都重算整个列表。
   新增大列表页时沿用这个模式，并在 `XxxContract.kt` 或 `XxxStateHolders.kt` 里用
   `@Stable class` 打包这些访问器（样板：`MainSlices`）。
4. `isLoading` 不进 UiState，由 `BaseViewModel` 的引用计数提供。
5. UI 侧不得把 State 里的值 `remember` 成第二份可变副本。表单输入的唯一来源也是 UiState
   （样板：`BaseEditViewModel` 系列的编辑页）。

## 4. UDF：单向数据流

1. `XxxAction : BaseAction`，用 `sealed interface` + `data object` / `data class`。
   命名是**用户意图**（`ToggleService`、`RemoveServer(guid)`），不是实现动作（`setRunning(true)`）。
2. `XxxViewModel.onAction(action)` 用穷尽 `when` 分发，每个分支只做一件事：
   要么 `setState`，要么 `launch { repo.xxx() }`，要么 `platform(...)` / `navigate(...)`。
   分支体超过 3 行就抽私有方法。
3. 一次性效果走 `BaseEvent`，**永远不进 UiState**：
   - `BaseEvent.Message` —— toast/snackbar；ViewModel 只描述（`BaseText`），UI 负责渲染；
   - `BaseEvent.Navigate(route)` —— 由 `BaseScreen` 消费；
   - `BaseEvent.Finish(result)` —— 关页并回传；
   - `XxxEvent : BaseEvent.Platform` —— 只有 Activity 能做的事
     （VPN 授权、启停内核、扫码、选文件），由 `XxxActivity.handlePlatformEvent` 翻译。
4. Composable 拿到的永远是 `(state, onAction)`；子组件继续往下传 `onAction` 或更窄的回调，
   不得下传 ViewModel。
5. Activity 里禁止业务判断。`MainActivity` 的形态就是上限：构造 VM、
   `requestPermission`、把 `MainEvent` 翻成 `LauncherManager` / `VpnService.prepare()` 调用、
   把结果再 dispatch 回 Action。

## 5. ViewModel 规范

- 构造参数只接 Repository（+ 必要的 `SavedStateHandle`），由
  `baseViewModels { app, handle -> XxxViewModel(XxxRepository(app), handle) }` 装配。
  `Application` 只允许在这个 lambda 内使用，**不得存进 ViewModel**。
- 所有协程走 `launch(loading =, context =, onError =) { }`，不用裸 `viewModelScope.launch`。
- 需要长期存活的 Job（预取、去抖、轮询）保存成字段，在 `onCleared()` 里取消；
  持有 `Closeable` Repository 的必须在 `onCleared()` 调 `close()`（样板：`MainViewModel`）。
- 校验失败不抛异常，用 `toastError(...)` + 保持状态；
  编辑页用 `BaseEditViewModel.doSave()` 返回 `null` 表示"留在本页"。

## 6. Activity 规范

- 只继承 `BaseActivity` 或 `BaseHelperActivity`。
- `ScreenContent()` 里只允许一个 `XxxScreen(viewModel, ...)` 调用。
- 允许出现的成员：VM 字段、`ActivityResultLauncher`、`handlePlatformEvent`、
  `onNewIntent` / `onKeyDown` 这类系统回调。
- 禁止：持有 UI 状态、直接读写 MMKV、直接调 handler/core（`LauncherManager` 例外，
  它就是平台能力）、弹 Toast（走 Event）。

## 7. 新增一屏的标准动作

1. 在 `ui/<feature>/` 建四件套：`XxxContract.kt` → `XxxViewModel.kt` → `XxxScreen.kt` → `XxxActivity.kt`。
2. 在 `repository/` 建 `XxxRepository : BaseRepository()`，方法全 `suspend` 且 `withIO { }` 包裹。
3. 在 `ui/AppRoute.kt` 加导航目标；需要传参的用 `AppRoute.Companion` 里的 `EXTRA_*` 常量。
4. 在 `AndroidManifest.xml` 注册 Activity。
5. 若该屏需要文件/权限/扫码 → 继承 `BaseHelperActivity`。
6. 跑 `./gradlew :app:compileFdroidReleaseKotlin` 与 `./gradlew test`。
