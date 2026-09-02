# Getting AI Sudoku onto Google Play

What is done, what needs a developer account, and what still needs a decision. Written
against the state of the repository on 2 September 2026.

## Done in the repository

| Item | State |
| --- | --- |
| App bundle | `./gradlew :app:bundleRelease` produces `app/build/outputs/bundle/release/app-release.aab` |
| 16 KB page alignment | every native library passes; guarded by `checkNativeAlignment` |
| Target API level | 36, above Play's current floor of 35 |
| Minimum API level | 26 |
| Signing | release builds are signed with the key in `docs/signing.md` |
| Version code | `github.run_number`, so it only ever increases |
| Permissions | `CAMERA` only; `ACCESS_NETWORK_STATE` is explicitly removed |
| Privacy policy text | `docs/privacy-policy.md` — still needs hosting at a public URL |
| Store icon | `docs/store/icon-512.png` |

## The 16 KB problem, and why it is worth knowing about

Play requires every app with native code to work on devices with 16 KB memory pages. A
library that fails this does not misbehave subtly — it fails to load, on a class of phone
this build was never run on.

OpenCV 4.11.0 shipped a correctly aligned `libopencv_java4.so` next to a stale
`libc++_shared.so` that was still built for 4 KB pages, so the fault sat entirely in a
dependency's packaging. Moving to OpenCV 4.12.0 fixes it. `checkNativeAlignment` reads the
ELF program headers of every library in the built APK and fails the build if any of them
declares a load alignment below 16384, so this cannot come back unnoticed through a future
dependency bump.

Note that the JVM tests link against `org.openpnp:opencv`, a different artifact, so the
test suite does **not** exercise the Android library. The bump is verified for alignment
and for compilation, not for behaviour — that check is a scan on a real phone.

## Needs the developer account

These cannot be done from the repository.

1. **Register**, pay the one-off fee, and complete identity verification. Verification can
   take a few days, so it is worth starting before the listing is ready.
2. **Create the app** with package name `io.github.tonyxmelon.aisudoku`. This is permanent
   — it cannot be changed after the first upload, and it is the identity every future
   update is matched against.
3. **Enrol in Play App Signing.** Play then holds the *app signing key* and the key in
   `docs/signing.md` becomes the *upload key* — it keeps signing what we send, and Play
   re-signs for distribution. Keep it exactly as safe as before: losing it means asking
   Google to reset the upload key.
4. **Host the privacy policy** somewhere public and paste the URL into the listing. Play
   requires one because the app requests camera access. GitHub Pages on this repository is
   enough; so is the raw file URL.
5. **Data safety form.** The honest answers are: no data collected, no data shared, no
   data sent off the device. The camera is used but nothing from it leaves the phone.
6. **Content rating questionnaire.** A sudoku reader with no ads, no purchases, no user
   content and no communication rates at the lowest category everywhere.

## Store listing assets still to make

| Asset | Requirement | State |
| --- | --- | --- |
| App icon | 512x512 PNG | `docs/store/icon-512.png` |
| Feature graphic | 1024x500 PNG | not made |
| Phone screenshots | at least 2, 16:9 or 9:16, min 320px | not made — these should come off a real phone |
| Title | 30 characters | "AI Sudoku" |
| Short description | 80 characters | draft below |
| Full description | 4000 characters | draft below |

Screenshots are worth taking on the phone rather than an emulator: the camera screen is
most of the app's first impression, and an emulator cannot show it reading a real page.

### Draft short description

> Point your camera at a printed sudoku and get a tutor that explains every step.

### Draft full description

> AI Sudoku reads a printed sudoku through your camera and then teaches you how to solve
> it, one step at a time, in plain language.
>
> Photograph a puzzle from a newspaper or a book. The app finds the grid, reads the printed
> clues, and hands you a board you can work on. Your own pencilled answers are read too, so
> a puzzle you have already started carries on where you left it.
>
> The tutor works through twenty-three human solving techniques, from naked singles to
> forcing chains, and names the one it is using at every step. It shows you which squares
> the deduction rests on rather than simply filling a number in, so the point is to
> understand the move rather than to be given it.
>
> Nothing leaves your phone. The app has no internet permission at all, no accounts, no
> analytics and no advertising.

## Deciding before the first upload

- **Minification is off.** Turning R8 on would shrink the Kotlin code, but the app's size
  is dominated by OpenCV's native libraries, which R8 does not touch. The gain is small and
  the risk is a reflection-related crash that only appears in release. Recommended: leave
  it off for the first release, and revisit if size matters.
- **The bundle carries all four ABIs** and is about 67 MB as a file, while each phone
  downloads only its own slice. This is the right shape for Play, and different from the
  arm64-only APK used for Firebase distribution — that one exists to keep test downloads
  small.
- **A closed test track first.** Play now expects a period of closed testing before a
  personal developer account can go to production. Starting that track early runs the
  clock down while the listing is finished.
