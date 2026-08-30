# UI code agent guide

These rules apply to the UI paths listed in the repository-root `AGENTS.md`. Follow both
guides; apply the precedence rule defined by the root guide.

## Compose architecture and state

- Implement every new screen and component with the existing Compose and Material 3
  stack. A new screen activity must extend `BaseComponentActivity` or
  `HelperBaseComponentActivity` and place its screen, ViewModel, and action contract in
  the package that owns its navigation entry. Do not add a fragment, ViewBinding layout,
  XML screen layout, or RecyclerView adapter. Do not migrate existing legacy UI outside
  task scope, and do not extend it or use it as the template for new UI.
- Keep durable screen state and business work in a ViewModel/repository and expose it as
  immutable observable state. Collect flows with `collectAsStateWithLifecycle()`.
  Compose-local state is only for transient presentation state; use `rememberSaveable`
  when that state must survive activity recreation.
- Durable state includes loaded data, selection by domain ID, validation results,
  progress, errors, and any value written to settings or a profile. Transient
  presentation state is limited to visual expansion, animation, scroll/focus position,
  and an uncommitted input value owned solely by the visible component. A composable
  must not perform repository, MMKV, import/export, routing, or native-core work.
- Send user operations through the owning feature's action/event contract and
  ViewModel. Main-screen operations must use `MainAction` and `MainViewModel`.
  Composables must not mutate a repository, `MmkvManager`, or `SettingsManager`
  directly.
- Use server GUIDs and group IDs for Compose keys, selection, saved state, and action
  parameters. Do not persist or dispatch a visible index, adapter position, or paging
  offset as identity. After filtering, sorting, subscription replacement, paging, or an
  asynchronous result, resolve the item again by its domain ID.
- Run blocking file, network, package-manager, and bitmap-decoding operations on
  `Dispatchers.IO`. Run CPU-bound parsing, sorting, filtering, and transformation on
  `Dispatchers.Default`. Launch both from the owning ViewModel or lifecycle scope.
  Publish results through a private `MutableStateFlow` exposed as `StateFlow`; do not
  read or write Compose snapshot state on either background dispatcher.

## Interaction, accessibility, and layout

- Give every icon with a click, long-click, toggle, or custom accessibility action a
  localized accessible name that states its action. Set `contentDescription = null` on
  every icon with no user action. Do not expose a raw URL,
  package ID, GUID, or duplicated descendant label when a row-level semantic node
  supplies the name.
- A row with one activation behavior must expose one focusable semantic node, one
  localized name, its current selected/on/off state, and one activation action. Merge or
  clear descendant semantics so a label, icon, checkbox, and switch do not become
  duplicate focus targets. Keep a descendant as a separate node only when it performs a
  different user action; give that node its own name and role. TalkBack, touch, Enter,
  Space, and D-pad center must invoke the same action for the same node.
- Key each focusable list item by its domain ID. Reordering or recomposition must keep
  focus on the node with that ID if it still exists. If a selection change removes or
  replaces the focused node, move focus exactly once to the final active item after the
  state update; do not announce or focus an intermediate item.
- Use `stringResource` for text created inside a composable and localized
  `Context.getString`/resources outside Compose. This rule covers visible labels,
  semantic labels, state descriptions, validation messages, errors, dialogs, snackbars,
  and toasts. Apply the root guide's locale and placeholder requirements to every key.
- Every form containing a text-editing control must use a vertically scrollable
  container. Inside a `Scaffold`, apply its `innerPadding`, call
  `consumeWindowInsets(innerPadding)`, and
  apply `imePadding()` to that container. Outside a `Scaffold`, apply `imePadding()` and
  consume the navigation-bar inset exactly once. With the IME visible, the user must be
  able to focus, read, edit, and activate both the first and last form controls without
  dismissing the IME.
- A shared component used by server screens must preserve edge-to-edge system-bar
  insets, light and dark themes, dynamic color enabled and disabled, and both single-
  and double-column layouts. A change to such a component requires rendering and
  interaction checks in each of those configurations; dynamic-color checks require a
  device or emulator that supports dynamic color.

## Validation mapping

Apply every row whose trigger matches the UI diff:

- ViewModel state, action mapping, validation, filtering, parsing, or selection logic:
  add or update a JVM test that asserts the initial state and every success, invalid-
  input, empty-input, and failure branch added or modified by the diff.
- `rememberSaveable`, `SavedStateHandle`, activity recreation, or restoration logic:
  recreate the activity and verify the final state by domain ID, not list position.
- Loading or data presentation: exercise every state represented by the owning state
  model among empty, loading, error, and populated; do not invent states absent from
  that model.
- Click, toggle, selection, or navigation behavior: verify touch, keyboard focus and
  activation, and D-pad focus and activation.
- Semantics, focus, accessible text, row merging, or icon meaning: inspect the semantics
  tree and run TalkBack on an emulator or device. Record the spoken label, state, focus
  order, and activation result.
- Form fields, scrolling, or insets: open the IME, traverse from the first control to the
  last control, and activate the primary action with the IME still visible.
- A setting, profile, routing rule, or other persisted value: perform the action, close
  and reopen the screen, and verify the value through the owning ViewModel/repository.

A screenshot verifies appearance only. It does not verify semantics, focus, activation,
state ownership, persistence, or recreation. Record every mapped check that did not run
under `Not run`; do not claim the corresponding behavior is verified.
