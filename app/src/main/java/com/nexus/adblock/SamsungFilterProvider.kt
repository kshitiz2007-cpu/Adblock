package com.nexus.adblock

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class SamsungFilterProvider : ContentProvider() {
    private lateinit var filterFile: File
    override fun onCreate(): Boolean {
        filterFile = File(requireNotNull(context).filesDir, FilterListRepository.FILE_NAME)
        if (!filterFile.exists()) filterFile.writeText(FilterListRepository.defaultRules)
        return true
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Filters are read-only")
        if (!filterFile.exists()) filterFile.writeText(FilterListRepository.defaultRules)
        return ParcelFileDescriptor.open(filterFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri): String = "text/plain"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
}
