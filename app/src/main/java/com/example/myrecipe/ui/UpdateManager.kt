package com.example.myrecipe.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.myrecipe.BuildConfig
import com.example.myrecipe.model.UpdateInfo
import com.example.myrecipe.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class UpdateManager(private val context: Context) {

    suspend fun checkForUpdates(allowSameVersion: Boolean = true): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val updateInfo = RetrofitClient.instance.getUpdateInfo()
                val currentCode = BuildConfig.VERSION_CODE
                android.util.Log.d("UpdateManager", "Server Code: ${updateInfo.versionCode}, App Code: $currentCode")
                
                if (updateInfo.versionCode > currentCode || (allowSameVersion && updateInfo.versionCode == currentCode)) {
                    updateInfo
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "App v${BuildConfig.VERSION_NAME} (Code $currentCode) is up to date with Server (Code ${updateInfo.versionCode})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Unable to check for updates: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                null
            }
        }
    }

    fun downloadAndInstall(updateInfo: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Resolve relative URL against RetrofitClient BASE_URL if needed
        val rawUrl = updateInfo.updateUrl
        val downloadUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            RetrofitClient.BASE_URL.removeSuffix("/") + "/" + rawUrl.removePrefix("/")
        }

        android.util.Log.d("UpdateManager", "Downloading update from: $downloadUrl")
        val uri = Uri.parse(downloadUrl)

        val fileName = "ChefMate_${updateInfo.versionName}.apk"

        // Delete existing old download if present
        try {
            val oldFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (oldFile.exists()) oldFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val request = DownloadManager.Request(uri)
            .setTitle("Downloading ChefMate Update")
            .setDescription("Version ${updateInfo.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)
        
        Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIdx)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            installApk(updateInfo.versionName)
                        } else {
                            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = cursor.getInt(reasonIdx)
                            Toast.makeText(context, "Download failed (Status: $status, Reason: $reason)", Toast.LENGTH_LONG).show()
                        }
                    }
                    cursor.close()
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(versionName: String) {
        val apkFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "ChefMate_$versionName.apk"
        )

        if (!apkFile.exists()) {
            Toast.makeText(context, "Update file not found after download", Toast.LENGTH_LONG).show()
            return
        }

        // On Android 8.0+ (API 26), ensure permission to install unknown apps is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "Please enable 'Install unknown apps' permission to complete update", Toast.LENGTH_LONG).show()
            try {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        val intent = Intent(Intent.ACTION_VIEW)
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to open installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
