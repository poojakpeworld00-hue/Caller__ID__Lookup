package com.callerid.number.lookup.home.shell.screens

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.commons.models.RadioItem
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.databinding.ScreenLauncherSettingsBinding
import com.callerid.number.lookup.home.shell.ext.config
import com.callerid.number.lookup.home.shell.support.MAX_COLUMN_COUNT
import com.callerid.number.lookup.home.shell.support.MAX_ROW_COUNT
import com.callerid.number.lookup.home.shell.support.MIN_COLUMN_COUNT
import com.callerid.number.lookup.home.shell.support.MIN_ROW_COUNT
import com.callerid.number.lookup.home.shell.signals.ScreenLockAdminReceiver
import java.util.Locale
import kotlin.system.exitProcess

class BoardSettingsActivity : ShellBaseActivity() {

    private val binding by viewBinding(ScreenLauncherSettingsBinding::inflate)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.settingsNestedScrollviewVw))
        setupMaterialScrollListener(binding.settingsNestedScrollviewVw, binding.settingsAppbarVw)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbarVw, NavigationIcon.Arrow)

        setupCustomizeColors()
        setupUseEnglish()
        setupDoubleTapToLock()
        setupCloseAppDrawerOnOtherAppOpen()
        setupOpenKeyboardOnAppDrawer()
        setupDrawerColumnCount()
        setupDrawerSearchBar()
        setupShowDrawerAppLabels()
        setupHomeRowCount()
        setupHomeColumnCount()
        setupShowHomeAppLabels()
        setupLanguage()
        setupManageHiddenIcons()
        updateTextColors(binding.settingsHolderVw)

        arrayOf(
            binding.settingsColorCustomizationSectionLabelVw,
            binding.settingsGeneralSettingsLabelVw,
            binding.settingsDrawerSettingsLabelVw,
            binding.settingsHomeScreenLabelVw
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolderVw.setOnClickListener {
            // see ShellBaseActivity.withFossifyPackageNameSpoofed — startCustomizationActivity()
            // carries its own copy of the anti-rebrand check, separate from the one in onCreate()
            withFossifyPackageNameSpoofed { startCustomizationActivity() }
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolderVw.beVisibleIf(
            beVisible = (config.wasUseEnglishToggled || Locale.getDefault().language != "en")
                    && !isTiramisuPlus()
        )

        binding.settingsUseEnglishVw.isChecked = config.useEnglish
        binding.settingsUseEnglishHolderVw.setOnClickListener {
            binding.settingsUseEnglishVw.toggle()
            config.useEnglish = binding.settingsUseEnglishVw.isChecked
            exitProcess(0)
        }
    }

    private fun setupDoubleTapToLock() {
        val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        binding.settingsDoubleTapToLockVw.isChecked = devicePolicyManager.isAdminActive(
            ComponentName(this, ScreenLockAdminReceiver::class.java)
        )

        binding.settingsDoubleTapToLockHolderVw.setOnClickListener {
            val isLockDeviceAdminActive = devicePolicyManager.isAdminActive(
                ComponentName(this, ScreenLockAdminReceiver::class.java)
            )
            if (isLockDeviceAdminActive) {
                devicePolicyManager.removeActiveAdmin(
                    ComponentName(this, ScreenLockAdminReceiver::class.java)
                )
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this, ScreenLockAdminReceiver::class.java)
                )
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.lock_device_admin_hint)
                )
                startActivity(intent)
            }
        }
    }

    private fun setupOpenKeyboardOnAppDrawer() {
        binding.settingsOpenKeyboardOnAppDrawerHolderVw.beVisibleIf(config.showSearchBar)
        binding.settingsOpenKeyboardOnAppDrawerVw.isChecked = config.autoShowKeyboardInAppDrawer
        binding.settingsOpenKeyboardOnAppDrawerHolderVw.setOnClickListener {
            binding.settingsOpenKeyboardOnAppDrawerVw.toggle()
            config.autoShowKeyboardInAppDrawer = binding.settingsOpenKeyboardOnAppDrawerVw.isChecked
        }
    }

    private fun setupCloseAppDrawerOnOtherAppOpen() {
        binding.settingsCloseAppDrawerOnOtherAppVw.isChecked = config.closeAppDrawer
        binding.settingsCloseAppDrawerOnOtherAppHolderVw.setOnClickListener {
            binding.settingsCloseAppDrawerOnOtherAppVw.toggle()
            config.closeAppDrawer = binding.settingsCloseAppDrawerOnOtherAppVw.isChecked
        }
    }

    private fun setupDrawerColumnCount() {
        val currentColumnCount = config.drawerColumnCount
        binding.settingsDrawerColumnCountVw.text = currentColumnCount.toString()
        binding.settingsDrawerColumnCountHolderVw.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in 1..MAX_COLUMN_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            org.fossify.commons.R.plurals.column_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentColumnCount) {
                val newColumnCount = it as Int
                if (currentColumnCount != newColumnCount) {
                    config.drawerColumnCount = newColumnCount
                    setupDrawerColumnCount()
                }
            }
        }
    }

    private fun setupDrawerSearchBar() {
        val showSearchBar = config.showSearchBar
        binding.settingsShowSearchBarVw.isChecked = showSearchBar
        binding.settingsDrawerSearchHolderVw.setOnClickListener {
            binding.settingsShowSearchBarVw.toggle()
            config.showSearchBar = binding.settingsShowSearchBarVw.isChecked
            binding.settingsOpenKeyboardOnAppDrawerHolderVw.beVisibleIf(config.showSearchBar)
        }
    }

    private fun setupShowDrawerAppLabels() {
        binding.settingsShowDrawerAppLabelsVw.isChecked = config.showDrawerAppLabels
        binding.settingsShowDrawerAppLabelsHolderVw.setOnClickListener {
            binding.settingsShowDrawerAppLabelsVw.toggle()
            config.showDrawerAppLabels = binding.settingsShowDrawerAppLabelsVw.isChecked
        }
    }

    private fun setupHomeRowCount() {
        val currentRowCount = config.homeRowCount
        binding.settingsHomeScreenRowCountVw.text = currentRowCount.toString()
        binding.settingsHomeScreenRowCountHolderVw.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in MIN_ROW_COUNT..MAX_ROW_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            org.fossify.commons.R.plurals.row_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentRowCount) {
                val newRowCount = it as Int
                if (currentRowCount != newRowCount) {
                    config.homeRowCount = newRowCount
                    setupHomeRowCount()
                }
            }
        }
    }

    private fun setupHomeColumnCount() {
        val currentColumnCount = config.homeColumnCount
        binding.settingsHomeScreenColumnCountVw.text = currentColumnCount.toString()
        binding.settingsHomeScreenColumnCountHolderVw.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in MIN_COLUMN_COUNT..MAX_COLUMN_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            org.fossify.commons.R.plurals.column_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentColumnCount) {
                val newColumnCount = it as Int
                if (currentColumnCount != newColumnCount) {
                    config.homeColumnCount = newColumnCount
                    setupHomeColumnCount()
                }
            }
        }
    }

    private fun setupShowHomeAppLabels() {
        binding.settingsShowHomeAppLabelsVw.isChecked = config.showHomeAppLabels
        binding.settingsShowHomeAppLabelsHolderVw.setOnClickListener {
            binding.settingsShowHomeAppLabelsVw.toggle()
            config.showHomeAppLabels = binding.settingsShowHomeAppLabelsVw.isChecked
        }
    }

    @SuppressLint("NewApi")
    private fun setupLanguage() {
        binding.settingsLanguageVw.text = Locale.getDefault().displayLanguage
        binding.settingsLanguageHolderVw.beVisibleIf(isTiramisuPlus())
        binding.settingsLanguageHolderVw.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupManageHiddenIcons() {
        binding.settingsManageHiddenIconsHolderVw.setOnClickListener {
            startActivity(Intent(this, MaskedAppsActivity::class.java))
        }
    }
}
