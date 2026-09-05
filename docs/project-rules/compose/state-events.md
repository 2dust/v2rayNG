# Compose 规范 · 状态、Action 与一次性事件

## 1. UiState 建模

```kotlin
@Immutable
data class XxxUiState(
    val items: List<XxxItem> = emptyList(),
    val selectedId: String? = null,
    val isRunning: Boolean = false,
) : BaseUiState
```

规则：

1. `@Immutable` + `data class` + 全 `val` + 全部有默认值（便于 Preview 与初始化）。
2. 不放 `isLoading`（`BaseViewModel` 提供）。
3. 不放一次性效果（消息、导航、弹窗触发）。
4. 不放 `Context`、`Bitmap` 之外的平台对象；`Bitmap` 只允许经 `BaseEvent.Platform` 传递
   （`MainEvent.ShowQrCode`），不入 State。
5. 派生值优先在 UI 现算（`state.items.isEmpty()`），只有计算昂贵时才存进 State。
6. 超大列表不进 State，走 ViewModel 的切片 StateFlow（见 `architecture-rules.md` §3.3）。
7. 多态状态用密封接口而不是一堆布尔：
   ```kotlin
   @Immutable
   sealed interface MainStatus {
       data object Disconnected : MainStatus
       data object Connected : MainStatus
       data object Testing : MainStatus
       data class TaskLeft(val left: String) : MainStatus
       data class Delay(val content: String) : MainStatus
   }
   ```
   状态到文案的映射写成 UI 侧的 `@Composable fun MainStatus.asText(): String`，
   ViewModel 不碰 `stringResource`。

## 2. Action 建模

```kotlin
sealed interface XxxAction : BaseAction {
    data object Initialize : XxxAction
    data class SelectItem(val id: String) : XxxAction
    data class ResultReceived(val result: BaseResult) : XxxAction
}
```

- 名字表达用户意图，不表达实现。
- 参数只带 UI 能提供的原始信息（id、index、文本、Uri），不带整个对象。
- `onAction` 的 `when` 必须穷尽（不写 `else`），加新 Action 时编译器会提醒你补分支。
- 需要在 `onCreate` 触发的一次性初始化，用显式 `Initialize` Action，
  ViewModel 内用 `initialized` 布尔守卫幂等（`MainViewModel.initialize()`）。

## 3. Event 建模

```kotlin
sealed interface XxxEvent : BaseEvent.Platform {
    data object PickFile : XxxEvent
    data class ShowQrCode(val bitmap: Bitmap) : XxxEvent
}
```

三条路径：

| 事件 | 谁消费 |
| --- | --- |
| `BaseEvent.Message` | `BaseEventEffect` → `LocalAppSnackbar` |
| `BaseEvent.Navigate(route)` | `BaseScreen`：`AppRoute.OpenUrl` 走 `Utils.openUri`，其余走 `BaseResultContract` |
| `BaseEvent.Finish(result)` | `BaseEventEffect` → `Activity.finishWithResult` |
| `BaseEvent.Platform` 子类型 | 先由 `BaseScreen(onEvent = …)` 里的屏内处理器截获（弹窗、滚动定位），未截获的再交给 `XxxActivity.handlePlatformEvent` |

`onEvent` 返回 `true` = 已消费。样板：

```kotlin
onEvent = { event ->
    when (event) {
        is MainEvent.ShowQrCode -> { dialogs.show(MainDialog.QrCode(event.bitmap)); true }
        is MainEvent.LocateProfile -> { locateTarget = event.target; true }
        is MainEvent -> onPlatformEvent(event)   // 交给 Activity
        else -> false                            // 交给 BaseScreen 默认处理
    }
}
```

## 4. 文案：BaseText

ViewModel 里禁止出现 `Context` 和 `stringResource`。用 `BaseText`：

```kotlin
toast(R.string.title_del_config_count, count)         // 简写
toast(BaseText.of(R.string.msg_x, BaseText.of(R.string.title_y)))  // 可嵌套
toastSuccess() / toastError() / toastInfo(resId, …)
```

UI 侧由 `BaseText.asString(context)` 解析。数据层产生的原始错误串用 `BaseText.Raw`。

## 5. 弹窗

弹窗是 UI 局部状态，**不进 UiState**。每屏定义：

```kotlin
sealed interface XxxDialog {
    data object DeleteAll : XxxDialog
    data class DeleteOne(val id: String) : XxxDialog
    data class QrCode(val bitmap: Bitmap) : XxxDialog
}
```

宿主用 `@Stable class`（`MainDialogHost`）持有 `var current by mutableStateOf<XxxDialog?>(null)`，
对外暴露稳定的 `show` / `dismiss` / `requestRemove` lambda 引用——
这样传给子组件的回调引用恒定，不会因为宿主重组而使子树失效。

需要读 UiState 才能决定行为的开关（如"删除前是否确认"），
用 `SideEffect { dialogs.confirmRemove = state.confirmRemove }` 同步进宿主，
而不是把宿主变成 Composable 参数。

渲染集中在一个 `XxxDialogs(dialog, onDismiss, onAction, …)` 里，`when` 穷尽分发。

## 6. 表单与编辑页

- 输入值的唯一来源是 UiState，`onValueChange` → `onAction(XxxAction.FieldChanged(...))`
  → `setState`。不要在 Composable 里 `remember { mutableStateOf(initial) }` 存输入。
- 校验在 ViewModel。失败时 `toastError` 并保持在当前页
  （`BaseEditViewModel.doSave()` 返回 `null`）。
- 保存/删除用 `BaseEditViewModel.save()` / `delete()`，它们自带 `loading = true`
  和"成功即 `finishWith(result)`"。
- 需要跨进程死亡保活的输入，构造 VM 时接 `SavedStateHandle`。

## 7. 结果回传

```kotlin
sealed interface BaseResult { Cancelled / Saved / Deleted / Changed / Selected }
```

- 子页：`finishWithResult(BaseResult.Saved(...))`。
- 父页：`BaseScreen(onResult = { viewModel.onAction(XxxAction.ResultReceived(it)) })`。
- ViewModel 在 `handleResult` 里按 `result.refreshList` / `result.restartService` /
  `result.notify` 决定后续动作。
- `autoToastResult = true`（默认）时框架已经替你弹成功提示，别重复弹。
