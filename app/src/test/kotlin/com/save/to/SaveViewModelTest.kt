package com.save.to

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SaveViewModelTest {

    private val application: Application = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private lateinit var viewModel: SaveViewModel

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { application.contentResolver } returns contentResolver
        // Use spyk to mock the internal open helper methods of SaveViewModel
        viewModel = spyk(SaveViewModel(application))
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private suspend fun awaitCompletion(viewModel: SaveViewModel): SaveViewModel.State.Complete {
        val start = System.currentTimeMillis()
        var state = viewModel.state.value
        while (state !is SaveViewModel.State.Complete && System.currentTimeMillis() - start < 3000) {
            delay(10)
            state = viewModel.state.value
        }
        return state as SaveViewModel.State.Complete
    }

    @Test
    fun getFileInfo_success() {
        val uri = Uri.parse("content://authority/path")
        val cursor = mockk<Cursor>(relaxed = true)
        every { contentResolver.getType(uri) } returns "image/png"
        every { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getString(0) } returns "test_image.png"

        val fileInfo = viewModel.getFileInfo(uri)

        assertEquals("test_image.png", fileInfo.first)
        assertEquals("image/png", fileInfo.second)
    }

    @Test
    fun getFileInfo_securityException_fallsBack() {
        val uri = Uri.parse("content://authority/path")
        every { contentResolver.getType(uri) } returns "image/png"
        every { contentResolver.query(uri, any(), any(), any(), any()) } throws SecurityException("Permission Denied")

        val fileInfo = viewModel.getFileInfo(uri)

        assertEquals("shared_file", fileInfo.first)
        assertEquals("image/png", fileInfo.second)
    }

    @Test
    fun saveSingleFile_success() = runTest {
        val sourceUri = Uri.parse("content://source")
        val destUri = Uri.parse("content://dest")
        val testData = "Hello SaveTo".toByteArray()

        val inputStream = ByteArrayInputStream(testData)
        val outputStream = ByteArrayOutputStream()

        every { contentResolver.openInputStream(sourceUri) } returns inputStream
        every { contentResolver.openOutputStream(destUri) } returns outputStream

        viewModel.saveSingleFile(sourceUri, destUri)

        val completeState = awaitCompletion(viewModel)

        assertEquals(1, completeState.copiedCount)
        assertEquals(1, completeState.totalCount)
        assertTrue(completeState.errors.isEmpty())
        assertArrayEquals(testData, outputStream.toByteArray())
    }

    @Test
    fun saveSingleFile_ioException_handlesGracefullyAndDeletesDest() = runTest {
        val sourceUri = Uri.parse("content://source")
        val destUri = Uri.parse("content://dest")

        every { contentResolver.openInputStream(sourceUri) } throws IOException("Disk Full")
        
        // Mock ViewModel's own deleteDocument method
        every { viewModel.deleteDocument(contentResolver, destUri) } just Runs

        viewModel.saveSingleFile(sourceUri, destUri)

        val completeState = awaitCompletion(viewModel)

        assertEquals(0, completeState.copiedCount)
        assertEquals(1, completeState.totalCount)
        assertEquals(1, completeState.errors.size)
        assertEquals("Disk Full", completeState.errors.first())

        verify { viewModel.deleteDocument(contentResolver, destUri) }
    }

    @Test
    fun saveMultipleFiles_partialFailure() = runTest {
        val sourceUri1 = Uri.parse("content://source1")
        val sourceUri2 = Uri.parse("content://source2")
        val treeUri = Uri.parse("content://authority/tree/treeId")
        val testData = "Multi File Data".toByteArray()

        val inputStream1 = ByteArrayInputStream(testData)
        val outputStream1 = ByteArrayOutputStream()

        val destUri1 = Uri.parse("content://authority/tree/treeId/document/file1")

        // Stub static methods through our ViewModel's open helpers
        every { viewModel.getTreeDocumentId(treeUri) } returns "treeId"
        every { viewModel.buildDocumentUriUsingTree(treeUri, "treeId") } returns Uri.parse("content://treeDocUri")
        every { viewModel.createDocument(contentResolver, any(), "text/plain", "file1.txt") } returns destUri1
        every { viewModel.createDocument(contentResolver, any(), "text/plain", "file2.txt") } returns null

        // Mock ContentResolver queries for metadata
        val cursor1 = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(sourceUri1, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) } returns cursor1
        every { cursor1.moveToFirst() } returns true
        every { cursor1.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor1.getString(0) } returns "file1.txt"
        every { contentResolver.getType(sourceUri1) } returns "text/plain"

        val cursor2 = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(sourceUri2, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) } returns cursor2
        every { cursor2.moveToFirst() } returns true
        every { cursor2.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor2.getString(0) } returns "file2.txt"
        every { contentResolver.getType(sourceUri2) } returns "text/plain"

        // Mock Streams
        every { contentResolver.openInputStream(sourceUri1) } returns inputStream1
        every { contentResolver.openOutputStream(destUri1) } returns outputStream1

        // Run multiple save
        viewModel.saveMultipleFiles(listOf(sourceUri1, sourceUri2), treeUri)

        val completeState = awaitCompletion(viewModel)

        assertEquals(1, completeState.copiedCount)
        assertEquals(2, completeState.totalCount)
        assertEquals(1, completeState.errors.size)
        assertTrue(completeState.errors.first().contains("Could not create file"))
        assertArrayEquals(testData, outputStream1.toByteArray())
    }
}
