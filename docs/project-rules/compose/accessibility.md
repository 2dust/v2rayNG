# Compose 规范 · 无障碍

## 1. contentDescription

- **纯装饰**的 `Icon` / `Image`：`contentDescription = null`。
- **可点击**的图标必须有描述，且描述的是**动作**：
  ```kotlin
  IconButton(onClick = onBack) {
      Icon(painterResource(R.drawable.ic_arrow_back_24dp), stringResource(R.string.acc_back))
  }
  ```
- 描述文案进 `strings.xml`，统一 `acc_` 前缀（已有 `acc_back`）。
- 图标 + 文字并列时，图标 `null`，让文字承载语义。

## 2. 语义合并

列表行、设置项这类"整行可点"的容器，用 `Modifier.clickable(...)` 加在**最外层**，
内部子元素不再各自可点；需要时用 `Modifier.semantics(mergeDescendants = true) { }`
把整行合并成一个可读节点。

行内还有独立按钮（编辑、分享、删除）时，这些按钮各自需要 `contentDescription`，
且不能被父节点合并掉——把它们放在 `clickable` 容器之外或使用 `IconButton`（自带语义边界）。

## 3. 状态语义

- 开关行：`Modifier.toggleable(value, onValueChange = …, role = Role.Switch)`，
  内层 `Switch` 传 `onCheckedChange = null` 避免双重语义
  （`SettingsSwitchItem` 已是这个形态，`SwitchScale = 0.8f` 只是视觉缩放）。
- 单选行：`Modifier.selectable(selected, role = Role.RadioButton)`。
- 不可用项：用 `enabled = false` 而不是只把颜色调淡（`DisabledAlpha = 0.38f` 是视觉配套）。

## 4. 触控目标

- 最小 48.dp × 48.dp。图标本身 24.dp 时，用 `IconButton`（默认 48.dp）或
  给容器加 `Modifier.minimumInteractiveComponentSize()`。
- 列表行高不足 48.dp 时增加垂直 padding（现有 `ItemVerticalPad = 12.dp`
  配 24.dp 图标正好 48.dp）。
- 拖拽手柄要足够大，且必须提供非拖拽的替代路径（长按菜单里的上移/下移或排序动作）。

## 5. 文本与对比度

- 不要写死 `fontSize`；允许系统字体缩放。长文本给 `maxLines` + `TextOverflow.Ellipsis`
  而不是固定高度裁切。
- 颜色对比度依赖 `colorScheme` 的 on/container 配对，不要自己挑 `onSurface` 配自定义背景。
- 状态不能只靠颜色表达（延迟好/坏除了颜色还要有数值文本）。

## 6. 焦点与输入

- 表单字段给 `KeyboardOptions(keyboardType = …)`；只读下拉用
  `ExposedDropdownMenuAnchorType.PrimaryNotEditable` 并在获焦时收起键盘
  （`FormDropdownField` 已实现）。
- 弹窗打开时焦点应落在主输入框；关闭后 `focusManager.clearFocus()`。
- TV / 手柄场景：`MainActivity` 已处理 `KEYCODE_BUTTON_B`，新增全屏交互时注意
  可聚焦元素要有可见的焦点态。
