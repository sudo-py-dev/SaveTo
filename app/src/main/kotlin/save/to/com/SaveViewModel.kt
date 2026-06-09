package save.to.com

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SaveViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface State {
        data object Idle : State
        data object Saving : State
        data class Complete(
            val copiedCount: Int,
            val totalCount: Int,
            val errors: List<String>
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val resolver: ContentResolver
        get() = getApplication<Application>().contentResolver

    fun saveSingleFile(sourceUri: Uri, destUri: Uri) {
        if (_state.value is State.Saving) return
        _state.value = State.Saving

        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            var errorDetail: String? = null

            try {
                val input = resolver.openInputStream(sourceUri)
                    ?: throw IOException("Could not open input stream")
                val output = resolver.openOutputStream(destUri)
                    ?: throw IOException("Could not open output stream")

                input.use { src ->
                    output.use { dst ->
                        copyStream(src, dst)
                        dst.flush()
                        success = true
                    }
                }
            } catch (e: Exception) {
                errorDetail = e.localizedMessage ?: e.message ?: "Unknown I/O error"
                tryDeleteDocument(destUri)
            } finally {
                cleanupCacheFile(sourceUri)
            }

            _state.value = State.Complete(
                copiedCount = if (success) 1 else 0,
                totalCount = 1,
                errors = if (errorDetail != null) listOf(errorDetail) else emptyList()
            )
        }
    }

    fun saveMultipleFiles(sourceUris: List<Uri>, treeUri: Uri) {
        if (_state.value is State.Saving) return
        _state.value = State.Saving

        viewModelScope.launch(Dispatchers.IO) {
            var copiedCount = 0
            val errors = mutableListOf<String>()

            for (sourceUri in sourceUris) {
                ensureActive()
                val name = try {
                    getFileInfo(sourceUri).first
                } catch (_: Exception) {
                    "Unknown"
                }

                try {
                    val (resolvedName, mime) = getFileInfo(sourceUri)
                    val destUri = createDocumentInTree(treeUri, mime, resolvedName)
                    if (destUri == null) {
                        errors.add("$resolvedName: Could not create file")
                        continue
                    }

                    val input = resolver.openInputStream(sourceUri)
                        ?: throw IOException("Could not open input for $resolvedName")
                    val output = resolver.openOutputStream(destUri)
                        ?: throw IOException("Could not open output for $resolvedName")

                    input.use { src ->
                        output.use { dst ->
                            copyStream(src, dst)
                            dst.flush()
                            copiedCount++
                        }
                    }
                } catch (e: Exception) {
                    errors.add("$name: ${e.localizedMessage ?: e.message}")
                }
            }

            _state.value = State.Complete(
                copiedCount = copiedCount,
                totalCount = sourceUris.size,
                errors = errors
            )
        }
    }

    fun consumeResult() {
        _state.value = State.Idle
    }

    fun getFileInfo(uri: Uri): Pair<String, String> {
        var name = "shared_file"
        var mime = resolver.getType(uri) ?: "application/octet-stream"

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val resolvedName = cursor.getString(nameIndex)
                            if (!resolvedName.isNullOrEmpty()) {
                                name = sanitizeFilename(resolvedName)
                            }
                        }
                    }
                }
            } catch (_: SecurityException) {
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { path ->
                name = sanitizeFilename(File(path).name)
            }
        }

        if (mime == "*/*" || mime == "application/octet-stream") {
            val ext = MimeTypeMap.getFileExtensionFromUrl(Uri.encode(name))
            if (!ext.isNullOrEmpty()) {
                val resolvedMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                if (resolvedMime != null) {
                    mime = resolvedMime
                }
            }
        }

        if (mime == "*/*") {
            mime = "application/octet-stream"
        }

        return Pair(name, mime)
    }

    private fun createDocumentInTree(treeUri: Uri, mimeType: String, displayName: String): Uri? {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            DocumentsContract.createDocument(resolver, parentUri, mimeType, displayName)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        withContext(Dispatchers.IO) {
            while (input.read(buffer).also { bytesRead = it } != -1) {
                ensureActive()
                output.write(buffer, 0, bytesRead)
            }
        }
    }

    private fun cleanupCacheFile(uri: Uri) {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { path ->
                val file = File(path)
                val cacheDir = getApplication<Application>().cacheDir
                if (file.parentFile?.canonicalPath == cacheDir.canonicalPath) {
                    file.delete()
                }
            }
        }
    }

    private fun tryDeleteDocument(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (_: Exception) {
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "shared_file" }
    }

    companion object {
        private const val BUFFER_SIZE = 8192
    }
}
