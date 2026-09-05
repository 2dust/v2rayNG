# 服务、多进程与 IPC 规范

## 1. 进程拓扑

| 进程 | 组件 |
| --- | --- |
| 默认（UI） | 所有 Activity、`MainRepository` 的 receiver |
| `:RunSoLibV2RayDaemon` | `CoreVpnService`、`CoreProxyOnlyService`、`CoreTestService`、`TProxyService`、`DialerNativeService` 等 |
| `:bg` | WorkManager（`AngApplication` 里以 `${ANG_PACKAGE}:bg` 配置） |

跨进程**不能**共享内存对象。唯一的共享状态是 MMKV，唯一的通信手段是广播与 Messenger。

## 2. 消息协议

- 所有消息 key 是 `AppConfig.MSG_*` 整型常量，禁止字符串魔法值。
- 发送统一走 `helper/MessageHelper`：`sendMsg2Service`、`sendMsg2TestService`。
- UI 侧接收只有一个入口：`MainRepository` 内部的 `BroadcastReceiver`
  （`IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)`，flags 用 `Utils.receiverFlags()`），
  翻译成 `MainServiceEvent` 密封接口后 `tryEmit`。
- 新增一种服务→UI 的通知：
  1. `AppConfig` 加 `MSG_*` 常量；
  2. `MainServiceEvent` 加一个成员；
  3. receiver 的 `when` 加一条映射；
  4. `MainViewModel.handleServiceEvent` 加一条处理。
  **不要**在别的 Repository 里再注册一个 `BROADCAST_ACTION_ACTIVITY` 接收器。
- 注册必须配对反注册：Repository 实现 `Closeable`，用 `AtomicBoolean` 做幂等，
  `ViewModel.onCleared()` 调 `close()`，反注册用 `runCatching` 包住。

## 3. Service 规范

- 前台服务的通知统一走 `handler/NotificationManager` + `helper/NotificationHelper`，
  渠道定义在 `enums/NotificationChannelType`。
- Service 不 import `ui/`、不 import `repository/`；需要数据直接用 `handler/`。
- 启停内核的唯一入口是 `core/LauncherManager`（`startService` / `stopService` /
  `startServiceFromToggle`），Activity、`QSTileService`、`WidgetProvider`、
  `TaskerReceiver` 都只能调它。
- VPN 权限（`VpnService.prepare`）与运行时权限只能在 Activity 里请求；
  ViewModel 通过 `MainEvent.StartService(requireVpnPermission, requireLocalNetwork)`
  把"需要什么"描述出去，由 `MainActivity` 决定怎么要。
- API 版本分支写法：用 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.XXX`
  （如本地网络权限用 `CINNAMON_BUN`），不要用数字字面量。

## 4. Receiver 规范

- manifest 注册的三个：`BootReceiver`（开机自启）、`TaskerReceiver`（第三方自动化）、
  `WidgetProvider`（桌面小组件）。
- 只允许"翻译 + 转发"，禁止业务判断与 IO。需要耗时工作时启动 Service 或入队 Worker。
- `WidgetProvider` 用 `RemoteViews`，不是 Compose；改小组件样式改
  `res/layout/widget_switch.xml`，不要试图 Compose 化。
- `PendingIntent` 必须带 `FLAG_IMMUTABLE`。

## 5. Worker

- WorkManager 配置在 `AngApplication.onCreate`，指定 `:bg` 进程，
  依赖 `work-multiprocess`。
- Worker 只能依赖 `handler/` 与 `util/`。
- 订阅自动更新走 `SubscriptionUpdateService` / `SubscriptionUpdater`，
  不要另起一套定时机制。
