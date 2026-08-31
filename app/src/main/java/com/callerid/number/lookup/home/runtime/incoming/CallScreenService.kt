package com.callerid.number.lookup.home.runtime.incoming

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.callerid.number.lookup.home.store.BlockListRegistry

/**
 * Also raises the **caller-ID card** for calls we let through, rather than waiting for the
 * RINGING broadcast. This is the better trigger: it fires before the phone rings, the number
 * comes from [Call.Details] so it needs no READ_PHONE_STATE, and holding the role is itself
 * the background-start exemption the card needs.
 *
 * [PhoneStateReceiver] still raises the card on RINGING — that is the only path on
 * pre-Android-10 devices and whenever another app holds the role. The two overlap whenever we
 * do hold it, so [IdentOverlayService.start] dedupes them.
 *
 * The card is only *raised* here; there is no call-end callback on a screening service (the
 * system unbinds right after [respondToCall]), so dismissal stays with [PhoneStateReceiver]
 * and [com.callerid.number.lookup.home.runtime.CallEndGuard].
 */
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

        if (isIncoming && !block && !number.isNullOrBlank()) {
            Log.d(TAG, "raising caller-ID card from screening: $number")
            IdentOverlayService.start(this, number)
        }
    }

    companion object {
        private const val TAG = "CallScreening"
    }
}
