package com.callerid.number.lookup.home.runtime.incoming

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.callerid.number.lookup.home.store.BlockListRegistry

class CallScreenService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else true

        val number = callDetails.handle?.schemeSpecificPart
        val block = isIncoming && !number.isNullOrBlank() &&
                BlockListRegistry(this).isBlocked(number)

        if (block) Log.d(TAG, "blocked incoming call screened: $number")

        val response = CallResponse.Builder()
            .setDisallowCall(block)
            .setRejectCall(block)
            .setSkipCallLog(false)
            .setSkipNotification(block)
            .build()

        respondToCall(callDetails, response)
    }

    companion object {
        private const val TAG = "CallScreening"
    }
}
