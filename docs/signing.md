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

That check only started failing once a key existed to use. While there was none it warned
instead, since stopping the upload would have taken away the only route a build had to a
phone — which is a thing to say out loud, not to decide quietly on somebody's behalf.

## The key in use

Set up on 2 September 2026. The keystore and its password live outside the repository, on
the machine that made them; the four secrets are on the repository. What follows is public
- it is the certificate every genuine build carries, and it is the thing to check an APK
against if you ever need to know whether it came from here.

    CN=AI Sudoku, O=AI Sudoku, C=BG
    SHA-256  a5:10:d8:2b:87:e7:06:9b:53:d5:20:be:ca:91:5b:35:2a:e9:6c:ea:85:0c:68:ca:00:61:17:e3:5d:75:d3:e5

To check a build:

```bash
apksigner verify --print-certs app-arm64-v8a-release.apk
```

Anything reporting `CN=Android Debug` was built without the secrets and cannot be updated
over, whoever built it.

## The one last uninstall

The first build signed with the new key still differs from whatever is on the phone now,
so testers must uninstall once more. Every build after that is an ordinary update, and
their puzzles and photographs stay put.

## When the app goes to Google Play

Play App Signing changes what this key is for. Google holds the *app signing key* — the one
every installed copy is verified against — and the key described above becomes the *upload
key*: it keeps signing what CI builds, and Play re-signs before distributing.

That makes the key less catastrophic to lose than it is today, but not harmless. If it goes
missing, Google can reset the upload key on request; until they do, nothing can be
uploaded. Keep it exactly as safe as before.

The certificate recorded above is what Firebase App Distribution builds carry, and what
testers have installed today. Once Play App Signing is on, a build installed from the
Play Store will report Google's certificate instead - a difference to expect rather than
one to investigate.
