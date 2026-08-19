package com.callerid.number.lookup.home.shell.sheets

import android.app.Activity
import android.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import com.callerid.number.lookup.home.databinding.DlgRenameItemBinding
import com.callerid.number.lookup.home.shell.ext.homeScreenGridItemsDB
import com.callerid.number.lookup.home.shell.entities.BoardItem

class RelabelItemDialog(val activity: Activity, val item: BoardItem, val callback: () -> Unit) {

    init {
        val binding = DlgRenameItemBinding.inflate(activity.layoutInflater)
        val view = binding.root
        binding.renameItemEdittextVw.setText(item.title)

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, org.fossify.commons.R.string.rename) { alertDialog ->
                    alertDialog.showKeyboard(binding.renameItemEdittextVw)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.renameItemEdittextVw.value
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
