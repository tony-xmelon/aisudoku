# Signing the release

Every build that reaches a tester must be signed with the same key. Android refuses to
update an installed app whose signature has changed, so a release signed with a different
key each time cannot be updated at all: it arrives as an uninstall, and the tester loses
their puzzles, their photographs and their settings.

That is what was happening. `buildTypes.release` used `signingConfigs.getByName("debug")`,
and a CI runner has no debug keystore — so the Android plugin generated a fresh one on
every run. Two consecutive builds were signed by two different certificates:

    0.1.60   CN=Android Debug   SHA-256 0e467993f0438d83b3ab971bfa3e5f3c…
    0.1.61   CN=Android Debug   SHA-256 8bd7c03cb97d31de9e13c743997c0b22…

## Making a key

Once, on a machine you trust. Keep the file and the passwords; if they are lost, no future
build can update an installed app and every tester has to uninstall one last time.

```bash
keytool -genkeypair -v \
  -keystore aisudoku-release.jks \
  -alias aisudoku \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=AI Sudoku, O=AI Sudoku, C=GB"
```

## Telling CI about it

Four repository secrets. The keystore is binary, so it travels base64-encoded:

```bash
base64 -w0 aisudoku-release.jks       # the value for ANDROID_KEYSTORE_BASE64
```

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | the store password |
| `ANDROID_KEY_ALIAS` | `aisudoku`, if you used the command above |
| `ANDROID_KEY_PASSWORD` | the key password |

The workflow writes the keystore to a temporary file and sets `SIGNING_KEYSTORE` to its
path; `app/build.gradle.kts` picks it up from there.

## What happens without it

A local build still works — it falls back to the debug key, which is what you want on a
development machine. What it will not do is reach anybody: `checkReleaseSigning` runs
before `appDistributionUpload` and fails the job, because a debug-signed release that
testers cannot update is worse than no release at all.

## The one last uninstall

The first build signed with the new key still differs from whatever is on the phone now,
so testers must uninstall once more. Every build after that is an ordinary update, and
their puzzles and photographs stay put.
