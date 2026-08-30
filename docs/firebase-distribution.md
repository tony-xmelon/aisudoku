# Distributing test builds through Firebase

Project: **`aisudoku-xmelon`** (number 52623658492)
Android app: **`1:52623658492:android:dbb8616352a8d44e29f679`**, package `io.github.tonyxmelon.aisudoku`

## What is already set up

- The Android app is registered in the Firebase project.
- A tester group `testers` exists. It is **empty**, so no build has emailed anybody.
- Two releases have been distributed successfully, so the path is proven working:
  `0.1.1 (1)` and `0.1.2 (2)`.
- The app id is committed in `app/build.gradle.kts`. It is not a secret - it is derivable
  from any built APK - so a fresh clone can distribute without configuration.
- CI builds a release APK on every push and uploads it as a workflow artifact.

`google-services.json` is deliberately **not** used. The app links no Firebase SDK and
makes no network calls; distribution is purely a build-time concern.

## Distributing from this machine

The Firebase CLI is already signed in, so this works as-is:

```bash
BUILD_NUMBER=$(date +%s) ./gradlew :app:assembleRelease && firebase appdistribution:distribute app/build/outputs/apk/release/app-arm64-v8a-release.apk --app 1:52623658492:android:dbb8616352a8d44e29f679 --release-notes-file docs/release-notes.txt --groups testers --project aisudoku-xmelon
```

## The one remaining step: distributing from CI

CI cannot use the signed-in CLI, because that credential lives only on this machine. It
needs a service account instead, and creating one is the single thing that has to be done
by hand:

1. In the [Google Cloud console](https://console.cloud.google.com/iam-admin/serviceaccounts?project=aisudoku-xmelon)
   for `aisudoku-xmelon`, create a service account.
2. Grant it the **Firebase App Distribution Admin** role.
3. Create a JSON key for it and download the file.
4. In GitHub: `Settings → Secrets and variables → Actions → New repository secret`, named
   **`FIREBASE_SERVICE_ACCOUNT`**, with the entire contents of the JSON file as the value.

That is the only secret needed; the app id is already committed. The next push to `main`
will distribute automatically. Until then CI logs a warning and still succeeds, so the
build is never broken by a credential it does not have.

**This step is deliberately left to you.** The key can publish builds to your testers, and
it should not pass through anything but your own hands and GitHub's secret store.

## Adding testers

The group is empty, which is why no build has notified anyone. Add people in
[App Distribution → Testers & Groups](https://console.firebase.google.com/project/aisudoku-xmelon/appdistribution),
or from the CLI:

```bash
firebase appdistribution:testers:add your.email@example.com --project aisudoku-xmelon
```

Adding a tester sends them an invitation email, which is why it has been left for you to do.

## Signing

Release builds are signed with the debug key. That is fine for App Distribution, where
testers install the APK directly, but it is **not** publishable to Google Play, which
requires a real upload key. When that time comes, add a keystore as further secrets and
point `signingConfigs` at it.
