package com.callerid.number.lookup.home.launcher.dialogs

import android.app.Activity
import android.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import com.callerid.number.lookup.home.databinding.ModalRenameItemBinding
import com.callerid.number.lookup.home.launcher.extensions.homeScreenGridItemsDB
import com.callerid.number.lookup.home.launcher.models.BoardItem

class RelabelItemDialog(val activity: Activity, val item: BoardItem, val callback: () -> Unit) {

    init {
        val binding = ModalRenameItemBinding.inflate(activity.layoutInflater)
        val view = binding.root
        binding.renameItemEdittext.setText(item.title)

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, org.fossify.commons.R.string.rename) { alertDialog ->
                    alertDialog.showKeyboard(binding.renameItemEdittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.renameItemEdittext.value
                        if (newTitle.isNotEmpty()) {
                            ensureBackgroundThread {
                                val result = activity.homeScreenGridItemsDB.updateItemTitle(newTitle, item.id!!)
                                if (result == 1) {
                                    callback()
                                    alertDialog.dismiss()
                                } else {
                                    activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                                }
                            }
                        } else {
                            activity.toast(org.fossify.commons.R.string.value_cannot_be_empty)
                        }
                    }
                }
            }
    }
}
