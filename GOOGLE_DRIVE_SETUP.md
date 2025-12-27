# Google Drive Setup Guide

To use Google Drive as a remote storage provider in MyFolder, you must configure a project in the Google Cloud Console and add the `google-services.json` file to the app.

## 1. Create a Google Cloud Project

1.  Go to the [Google Cloud Console](https://console.cloud.google.com/).
2.  Click the project drop-down and select **New Project**.
3.  Enter a project name (e.g., "MyFolder Drive") and click **Create**.

## 2. Enable Google Drive API

1.  In the sidebar, go to **APIs & Services > Library**.
2.  Search for **Google Drive API**.
3.  Click on it and click **Enable**.

## 3. Configure OAuth Consent Screen

1.  In the sidebar, go to **APIs & Services > OAuth consent screen**.
2.  Select **External** (unless you are a Google Workspace user testing internally) and click **Create**.
3.  Fill in the **App Information**:
    *   **App name**: MyFolder
    *   **User support email**: Your email
    *   **Developer contact information**: Your email
4.  Click **Save and Continue**.
5.  **Scopes**: Click **Add or Remove Scopes**.
    *   Search for `drive.file` and check it (`../auth/drive.file`).
    *   Search for `drive.appdata` and check it (`../auth/drive.appdata`).
    *   Click **Update**, then **Save and Continue**.
6.  **Test Users**: Add the Google email addresses you want to test with.
7.  Review and go back to Dashboard.

## 4. Create Credentials

1.  In the sidebar, go to **APIs & Services > Credentials**.
2.  Click **Create Credentials** > **OAuth client ID**.
3.  **Application type**: Select **Android**.
4.  **Package name**: Enter `com.kcpd.myfolder`.
5.  **SHA-1 Certificate Fingerprint**:
    *   You need the SHA-1 fingerprint of the keystore used to sign the app.
    *   **For Debug builds**: Run `./gradlew signingReport` in the terminal to see the SHA-1 for `debug` variant.
    *   **For Release builds**: Use the SHA-1 of your release keystore (or Play Signing if using Play Store).
6.  Click **Create**.

## 5. Download `google-services.json`

**IMPORTANT**: The file must be named `google-services.json` and contain a `client_info` section. Do **not** use the `client_secret_....json` file downloaded from the Credentials list, as that is for Desktop/Web apps.

**Recommended Method (Easiest)**:
1.  Go to the [Firebase Console](https://console.firebase.google.com/).
2.  Click **Add project** and select your existing Google Cloud project ("MyFolder Drive") from the dropdown.
3.  Click **Continue** and finish setup.
4.  In the Firebase Project Overview, click the **Android** icon to add an app.
5.  **Android package name**: `com.kcpd.myfolder`.
6.  **Debug signing certificate SHA-1**: Enter the SHA-1 you got from `./gradlew signingReport`.
7.  Click **Register app**.
8.  Click **Download google-services.json**.
9.  This is the correct file.

**Manual Method (Google Cloud Console)**:
If you cannot use Firebase, ensure you created an **Android** OAuth Client ID. However, the Cloud Console often does not provide the full `google-services.json` file easily. Using Firebase (linked to the same project) is the standard way to generate it for Android.

**Placement**:
2.  Rename the downloaded file to `google-services.json` (if needed).
3.  Place the file in the **`app/`** directory of the project:
    ```
    MyFolderCompose/app/google-services.json
    ```

## 6. Build and Run

1.  Sync Gradle files (the project now includes the `com.google.gms.google-services` plugin).
2.  Build and run the app.
3.  Go to **Settings**.
4.  Under **Cloud Storage**, select **Google Drive**.
5.  Click **Sign In with Google**.

## Troubleshooting

*   **Sign-In Failed (Code 10)**: Often means the SHA-1 fingerprint in the Cloud Console does not match the app's signature. Ensure you added the correct SHA-1 (Debug vs Release).
*   **Sign-In Failed (Code 12500)**: Often means a missing scope or generic configuration error.
*   **API Error**: Ensure the Google Drive API is enabled in the Cloud Console.
