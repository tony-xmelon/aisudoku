# Distributing test builds through Firebase

CI already assembles a signed release APK on every push to `main` and uploads it as a
workflow artifact. Distribution to testers needs two secrets that only you can create,
because they come from your Firebase project. Until they exist the distribute job runs,
logs a notice and succeeds — a missing credential does not break the build.

## What has to be done once

**1. Register the Android app in the Firebase project.**

In [the console](https://console.firebase.google.com/u/2/project/aisudoku-xmelon/overview),
add an Android app with the package name:

```
io.github.tonyxmelon.aisudoku
```

This must match exactly; App Distribution keys builds off it. Downloading
`google-services.json` is *not* required — the app makes no network calls and links no
Firebase SDK, so nothing in it reads that file. Distribution is a build-time concern only.

**2. Copy the App ID.**

Project settings → General → Your apps → App ID. It looks like:

```
1:123456789012:android:0123456789abcdef
```

**3. Create a service account key.**

In the Google Cloud console for the same project, create a service account, grant it the
**Firebase App Distribution Admin** role, and create a JSON key. The whole JSON file
becomes the secret value.

**4. Add both as GitHub repository secrets.**

`Settings → Secrets and variables → Actions → New repository secret`:

| Secret | Value |
| --- | --- |
| `FIREBASE_APP_ID` | the App ID from step 2 |
| `FIREBASE_SERVICE_ACCOUNT` | the entire contents of the JSON key file |

**5. Create a tester group called `testers`.**

App Distribution → Testers & Groups. The Gradle config distributes to that group by
name; change `groups` in `app/build.gradle.kts` if you would rather call it something
else.

That is all. The next push to `main` distributes a build.

## Why the credential is handled this way

The service account key is a credential with permission to publish builds to your
testers. It is never committed, never written to the repository, and in CI it exists only
as a temporary file that is deleted in the same step that uses it. `.gitignore` also
blocks the obvious filenames in case one is ever downloaded into the working tree.

The App ID is not secret, but it lives beside the key so that a clone with neither still
builds. `app/build.gradle.kts` reads both from the environment and falls back to empty
strings, which is why the build works untouched on a machine that has never seen them.

## Signing

Release builds are currently signed with the debug key. That is fine for App
Distribution — testers install the APK directly — but it is **not** publishable to Google
Play, which requires a real upload key. When that time comes, add a keystore as a further
set of secrets and point `signingConfigs` at it.

## Running a distribution by hand

```bash
FIREBASE_APP_ID=1:...:android:... \
FIREBASE_CREDENTIALS_FILE=/path/to/key.json \
BUILD_NUMBER=1 \
./gradlew :app:assembleRelease appDistributionUploadRelease
```
