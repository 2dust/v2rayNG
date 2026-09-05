# 数据层规范（repository / handler）

## 1. BaseRepository

所有 Repository 继承 `BaseRepository`，它只做一件事：线程收敛。
`BaseRepository` 提供了 `withIO`、`runIO` 和 `flowIO` 三个工具方法，分别用于：
- **`withIO`**：单次阻塞调用，异常直接上抛（由 ViewModel 统一处理）。
- **`runIO(fallback) { ... }`**：带降级的阻塞调用，内部捕获异常并记录日志，返回 fallback，适用于可预期失败且业务允许返回默认值的场景。
- **`flowIO`**：为 `Flow` 切换线程（`flowOn(io)`）。

```kotlin
class XxxRepository(private val app: Application) : BaseRepository() {
    suspend fun load(): XxxData = withIO { MmkvManager.decodeXxx() }
}
```

- **凡是会阻塞的调用（MMKV、文件、网络、`PackageManager`、Root shell）必须在 `withIO { }` 内。**
  如果该调用可能失败且业务上允许返回默认值，可使用 runIO(fallback) { ... }，它会记录异常并返回 fallback；否则用 withIO 让异常上抛。
- 不阻塞的极轻量读取（如一次 `decodeSettingsBool`）可以做成同步 `fun`，
  供 ViewModel 在 `init` 里构造初始状态用（样板：`MainRepository.selectedGroupId()`、
  `confirmRemove()`、`doubleColumnDisplay()`）。这类方法必须明确无 IO 风险。
- 不要在 Repository 里自己 `withContext(Dispatchers.IO)`，用 `withIO`。
- 不要在 Repository 里 `launch`。Repository 没有 scope；需要后台任务由 ViewModel 发起。

## 2. Repository 的四类职责

1. **门面**：把 `handler/` 的同步 API 变成 `suspend`，屏蔽 MMKV 细节。
2. **聚合/投影**：把 `ProfileItem` + `ServerAffiliationInfo` 组装成 UI 直接能画的
   `ServerRowItem`，包括 `generateDescription`、`protocolDescription` 这类展示串拼装。
   **展示串在 Repository 拼，不在 Composable 拼。**
3. **缓存**：内存缓存 + 失效。样板 `MainRepository`：
   - `cache: MutableMap<String, List<ServerRowItem>>` 由 `cacheMutex` 保护；
   - `loadMutexes: ConcurrentHashMap<String, Mutex>` 保证同一分组不并发重复加载；
   - `cacheEpoch: AtomicLong` 做写入竞态防护——加载开始时取 epoch，写回时比对，
     期间若发生 `invalidate()` 则丢弃这次结果；
   - 所有删除操作走 `dropFromCache(guids)`，不全量失效。
   新增带缓存的 Repository 时**照抄这四件套**，不要自创。
4. **事件源**：需要监听系统/跨进程事件时，在 Repository 内注册 receiver，
   对外只给 `SharedFlow`（`replay = 0`、`extraBufferCapacity = 64`、
   `onBufferOverflow = DROP_OLDEST`），并实现 `Closeable`。

## 3. 渐进式加载

大列表用 `onChunk` 回调分批回吐，让首屏更快出来：

```kotlin
suspend fun loadServers(
    groupId: String,
    forceRefresh: Boolean = false,
    onChunk: (suspend (List<ServerRowItem>) -> Unit)? = null,
): List<ServerRowItem>
```

- 分块大小常量私有（`private const val LOAD_CHUNK_SIZE = 60`）。
- 循环体内必须 `currentCoroutineContext().ensureActive()`，保证取消及时。
- 只在首次进入 / 强制刷新时开 `progressive`，预取不开。

## 4. 可测试性

Repository 声明为 `open class`，需要被 mock 的方法声明 `open`
（样板：`MainRepository` 全量 `open`）。测试用 `mockito-kotlin` 的 `mock<XxxRepository>()`
直接替换，ViewModel 无需 DI 框架即可测。

不要为了"接口纯洁"给每个 Repository 抽 interface——本项目只有一个实现，`open` 足够。

## 5. handler 使用规则

| 场景 | 做法 |
| --- | --- |
| ViewModel 需要数据 | 调 Repository；Repository 内部调 handler |
| Service / Worker / Receiver 需要数据 | 直接调 handler，**不要**建 Repository |
| 新逻辑只服务于一屏 | 写进 Repository，不新建 handler |
| 新逻辑要跨进程复用 | 才允许新建 handler，且必须无状态、同步、不依赖 Dispatcher |

`MmkvManager` 是唯一持久层。新增偏好项的流程：

1. 在 `AppConfig` 加 `PREF_XXX` 常量；
2. 在 `SettingsRepository` 的 `BoolPref` / `StringPref` 枚举加一项（带默认值）；
3. 若该项影响内核配置（需要重启服务生效），确保 `SettingsChangeManager.isUiOnly(key)`
   返回 `false`；纯 UI 项返回 `true`；
4. 在 `SettingsScreen` 加对应的 `SettingsSwitchItem` / `SettingsListItem` / `SettingsEditItem`。

禁止：新增 `SharedPreferences`、`DataStore`、`Room`、任何第二种持久化。

## 6. 写入串行化

同一 key 的连续写入必须串行，避免"后发先至"。ViewModel 侧用 Job 链
（样板：`SettingsViewModel.persist()`、`MainViewModel.moveServer()` 的 `orderJobs`）：

```kotlin
val previous = jobs[key]
jobs[key] = launch(onError = {}) {
    previous?.join()
    withContext(NonCancellable) { repo.write(...) }
}
```

需要"页面关闭前必须写完"的场景，在 `exit()` 里 `writeJob?.join()` 之后再 `finishWith(...)`。

## 7. 错误处理

- Repository 不吞异常，也不弹 toast。可预期失败返回 `null` / 空集合 / 布尔；
  不可预期失败让异常上抛，由 `BaseViewModel.launch` 的 `onError` 统一处理。
  但 runIO 内部会捕获异常并返回 fallback，适用于可预期失败；若需要异常上抛统一处理，应使用 withIO。
- 需要记录时用 `runCatching { }.onFailure { LogUtil.e(AppConfig.TAG, "…", it) }`
  （样板：`MainRepository.readClipboard()` / `readTextFromUri()`）。
- 反注册、关闭这类清理动作一律 `runCatching` 包住，不能因为清理失败影响主流程。
