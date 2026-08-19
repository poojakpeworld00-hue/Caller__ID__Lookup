package com.callerid.number.lookup.home.screen.main

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

interface HomeShellOwner {

    val hostActivity: AppCompatActivity

    val homeShellController: HomeShellDriver

    fun onShellBackExhausted()

    fun bringHostToFront()
}

val Fragment.homeShellHost: HomeShellOwner? get() = activity as? HomeShellOwner

val Fragment.homeShellController: HomeShellDriver? get() = homeShellHost?.homeShellController

val Fragment.homeShell: HomeShellFragment? get() = parentFragment as? HomeShellFragment
