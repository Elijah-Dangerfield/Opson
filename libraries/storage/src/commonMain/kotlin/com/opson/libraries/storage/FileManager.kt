package com.opson.libraries.storage

import okio.Path

interface FileManager {
    fun createFile(name: String): Path

    fun deleteFile(name: String)

    fun deleteAll()
}