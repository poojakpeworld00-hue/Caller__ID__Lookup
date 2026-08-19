package com.callerid.admesh.surface.tally.panels

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.callerid.admesh.surface.tally.lists.LogCallAdapter
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.store.CallHistorySource

class CallStreamFragment : Fragment() {

    private val adapter = LogCallAdapter(::callNumber)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.board_recent_calls, container, false)

        val recycler = view.findViewById<RecyclerView>(R.id.rollRecents)
        val empty = view.findViewById<TextView>(R.id.lblEmpty)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val calls = loadRecentCalls()
        adapter.submit(calls)

        val isEmpty = calls.isEmpty()
        recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE

        return view
    }

    private fun loadRecentCalls() = try {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) emptyList()
        else CallHistorySource(requireContext()).getCalls(limit = 200)
            .filter { it.number.isNotBlank() && !it.number.equals("Unknown", ignoreCase = true) }
            .distinctBy { it.number }
            .take(30)
    } catch (e: Exception) {
        emptyList()
    }

    private var pendingCallNumber: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val number = pendingCallNumber
        pendingCallNumber = null
        if (number != null) if (granted) startDirectCall(number) else openDialer(number)
    }

    private fun callNumber(number: String) {
        if (!isAdded || number.isBlank()) return
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startDirectCall(number)
        } else {
            pendingCallNumber = number
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun startDirectCall(number: String) {
        val placed = runCatching {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))); true
        }.getOrDefault(false)
        if (!placed) openDialer(number)
    }

    private fun openDialer(number: String) {
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    }
}
