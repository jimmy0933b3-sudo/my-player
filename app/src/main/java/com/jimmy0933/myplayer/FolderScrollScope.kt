package com.jimmy0933.myplayer

/** Separate durable scroll bookmarks for every folder and every gallery tab. */
internal fun folderScrollScope(browserMode: String, folderKey: String?): String =
    if (browserMode == "FOLDERS") {
        val key = folderKey.orEmpty()
        "folder_${key.length}_$key"
    } else "browser_$browserMode"
