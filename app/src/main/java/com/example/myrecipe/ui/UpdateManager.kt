package com.example.myrecipe.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.myrecipe.BuildConfig
import com.example.myrecipe.model.UpdateInfo
import com.example.myrecipe.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class UpdateManager(private val context: Context) {

    suspend fun checkForUpdates(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val updateInfo = RetrofitClient.instance.getUpdateInfo()
                val currentCode = BuildConfig.VERSION_CODE
                android.util.Log.d("UpdateManager", "Server Code: ${updateInfo.versionCode}, App Code: $currentCode")
                
                if (updateInfo.versionCode > currentCode) {
                    updateInfo
                } else {
                    // Show a toast with the details so the user knows WHY nothing happened
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No update: Server(${updateInfo.versionCode}) <= App($currentCode)", Toast.LENGTH_LONG).show()
                    }
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Check failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                null
            }
        }
    }

    fun downloadAndInstall(updateInfo: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(updateInfo.updateUrl)
        
        val request = DownloadManager.Request(uri)
            .setTitle("Downloading ChefMate Update")
            .setDescription("New version ${updateInfo.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "ChefMate_${updateInfo.versionName}.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)
        
        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()

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

        val intent = Intent(Intent.ACTION_VIEW)
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
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
