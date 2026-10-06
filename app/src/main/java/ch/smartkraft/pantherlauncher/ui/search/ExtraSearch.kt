package ch.smartkraft.pantherlauncher.ui.search

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import ch.smartkraft.common.showShortToast
import ch.smartkraft.pantherlauncher.MainActivity
import ch.smartkraft.pantherlauncher.R
import ch.smartkraft.pantherlauncher.data.AppListItem
import ch.smartkraft.pantherlauncher.data.CONTACT_PREFIX
import ch.smartkraft.pantherlauncher.data.Constants
import ch.smartkraft.pantherlauncher.data.FILE_PREFIX
import ch.smartkraft.pantherlauncher.data.PERMISSION_PREFIX
import ch.smartkraft.common.AppLogger
import ch.smartkraft.common.getLocalizedString

/**
 * The search continues in the user's files and contacts when no app matches.
 * The results are returned as [AppListItem]s so that they fit into the drawer list;
 * [AppListItem.isSearchExtra] tells them apart from apps.
 */
class ExtraSearch(private val context: Context) {

    private val user = Process.myUserHandle()

    /** Files first, contacts only when no file matched. */
    fun search(query: String, scope: Constants.SearchScope): List<AppListItem> {
        val results = mutableListOf<AppListItem>()
        if (scope.files) {
            if (hasFilesPermission()) results += files(query) else results += permissionRow(FILES)
        }
        if (scope.contacts && results.none { it.isFileResult }) {
            if (hasContactsPermission()) results += contacts(query) else results += permissionRow(CONTACTS)
        }
        return results
    }

    /** Opens a result; returns true when the drawer should close afterwards. */
    fun open(activity: Activity, item: AppListItem): Boolean {
        return try {
            when {
                item.isPermissionRequest -> {
                    requestPermission(activity, item.activityClass.removePrefix(PERMISSION_PREFIX))
                    false
                }

                item.isFileResult -> {
                    val uri = item.activityClass.removePrefix(FILE_PREFIX).toUri()
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    true
                }

                item.isContactResult -> {
                    val uri = item.activityClass.removePrefix(CONTACT_PREFIX).toUri()
                    activity.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }

                else -> false
            }
        } catch (_: ActivityNotFoundException) {
            context.showShortToast(getLocalizedString(R.string.search_no_app_to_open))
            false
        }
    }

    fun hasFilesPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission(activity: Activity, what: String) {
        // The drawer and the typed query should still be there when the user comes back
        (activity as? MainActivity)?.keepCurrentScreenWhileAway()
        when (what) {
            CONTACTS -> ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CODE)
            FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Documents are only visible with "All files access"; media permissions would show photos and music alone
                val own = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
                try {
                    activity.startActivity(own)
                } catch (_: ActivityNotFoundException) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE)
            }
        }
    }

    private fun permissionRow(what: String): AppListItem {
        val label = if (what == FILES) R.string.search_permission_files else R.string.search_permission_contacts
        return AppListItem(getLocalizedString(label), "", PERMISSION_PREFIX + what, user, customTag = "")
    }

    private fun files(query: String): List<AppListItem> {
        val volume = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )
        // Folders have no MIME type; '%' and '_' in the query must not act as wildcards
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\' AND ${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL"
        val results = mutableListOf<AppListItem>()
        try {
            context.contentResolver.query(
                volume, projection, selection, arrayOf("%$escaped%"),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                while (cursor.moveToNext() && results.size < LIMIT) {
                    val name = cursor.getString(nameCol) ?: continue
                    val uri = ContentUris.withAppendedId(volume, cursor.getLong(idCol))
                    val folder = cursor.getString(pathCol)?.trimEnd('/').orEmpty()
                    results += AppListItem(name, "", FILE_PREFIX + uri, user, customTag = folder)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ExtraSearch", "File search failed: ${e.message}", e)
        }
        AppLogger.d("ExtraSearch", "${results.size} files for '$query' (manager=${hasFilesPermission()})")
        return results
    }

    private fun contacts(query: String): List<AppListItem> {
        val filterUri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon().appendPath(query).build()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME
        )
        val results = mutableListOf<AppListItem>()
        try {
            context.contentResolver.query(filterUri, projection, null, null, "${ContactsContract.Contacts.DISPLAY_NAME} ASC")
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                    val keyCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                    val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                    while (cursor.moveToNext() && results.size < LIMIT) {
                        val name = cursor.getString(nameCol) ?: continue
                        val uri = ContactsContract.Contacts.getLookupUri(cursor.getLong(idCol), cursor.getString(keyCol))
                        results += AppListItem(name, "", CONTACT_PREFIX + uri, user, customTag = getLocalizedString(R.string.search_result_contact))
                    }
                }
        } catch (e: Exception) {
            AppLogger.e("ExtraSearch", "Contact search failed: ${e.message}", e)
        }
        return results
    }

    companion object {
        const val LIMIT = 30
        const val FILES = "files"
        const val CONTACTS = "contacts"
        private const val REQUEST_CODE = 4711
    }
}
