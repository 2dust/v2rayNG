package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GroupServerFragment : Fragment() {
    private val ownerActivity: MainActivity
        get() = requireActivity() as MainActivity
    private val mainViewModel: MainViewModel by activityViewModels()
    private val subId: String by lazy { arguments?.getString(ARG_SUB_ID).orEmpty() }

    private val share_method: Array<out String> by lazy {
        ownerActivity.resources.getStringArray(R.array.share_method)
    }
    private val share_method_more: Array<out String> by lazy {
        ownerActivity.resources.getStringArray(R.array.share_method_more)
    }
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            ownerActivity.restartV2Ray()
        }
    }

    companion object {
        private const val ARG_SUB_ID = "subscriptionId"
        fun newInstance(subId: String) = GroupServerFragment().apply {
            arguments = Bundle().apply { putString(ARG_SUB_ID, subId) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    ServerListScreen(
                        mainViewModel = mainViewModel,
                        onSelect = { guid -> setSelectServer(guid) },
                        onMoreClick = { guid, profile, position ->
                            onMoreClick(guid, profile, position)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun onMoreClick(guid: String, profile: ProfileItem, position: Int) {
        val isCustom = profile.configType.isComplexType()
        val more = true // In the modern UI, we always use the "more" menu

        val (shareOptions, skip) = if (more) {
            val options = if (isCustom) share_method_more.asList().takeLast(3) else share_method_more.asList()
            options to if (isCustom) 2 else 0
        } else {
            val options = if (isCustom) share_method.asList().takeLast(1) else share_method.asList()
            options to if (isCustom) 2 else 0
        }

        shareServer(guid, profile, position, shareOptions, skip)
    }

    override fun onResume() {
        super.onResume()
        LogUtil.d(AppConfig.TAG, "GroupServerFragment onResume: subId=$subId")
        mainViewModel.subscriptionIdChanged(subId)
    }

    /**
     * Shares server configuration
     * Displays a dialog with sharing options and executes the selected action
     * @param guid The server unique identifier
     * @param profile The server configuration
     * @param position The position in the list
     * @param shareOptions The list of share options
     * @param skip The number of options to skip
     */
    private fun shareServer(guid: String, profile: ProfileItem, position: Int, shareOptions: List<String>, skip: Int) {
        AlertDialog.Builder(ownerActivity).setItems(shareOptions.toTypedArray()) { _, i ->
            try {
                when (i + skip) {
                    0 -> ownerActivity.showQRCode(guid)
                    1 -> share2Clipboard(guid)
                    2 -> shareFullContent(guid)
                    3 -> editServer(guid, profile)
                    4 -> removeServer(guid, position)
                    else -> ownerActivity.toast("else")
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error when sharing server", e)
            }
        }.show()
    }

    /**
     * Shares server configuration to clipboard
     * @param guid The server unique identifier
     */
    private fun share2Clipboard(guid: String) {
        if (AngConfigManager.share2Clipboard(ownerActivity, guid) == 0) {
            ownerActivity.toastSuccess(R.string.toast_success)
        } else {
            ownerActivity.toastError(R.string.toast_failure)
        }
    }

    /**
     * Shares full server configuration content to clipboard
     * @param guid The server unique identifier
     */
    private fun shareFullContent(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(ownerActivity, guid)
            launch(Dispatchers.Main) {
                if (result == 0) {
                    ownerActivity.toastSuccess(R.string.toast_success)
                } else {
                    ownerActivity.toastError(R.string.toast_failure)
                }
            }
        }
    }

    /**
     * Edits server configuration
     * Opens appropriate editing interface based on configuration type
     * @param guid The server unique identifier
     * @param profile The server configuration
     */
    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            else -> ServerActivity::class.java
        }

        val intent = Intent(ownerActivity, activityClass)
            .putExtra("guid", guid)
            .putExtra("isRunning", mainViewModel.isRunning.value)
            .putExtra("createConfigType", profile.configType.value)
            .putExtra("subscriptionId", subId)

        launcher.launch(intent)
    }

    /**
     * Removes server configuration
     * Handles confirmation dialog and related checks
     * @param guid The server unique identifier
     * @param position The position in the list
     */
    private fun removeServer(guid: String, position: Int) {
        if (guid == MmkvManager.getSelectServer()) {
            ownerActivity.toast(R.string.toast_action_not_allowed)
            return
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
            AlertDialog.Builder(ownerActivity).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    removeServerSub(guid)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            removeServerSub(guid)
        }
    }

    /**
     * Executes the actual server removal process
     * @param guid The server unique identifier
     */
    private fun removeServerSub(guid: String) {
        mainViewModel.removeServer(guid)
        mainViewModel.updateSelectedGuid()
        ownerActivity.refreshGroupTabTitles()
    }

    /**
     * Sets the selected server
     * Updates UI and restarts service if needed
     * @param guid The server unique identifier to select
     */
    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid != selected) {
            MmkvManager.setSelectServer(guid)
            mainViewModel.updateSelectedGuid()

            if (mainViewModel.isRunning.value == true) {
                ownerActivity.restartV2Ray()
            }
        }
    }

    /**
     * Scrolls to the currently selected server in the LazyColumn
     */
    fun scrollToSelectedServer() {
        // In Compose, we would need to use LazyListState to scroll.
        // For now, this is a placeholder.
    }
}
