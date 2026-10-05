package com.lagradost.cloudstream4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream4.compose.Screen
import com.lagradost.cloudstream4.compose.createComposeView

abstract class BaseComposeFragment : Fragment(), Screen {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createComposeView(inflater, container, savedInstanceState)

    /*
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        this.view?.let { view ->
            fixSystemBarsPadding(
                view
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixSystemBarsPadding(
            view
        )
    }*/
}