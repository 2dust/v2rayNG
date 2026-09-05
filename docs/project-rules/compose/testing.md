# Compose 规范 · 测试

## 1. 现实约束

本仓库 CI 不跑测试，仪器测试基本未使用（只有 espresso 依赖声明）。
因此策略是：**把值得测的逻辑挤出 Composable，用 JVM 单元测试覆盖。**

依赖：JUnit4 + `mockito-inline` + `mockito-kotlin`。测试目录 `app/src/test/java/`。

## 2. 分层与优先级

| 优先级 | 测什么 | 现有样例 |
| --- | --- | --- |
| P0 | 纯函数：协议解析、字符串/列表扩展、工具 | `ShadowsocksFmtTest`、`UtilsTest`、`HttpUtilTest`、`ListExtTest` |
| P0 | UI 纯逻辑函数：菜单项计算、校验、可见性判断 | `MainImportMenuTest` |
| P1 | ViewModel reducer：给定 Action，State 如何变 | `AppPickerViewModelTest` |
| P2 | Repository 的缓存/失效逻辑（可用假 handler） | — |
| P3 | Composable UI 测试 | 目前不做 |

## 3. 让逻辑可测的写法

把"根据条件算出该显示哪些菜单项"这类逻辑抽成**顶层纯函数**，而不是写在 Composable 里：

```kotlin
fun serverMenuActions(
    isComplexProfile: Boolean,
    includeManagementActions: Boolean,
): List<ServerMenuAction> = …
```

Composable 只负责渲染 `serverMenuActions(...)` 的结果。测试就变成纯断言：

```kotlin
@Test
fun complexShareMenuContainsOnlyFullContent() {
    assertEquals(
        listOf(ServerMenuAction.ShareFullContent),
        serverMenuActions(isComplexProfile = true, includeManagementActions = false),
    )
}
```

同理适用于：表单校验（`ServerValidator`）、路由规则解析、延迟着色阈值、状态到文案的映射
（映射本身可测，`stringResource` 那层不测）。

## 4. ViewModel 测试模板

Repository 声明为 `open class` + `open fun`，直接 mock：

```kotlin
class XxxViewModelTest {

    private val repo = mock<XxxRepository> {
        onBlocking { load() } doReturn listOf(item("a"), item("b"))
    }

    @Test
    fun selectingItem_updatesState() = runTest {
        val vm = XxxViewModel(repo)
        vm.onAction(XxxAction.SelectItem("a"))
        assertEquals("a", vm.uiState.value.selectedId)
    }
}
```

- 需要控制调度时引入 `kotlinx-coroutines-test` 的 `StandardTestDispatcher`
  （当前 versions 目录未声明该依赖，如需使用先加进 `libs.versions.toml`）。
- 断言只看 `uiState.value` 与对 repo 的 `verify`，不要断言内部私有字段。
- 幂等性要测（`AppPickerViewModelTest.initialize_preservesChangesAfterFirstCall`
  就是防止二次 `initialize` 抹掉用户改动）。

## 5. 不测什么

- 不测 `BaseScreen` / `BaseViewModel` 这类基座的框架行为（改动它们时人工验证）。
- 不测 Compose 的布局与像素。
- 不为覆盖率而给 getter/setter 写测试。

## 6. 完成标准

提交前本地必须通过：

```bash
cd V2rayNG
./gradlew :app:compileFdroidReleaseKotlin
./gradlew test
```
