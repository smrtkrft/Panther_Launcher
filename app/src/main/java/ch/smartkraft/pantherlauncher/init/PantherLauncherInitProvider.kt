package ch.smartkraft.pantherlauncher.init

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import ch.smartkraft.pantherlauncher.PantherLauncher

class PantherLauncherInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context?.applicationContext ?: return false
        PantherLauncher.initialize(context)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null
}
