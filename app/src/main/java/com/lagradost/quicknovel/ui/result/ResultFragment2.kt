package com.lagradost.quicknovel.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.cloudstream4.rememberAppSettings
import com.lagradost.cloudstream4.state.ObserveEffect
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.perfToColor
import com.lagradost.cloudstream4.theme.perfToMode
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.mihon.presentation.settings.collectAsState

class ResultFragment2 : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(inflater.context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            val settings = rememberAppSettings()
            val mode by settings.ui.theme.collectAsState()
            val primaryColor by settings.ui.primaryColor.collectAsState()
            val viewModel: ResultViewModel2 =
                viewModel(factory = ResultViewModel2.provideFactory(requireArguments()))

            CloudStreamTheme(
                mode = perfToMode(mode),
                primaryColor = perfToColor(primaryColor),
            ) {
                val state by viewModel.state.collectAsStateWithLifecycle()

                ObserveEffect(viewModel.effect) { _ ->
                    // Not yet implemented
                }
                ResultScreen(state, viewModel::onAction)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.apply {
            window?.navigationBarColor =
                colorFromAttribute(R.attr.primaryBlackBackground)
        }
    }
}