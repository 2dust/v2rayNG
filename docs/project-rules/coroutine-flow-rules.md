# 协程与 Flow 规范

## 1. 唯一的启动入口

ViewModel 里禁止裸 `viewModelScope.launch`，统一用 `BaseViewModel.launch`：

```kotlin
protected fun launch(
    loading: Boolean = false,
    context: CoroutineContext = EmptyCoroutineContext,
    onError: (Throwable) -> Unit = { toastError() },
    block: suspend CoroutineScope.() -> Unit,
): Job
```

- `loading = true`：引用计数式加载态，嵌套安全；只在**用户可感知的阻塞操作**上开
  （导入、导出、删除、更新订阅、保存）。后台预取、事件监听一律 `false`。
- `onError`：默认弹通用错误 toast。后台任务传 `onError = {}` 静默，
  需要留痕时传 `{ LogUtil.e(AppConfig.TAG, "…", it) }`。
- `CancellationException` 由框架原样抛出，不要自己 catch `Throwable` 后吞掉。

## 2. Dispatcher 选择

| 场景 | 用什么 |
| --- | --- |
| 调 Repository 的 `suspend` 方法 | 默认（Main.immediate），线程由 `withIO` 内部决定 |
| CPU 密集（过滤、正则、排序） | `launch(context = Dispatchers.Default)`（`MainViewModel.cpu`） |
| 必须串行的 IO 序列（预取、初始化） | `Dispatchers.IO.limitedParallelism(1)`（`MainViewModel.serial`） |
| Repository 内部 | `withIO { }`，不要自己选 |

## 3. Job 生命周期

- 可被新请求取代的任务，Job 存成字段，启动前先 `cancel()`：
  `setupJob`、`prefetchJob`、`filterJob`、`delayJob`。
- 按 key 并发的任务用 `ConcurrentHashMap<String, Job>`：`groupJobs`、`orderJobs`。
- `onCleared()` 里取消全部字段 Job、取消 map 里的 Job、关闭 `Closeable` Repository。
- 长循环体内必须 `currentCoroutineContext().ensureActive()`。

## 4. 去抖、节流、合并

- **搜索去抖**：`filterJob?.cancel()` + `delay(SEARCH_DEBOUNCE_MS)`，常量 300ms。
- **高频刷新合并**：用 `AtomicBoolean` 脏标 + 单协程循环，不要每次事件起一个协程。
  样板 `MainViewModel.scheduleDelayRefresh()`：
  ```kotlin
  delayDirty.set(true)
  if (delayJob?.isActive == true) return
  delayJob = launch(onError = {}) {
      while (delayDirty.getAndSet(false)) {
          repo.refreshDelays(groupId)?.let { publish(groupId, it) }
          delay(DELAY_REFRESH_INTERVAL_MS)   // 400ms
      }
  }
  ```
- **预取**：半径常量 `PREFETCH_RADIUS = 1`，每次预取前 `delay(PREFETCH_DELAY_MS)`（32ms）
  给主线程让路，并在 `serial` dispatcher 上跑。
- **启动顺序**：用 `CompletableDeferred` 表达"首屏就绪"，重资产初始化
  （`SettingsManager.initAssets` / `SubscriptionUpdater.sync`）等首屏之后再做。

## 5. Flow 的位置

| 类型 | 放哪 | 参数 |
| --- | --- | --- |
| UI 状态 | `BaseViewModel._uiState` | `MutableStateFlow` |
| 列表切片 | ViewModel 内 `ConcurrentHashMap<String, MutableStateFlow<T>>` | 对外只暴露 `asStateFlow()` |
| 一次性事件 | `BaseViewModel._events` | `Channel(UNLIMITED)` + `receiveAsFlow()` |
| 跨进程/系统事件 | Repository | `MutableSharedFlow(replay = 0, extraBufferCapacity = 64, DROP_OLDEST)` |
| 全局主题 | `ThemeManager` | `MutableStateFlow` + `asStateFlow()` |

- 事件用 `Channel` 不用 `SharedFlow`：`receiveAsFlow()` 单消费者、
  UI STOPPED 时缓冲、RESUMED 时补发，且不会重放。
- UI 收集一律 `collectAsStateWithLifecycle()`；收事件一律
  `repeatOnLifecycle(Lifecycle.State.STARTED)`（`BaseEventEffect` 已封装）。
- 不要在 Composable 里 `collect { }` 后自己 `mutableStateOf` 存一份。

## 6. 取消与不可取消

用户已确认的写入不能因为退出页面而丢失，用 `withContext(NonCancellable)` 包裹落盘那一小段，
而不是把整个协程改成不可取消。

## 7. 并发原语选择

| 需求 | 用什么 |
| --- | --- |
| 保护可变 Map/List | `Mutex` + `withLock`（挂起，不阻塞线程） |
| 高并发只读 + 偶写的 key→value | `ConcurrentHashMap` |
| 布尔脏标、一次性开关 | `AtomicBoolean` / `compareAndSet` |
| 版本号/代次防护 | `AtomicLong`（`cacheEpoch`） |

禁止 `synchronized`、`@Volatile` 之外的 Java 锁；`@Volatile` 仅用于简单标量
（`keyword`、`testingGroupId`）。
