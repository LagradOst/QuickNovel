package com.lagradost.quicknovel.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.loadPrimaryColor
import com.lagradost.quicknovel.compose.loadThemeMode
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute

class ResultFragment2 : Fragment() {
    private val viewModel: ResultViewModel2 by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(inflater.context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            CloudStreamTheme(
                mode = LocalContext.current.loadThemeMode(),
                primaryColor = LocalContext.current.loadPrimaryColor(),
            ) {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(arguments) {
                    val url = arguments?.getString("url")
                    val apiName = arguments?.getString("apiName")
                    val id = arguments?.getInt("id", -1)?.takeIf { it != -1 }

                    if (url != null && apiName != null && state.response?.url != url) {
                        viewModel.onAction(
                            ResultPageAction.LoadResult(
                                isPreview = false,
                                url = url,
                                apiName = apiName,
                                id = id
                            )
                        )
                    }
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