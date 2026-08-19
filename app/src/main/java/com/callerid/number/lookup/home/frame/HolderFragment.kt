package com.callerid.number.lookup.home.frame

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.engine.logPermissionResult
import com.callerid.number.lookup.home.store.StorageRegistry
import java.util.Locale

abstract class HolderFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null
    protected val binding: VB get() = _binding!!

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initObservers()
    }

    protected open fun initView() {}

    protected open fun initObservers() {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) logScreenView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) logScreenView()
    }

    private fun logScreenView() {
        context?.trackEvent("screen_${this::class.java.simpleName.lowercase(Locale.ROOT)}")
    }

    protected fun requestPermissionManaged(
        permission: String,
        launcher: ActivityResultLauncher<String>
    ) {
        val prefs = StorageRegistry(requireContext())
        when {

            !prefs.hasRequestedPermission(permission) -> {
                prefs.markPermissionRequested(permission)
                launcher.launch(permission)
            }

            shouldShowRequestPermissionRationale(permission) -> launcher.launch(permission)

            else -> openAppSettings()
        }
    }

    private val permissionChain = ArrayDeque<String>()
    private var onPermissionChainComplete: (() -> Unit)? = null
    private var lastChainPermission: String? = null

    private val chainLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        lastChainPermission?.let { context?.logPermissionResult(it, granted) }
        advancePermissionChain()
    }

    protected fun requestPermissionChain(
        permissions: List<String>,
        onComplete: () -> Unit
    ) {
        permissionChain.clear()
        permissionChain.addAll(permissions)
        onPermissionChainComplete = onComplete
        advancePermissionChain()
    }

    private fun advancePermissionChain() {
        val ctx = context ?: return
        val prefs = StorageRegistry(ctx)
        while (permissionChain.isNotEmpty()) {
            val permission = permissionChain.removeFirst()
            if (ContextCompat.checkSelfPermission(ctx, permission)
                == PackageManager.PERMISSION_GRANTED
            ) continue

            when {

                !prefs.hasRequestedPermission(permission) -> {
                    prefs.markPermissionRequested(permission)
                    lastChainPermission = permission
                    chainLauncher.launch(permission)
                }

                shouldShowRequestPermissionRationale(permission) -> {
                    lastChainPermission = permission
                    chainLauncher.launch(permission)
                }

                else -> openAppSettings()
            }
            return
        }

        val done = onPermissionChainComplete
        onPermissionChainComplete = null
        done?.invoke()
    }

    protected fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().packageName, null)
                )
            )
        }
    }

    private var pendingCallNumber: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        context?.logPermissionResult(Manifest.permission.CALL_PHONE, granted)
        val number = pendingCallNumber
        pendingCallNumber = null
        if (number != null) if (granted) startCall(number) else openDialer(number)
    }

    protected fun placeCall(number: String) {
        if (number.isBlank()) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCall(number)
        } else {
            pendingCallNumber = number
            requestPermissionManaged(Manifest.permission.CALL_PHONE, callPermissionLauncher)
        }
    }

    private fun startCall(number: String) {
        val placed = runCatching {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))); true
        }.getOrDefault(false)
        if (!placed) openDialer(number)
    }

    private fun openDialer(number: String) {
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    }
}
