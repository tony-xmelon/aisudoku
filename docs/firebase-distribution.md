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
needs one of two credentials, and CI accepts either. **Take the first one** - it is a
single command and never touches the Google Cloud console.

### Option A: a Firebase token (recommended)

```bash
firebase login:ci
```

It opens a browser, you sign in as **tony.xmelon@gmail.com**, and it prints a token. Add
that token in GitHub under `Settings -> Secrets and variables -> Actions -> New repository
secret`, named **`FIREBASE_TOKEN`**.

That is all. The next push to `main` distributes.

### Option B: a service account key

Only needed if you would rather not use a long-lived token.

If `aisudoku-xmelon` does not appear in the Google Cloud console, it is almost certainly
one of these, because a Firebase project *is* a Cloud project and this one demonstrably
exists (number 52623658492):

- **You are signed in as the wrong Google account.** The Firebase console link for this
  project uses `/u/2/`, meaning the third Google account in your browser. The Cloud
  console uses a different parameter, so go straight there with:
  https://console.cloud.google.com/iam-admin/serviceaccounts?project=aisudoku-xmelon&authuser=2
  (try `authuser=0`, `1`, `2` until it resolves; it must be the account that owns the
  project, tony.xmelon@gmail.com)
- **The project picker is filtered by organization.** Firebase-created projects sit under
  *No organization*, which a Workspace account may hide by default. Clear the filter, or
  just use the direct link above, which bypasses the picker entirely.

Then: create a service account, grant it **Firebase App Distribution Admin**, create a
JSON key, and add the whole file as the GitHub secret **`FIREBASE_SERVICE_ACCOUNT`**.

**Either credential is deliberately left to you.** Both can publish builds to your
testers, and neither should pass through anything but your own hands and GitHub's secret
store.

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
