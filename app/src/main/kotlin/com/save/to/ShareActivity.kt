package com.save.to

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ShareActivity : ComponentActivity() {

    private val viewModel: SaveViewModel by viewModels()

    class CreateDocumentContract : ActivityResultContract<Pair<String, String>, Uri?>() {
        override fun createIntent(context: Context, input: Pair<String, String>): Intent {
            val (mimeType, filename) = input
            return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, filename)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == Activity.RESULT_OK) intent?.data else null
        }
    }

    private val createDocumentLauncher = registerForActivityResult(CreateDocumentContract()) { uri ->
        if (uri != null) {
            val sourceUri = singleSourceUri
            if (sourceUri != null) {
                viewModel.saveSingleFile(sourceUri, uri)
            } else {
                showErrorDialog(getString(R.string.error_file_path_missing))
            }
        } else {
            Toast.makeText(this, R.string.save_cancelled, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val sourceUris = multipleSourceUris
            if (!sourceUris.isNullOrEmpty()) {
                viewModel.saveMultipleFiles(sourceUris, uri)
            } else {
                showErrorDialog(getString(R.string.error_files_list_missing))
            }
        } else {
            Toast.makeText(this, R.string.save_cancelled, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private var singleSourceUri: Uri? = null
    private var multipleSourceUris: List<Uri>? = null
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeState()

        if (savedInstanceState != null) {
            singleSourceUri = getParcelableCompat(savedInstanceState, KEY_SINGLE_URI)
            multipleSourceUris = getParcelableArrayListCompat(savedInstanceState, KEY_MULTI_URIS)
        } else {
            handleIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        singleSourceUri?.let { outState.putParcelable(KEY_SINGLE_URI, it) }
        multipleSourceUris?.let { outState.putParcelableArrayList(KEY_MULTI_URIS, ArrayList(it)) }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is SaveViewModel.State.Idle -> dismissProgress()
                    is SaveViewModel.State.Saving -> showProgress()
                    is SaveViewModel.State.Complete -> handleComplete(state)
                }
            }
        }
    }

    private fun handleComplete(result: SaveViewModel.State.Complete) {
        dismissProgress()
        viewModel.consumeResult()

        if (result.errors.isEmpty()) {
            val message = if (result.totalCount == 1) {
                getString(R.string.saved_successfully)
            } else {
                getString(R.string.saved_n_files, result.copiedCount)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
        } else if (result.totalCount == 1) {
            showErrorDialog(getString(R.string.error_save_file, result.errors.first()))
        } else {
            showErrorDialog(
                getString(
                    R.string.error_saved_partial,
                    result.copiedCount,
                    result.totalCount,
                    result.errors.joinToString("\n")
                )
            )
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            showErrorDialog(getString(R.string.error_intent_empty))
            return
        }

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = getParcelableExtraCompat<Uri>(intent, Intent.EXTRA_STREAM)
                if (uri != null) {
                    if (!canReadUri(uri)) {
                        showErrorDialog(getString(R.string.error_permission_denied))
                        return
                    }
                    singleSourceUri = uri
                    try {
                        val (name, mime) = viewModel.getFileInfo(uri)
                        createDocumentLauncher.launch(Pair(mime, name))
                    } catch (e: Exception) {
                        showErrorDialog(getString(R.string.error_read_metadata, e.localizedMessage ?: e.message))
                    }
                } else {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        saveTextAsFile(sharedText)
                    } else {
                        showErrorDialog(getString(R.string.error_no_content))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = getParcelableArrayListExtraCompat<Uri>(intent, Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    multipleSourceUris = uris
                    openDocumentTreeLauncher.launch(null)
                } else {
                    showErrorDialog(getString(R.string.error_no_files))
                }
            }
            else -> {
                showErrorDialog(getString(R.string.error_unsupported_action, intent.action))
            }
        }
    }

    private fun saveTextAsFile(text: String) {
        singleSourceUri = null
        val textFilename = getString(R.string.default_text_filename)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cacheFile = File(cacheDir, textFilename)
                cacheFile.writeText(text)
                val cacheUri = Uri.fromFile(cacheFile)
                withContext(Dispatchers.Main) {
                    singleSourceUri = cacheUri
                    createDocumentLauncher.launch(Pair("text/plain", textFilename))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorDialog(getString(R.string.error_process_text, e.localizedMessage ?: e.message))
                }
            }
        }
    }

    private fun showProgress() {
        if (progressDialog?.isShowing == true) return
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            setPadding(50, 50, 50, 50)
        }
        progressDialog = AlertDialog.Builder(this)
            .setTitle(R.string.saving)
            .setView(progressBar)
            .setCancelable(false)
            .show()
    }

    private fun dismissProgress() {
        progressDialog?.let { dialog ->
            if (!isFinishing && !isDestroyed && dialog.isShowing) {
                dialog.dismiss()
            }
        }
        progressDialog = null
    }

    private fun showErrorDialog(message: String) {
        if (isFinishing || isDestroyed) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun canReadUri(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> getParcelableExtraCompat(intent: Intent, key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, T::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> getParcelableArrayListExtraCompat(intent: Intent, key: String): ArrayList<T>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, T::class.java)
        } else {
            intent.getParcelableArrayListExtra(key)
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> getParcelableCompat(bundle: Bundle, key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable(key, T::class.java)
        } else {
            bundle.getParcelable(key)
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> getParcelableArrayListCompat(bundle: Bundle, key: String): ArrayList<T>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelableArrayList(key, T::class.java)
        } else {
            bundle.getParcelableArrayList(key)
        }
    }

    companion object {
        private const val KEY_SINGLE_URI = "single_source_uri"
        private const val KEY_MULTI_URIS = "multi_source_uris"
    }
}
