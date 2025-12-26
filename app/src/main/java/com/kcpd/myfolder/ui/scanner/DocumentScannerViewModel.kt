package com.kcpd.myfolder.ui.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.kcpd.myfolder.domain.usecase.ImportMediaUseCase
import com.kcpd.myfolder.domain.usecase.ImportProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class DocumentScannerViewModel @Inject constructor(
    private val importMediaUseCase: ImportMediaUseCase
) : ViewModel() {

    /**
     * Import PDF files from scanner with specific folder ID.
     */
    fun importPdf(uri: Uri, folderId: String?): Flow<ImportProgress> {
        return importMediaUseCase.importFiles(listOf(uri), folderId)
    }
}
