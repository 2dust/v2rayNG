# 项目级规范索引

本目录是 v2rayNG 的强制规范库。写代码前按下表对照阅读；
**规范与源码冲突时以源码为准**，并在同一次改动里修正规范。

## 文件一览

| 文件 | 覆盖领域 | 什么时候必须读 |
| --- | --- | --- |
| [`architecture-rules.md`](architecture-rules.md) | 分层、包归属、MVVM/SSOT/UDF 落地、Activity 职责边界 | 新增屏、挪文件、决定"这个类该放哪"时 |
| [`repository-rules.md`](repository-rules.md) | Repository 与 handler 的分工、缓存、线程、可测试性 | 新增数据访问逻辑、加 Repository 方法时 |
| [`coroutine-flow-rules.md`](coroutine-flow-rules.md) | `launch` 包装、Scope、Job 管理、Flow 位置、去抖与串行化 | 写任何协程或 Flow 时 |
| [`service-ipc-rules.md`](service-ipc-rules.md) | 多进程、广播、MSG 常量、Worker、Receiver、通知 | 动 `service/`、`receiver/`、`core/` 时 |
| [`compose/structure.md`](compose/structure.md) | 目录/文件/函数命名、参数契约、脚手架用法 | 写任何 Composable 时 |
| [`compose/state-events.md`](compose/state-events.md) | UiState/Action/Event 建模、Dialog 宿主、表单与结果回传 | 设计一屏的状态时 |
| [`compose/theme-styles.md`](compose/theme-styles.md) | 颜色、尺寸、字符串、图标、图片、动画 | 用颜色/尺寸/资源时 |
| [`compose/performance.md`](compose/performance.md) | 重组防范、稳定性、列表、副作用、度量 | 写列表、发现卡顿、Review 性能时 |
| [`compose/navigation-preview.md`](compose/navigation-preview.md) | AppRoute、BaseResult、Preview 规范 | 加导航目标或 Preview 时 |
| [`compose/accessibility.md`](compose/accessibility.md) | contentDescription、语义合并、触控目标 | 加图标按钮/可点击行时 |
| [`compose/testing.md`](compose/testing.md) | 测试分层、可测边界、模板 | 写测试时 |
| [`compose/review-checklist.md`](compose/review-checklist.md) | 提交前自查 + Review 清单 | 每次提 PR 前、每次 Review 时 |

## 三条不可协商的底线

1. **SSOT**：一屏一个 `StateFlow<UiState>`，由 `BaseViewModel` 持有，
   `setState { }` 是唯一写入口。同一份数据不得在 State 与 Composable 局部状态里各存一份。
2. **UDF**：UI 只能通过 `onAction(action)` 与 ViewModel 通信。
   Composable 里不得出现除 `onAction` 之外的 ViewModel 成员调用
   （唯一例外：`BaseScreen` / 槽位内对 `viewModel.uiState`、`viewModel.isLoading`、
   `viewModel.events` 的**读取型**订阅）。
3. **分层**：`ui/` → `repository/` → `handler/` → MMKV / 网络 / 内核。
   ViewModel 不得 import `handler/`、`core/`、`android.content.Context`。