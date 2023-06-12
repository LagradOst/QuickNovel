package com.lagradost.quicknovel.ui.history

import android.content.DialogInterface
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.BaseApplication.Companion.getActivity
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.removeKeys
import com.lagradost.quicknovel.BookDownloader
import com.lagradost.quicknovel.BookDownloader.createQuickStream
import com.lagradost.quicknovel.BookDownloader.openQuickStream
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistoryViewModel : ViewModel() {

    private fun updateHistory() {
        ioSafe {
            val list = ArrayList<ResultCached>()
            val keys = getKeys(HISTORY_FOLDER) ?: return@ioSafe
            for (k in keys) {
                val res =
                    getKey<ResultCached>(k) ?: continue
                list.add(res)
            }
            list.sortBy { -it.cachedTime }
            cards.postValue(list)
        }
    }

    init {
        updateHistory()
    }

    val cards: MutableLiveData<ArrayList<ResultCached>> by lazy {
        MutableLiveData<ArrayList<ResultCached>>()
    }

    fun deleteAllAlert() {
        val act = activity ?: return

        act.runOnUiThread {
            val dialogClickListener =
                DialogInterface.OnClickListener { _, which ->
                    when (which) {
                        DialogInterface.BUTTON_POSITIVE -> {
                            removeKeys(HISTORY_FOLDER)
                            updateHistory()
                        }

                        DialogInterface.BUTTON_NEGATIVE -> {
                        }
                    }
                }
            // TODO MAKE STRINGS

            val builder: AlertDialog.Builder =
                AlertDialog.Builder(act)
            builder.setMessage("Are you sure?\nAll history will be lost.")
                .setTitle("Remove History")
                .setPositiveButton("Remove", dialogClickListener)
                .setNegativeButton("Cancel", dialogClickListener)
                .show()
        }
    }

    fun open(card: ResultCached) {
        loadResult(card.source, card.apiName)
    }

    fun deleteAlert(card: ResultCached) {
        val act = activity ?: return
        act.runOnUiThread {
            val dialogClickListener =
                DialogInterface.OnClickListener { _, which ->
                    when (which) {
                        DialogInterface.BUTTON_POSITIVE -> {
                            delete(card)
                        }

                        DialogInterface.BUTTON_NEGATIVE -> {
                        }
                    }
                }
            // TODO MAKE STRINGS
            val builder: AlertDialog.Builder =
                AlertDialog.Builder(act)
            builder.setMessage("This remove ${card.name} from your history.\nAre you sure?")
                .setTitle("Remove")
                .setPositiveButton("Remove", dialogClickListener)
                .setNegativeButton("Cancel", dialogClickListener)
                .show()
        }
    }

    fun delete(card: ResultCached) {
        removeKey(HISTORY_FOLDER, card.id.toString())
        updateHistory()
    }

    fun stream(card: ResultCached) =
        ioSafe {
            val data = withContext(Dispatchers.IO) {
                val api = Apis.getApiFromName(card.apiName)
                api.load(card.source)
            }
            if (data is Resource.Success) {
                val res = data.value

                if (res.data.isEmpty()) {
                    showToast(R.string.no_chapters_found, Toast.LENGTH_SHORT)
                    return@ioSafe
                }

                val uri = withContext(Dispatchers.IO) {
                    createQuickStream(
                        BookDownloader.QuickStreamData(
                            BookDownloader.QuickStreamMetaData(
                                res.author,
                                res.name,
                                card.apiName,
                            ),
                            res.posterUrl,
                            res.data.toMutableList()
                        )
                    )
                }
                openQuickStream(uri)
            } else {
                showToast(R.string.error_loading_novel, Toast.LENGTH_SHORT)
            }
        }

}