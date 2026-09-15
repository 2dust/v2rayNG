# Automation app access

Open **Settings → Automation app access** (under Advanced) and select the apps allowed to
start or stop v2rayNG and import or replace configurations without confirmation.
Leave the picker to save the selection. The list is empty on a fresh installation and after upgrading
from versions without access control. It is independent of per-app proxy routing.
Removing an app revokes its access. Grant it again after reinstalling it or changing
its signing certificate. Grants are device-local and excluded from backups.

## Tasker / Locale plugin actions

After allowing the automation host, open and save each v2rayNG plugin action in that
host. Existing actions must be saved again to receive authorization. The editor
checks Android's `Activity.getCallingPackage()` before loading profiles and again
when returning its result. It returns a random per-app capability inside the existing
Locale configuration bundle. Hosts should keep that bundle private, retain all its
fields, and return it unchanged with `FIRE_SETTING` (except for the intended command).
Exporting or sharing a task containing the capability delegates that grant to whoever
receives it; unchecking the host revokes all copies. Rechecking creates a new token.

The bundle retains `tasker_extra_bundle_switch` and `tasker_extra_bundle_guid`, and adds
`tasker_extra_bundle_package` and `tasker_extra_bundle_token`. The package field is a
lookup key, not proof of identity. A package name alone never grants access.

## Direct broadcast clients

On Android 14/API 34 and later, a selected app can send the existing explicit
`com.twofortyfouram.locale.intent.action.FIRE_SETTING` broadcast to
`com.v2ray.ang.receiver.TaskerReceiver`, using
`BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()` with the
`sendBroadcast` overload accepting options. The receiver checks the platform-provided
sender UID against the selected, installed app. Android treats packages sharing one
UID as the same security principal.

When Android does not expose the sender UID (including every version before API 34),
the client must configure a plugin action via `EDIT_SETTING` and retain its returned
capability. Raw anonymous broadcasts, including older manually configured Send Intent
actions, are rejected. Never use `Binder.getCallingUid()` or a caller-supplied referrer
to identify the broadcast sender.

`Default` starts the currently selected profile. A specific GUID must identify an
existing profile before selection changes. Stop requests still use a nonempty GUID,
as in the existing plugin contract. Android VPN consent, foreground-service limits,
and the selected operating mode still apply.

Launcher widgets use the app's immutable PendingIntent and do not need an allowlist
entry. Their receiver is private to prevent direct broadcasts bypassing this setting.

## External configuration imports

Shared text and `v2rayng://install-config` / `v2rayng://install-sub` links use the same
Automation app access grants. Allowed, authenticated automation apps retain the existing
replacement behavior for unattended updates. Removing the grant also removes this access.

On API 34+, launch the import activity with
`ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle()`. Android's
`Activity.getLaunchedFromUid()` identifies the sender. When Android does not expose
the launcher identity (including versions before API 34), include the saved Locale configuration bundle
as `com.twofortyfouram.locale.intent.extra.BUNDLE`, retaining its package and capability
fields. Never put the capability in a URL. A known platform identity takes precedence
over supplied credentials; referrers and claimed package names alone are not trusted.
`getCallingPackage()` is not used here: result forwarding can make it identify a
different app from the sender of the configuration.

Other callers require explicit confirmation before any configuration import or
subscription download. These one-off imports append profiles instead of replacing the
default group. Cancelling leaves the configuration untouched. No automatic access grant
is created by confirming an import.

Platform references: [activity launch identity](https://developer.android.com/reference/android/app/Activity#getLaunchedFromUid()),
[broadcast sender identity](https://developer.android.com/reference/android/content/BroadcastReceiver#getSentFromUid()),
[sharing identity](https://developer.android.com/reference/android/app/BroadcastOptions#setShareIdentityEnabled(boolean)),
[activity caller identity](https://developer.android.com/reference/android/app/Activity#getCallingPackage()).
