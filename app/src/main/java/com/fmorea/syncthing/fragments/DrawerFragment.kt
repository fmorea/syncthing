package com.fmorea.syncthing.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fmorea.syncthing.service.SyncthingService

class DrawerFragment : Fragment(), SyncthingService.OnServiceStateChangeListener {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = View(requireContext())

    override fun onServiceStateChange(currentState: SyncthingService.State?) {}
    fun drawerOpened() {}
}
