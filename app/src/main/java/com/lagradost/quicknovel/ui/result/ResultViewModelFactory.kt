package com.lagradost.quicknovel.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ResultViewModelFactory(private val resultRepository: ResultRepository)
    : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ResultViewModel(resultRepository) as T
    }
}

object InjectorUtils {
    // This will be called from QuotesActivity
    fun provideQuotesViewModelFactory(): ResultViewModelFactory {
        // ViewModelFactory needs a repository, which in turn needs a DAO from a database
        // The whole dependency tree is constructed right here, in one place
        val quoteRepository = ResultRepository.getInstance()
        return ResultViewModelFactory(quoteRepository)
    }
}