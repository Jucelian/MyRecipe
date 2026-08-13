# Implementation Plan: In-App Update System

This plan describes how to implement a self-hosted in-app update system using the existing Ktor server and Android app.

## User Review Required

> [!IMPORTANT]
> To update the app, you will need to manually upload the new APK to the server's `updates` directory or provide a way to host it. For now, the server will look for `ChefMate-release.apk` in an `updates` folder.

## Proposed Changes

### Server (Ktor)

#### [MODIFY] [Application.kt](file:///C:/Users/Jucelian/Desktop/MyRecipe/server/src/main/kotlin/com/example/server/Application.kt)
- Add an `UpdateInfo` data class.
- Implement `GET /app/version` to return the latest version info.
- Implement `GET /app/download` to serve the APK file.

---

### Android App

#### [NEW] [UpdateInfo.kt](file:///C:/Users/Jucelian/Desktop/MyRecipe/app/src/main/java/com/example/myrecipe/network/UpdateInfo.kt)
- Create a data class to match the server's response.

#### [MODIFY] [RecipeApiService.kt](file:///C:/Users/Jucelian/Desktop/MyRecipe/app/src/main/java/com/example/myrecipe/network/RecipeApiService.kt)
- Add `suspend fun getVersionInfo(): UpdateInfo`.

#### [MODIFY] [ProfileScreen.kt](file:///C:/Users/Jucelian/Desktop/MyRecipe/app/src/main/java/com/example/myrecipe/ui/ProfileScreen.kt)
- Add a "Check for Updates" button in a menu or as a list item.
- Implement the UI logic to show an "Update Available" dialog.

#### [NEW] [UpdateManager.kt](file:///C:/Users/Jucelian/Desktop/MyRecipe/app/src/main/java/com/example/myrecipe/ui/UpdateManager.kt)
- Create a helper class/ViewModel to:
    - Call the API to check for updates.
    - Compare `versionCode`.
    - Handle APK download using `DownloadManager` or `OkHttp`.
    - Trigger the installation Intent using `FileProvider`.

## Verification Plan

### Automated Tests
- N/A (Manual verification required for APK installation flow).

### Manual Verification
1. Deploy updated server with a dummy `versionCode` higher than the current app's.
2. Open the app and go to the Profile screen.
3. Click "Check for Updates".
4. Verify the "Update Available" dialog appears.
5. Click "Update" and verify the download starts and the installation prompt appears.
