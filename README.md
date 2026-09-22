# PSBDx DevBrowser

A developer-focused Android browser (Kotlin + Jetpack Compose, Material 3)
that injects [Eruda](https://github.com/liriliri/eruda) into any web page
for an on-device console, DOM inspector, network logger, and script
runner — plus a source viewer, snippet manager, one-tap storage cleaner,
and element picker on top of that.

- **License:** GPLv3 (see `LICENSE`)
- **Package:** `com.devbrowser.psbdx`
- **Min/Target SDK:** 24 / 34

## Eruda bundle

`app/src/main/assets/eruda.min.js` contains the genuine Eruda release
build (webpack UMD bundle), with the MIT copyright header prepended
above it:

```
Copyright (c) 2016-present, liriliri (https://github.com/liriliri)
Licensed under the MIT license.
```

If you ever need to update it to a newer Eruda release, keep that same
header intact above the new bundle — the MIT License's attribution
clause requires it wherever the software is redistributed:

```bash
curl -L -o /tmp/eruda.min.js \
  https://github.com/liriliri/eruda/releases/latest/download/eruda.min.js
cat > app/src/main/assets/eruda.min.js << 'EOF'
/*!
 * eruda
 * https://github.com/liriliri/eruda
 *
 * Copyright (c) 2016-present, liriliri (https://github.com/liriliri)
 *
 * Licensed under the MIT license.
 * https://github.com/liriliri/eruda/blob/master/LICENSE
 */
EOF
cat /tmp/eruda.min.js >> app/src/main/assets/eruda.min.js
```

## Local build

```bash
gradle wrapper --gradle-version 8.7   # generates gradlew + gradle-wrapper.jar
chmod +x gradlew
./gradlew assembleDebug
```

The `gradlew` and `gradlew.bat` scripts are included; only the
`gradle-wrapper.jar` binary is generated on demand (by you locally, or by
CI) rather than committed to the repo.

## CI/CD (GitHub Actions)

`.github/workflows/build.yml` builds both a Debug and a signed Release
APK on every push/PR to `main`, and uploads them as workflow artifacts:
`PSBDx-DevBrowser-Debug-APK` and `PSBDx-DevBrowser-Release-APK`.

Set these repository secrets to enable release signing (the Release
build falls back to debug signing if they're absent, so the workflow
never fails on a fork without secrets):

| Secret                    | Description                                  |
|---------------------------|-----------------------------------------------|
| `KEYSTORE_BASE64`          | `base64 -w0 your.keystore` output             |
| `RELEASE_STORE_PASSWORD`   | Keystore password                             |
| `RELEASE_KEY_PASSWORD`     | Key password                                  |
| `RELEASE_KEYALIAS`         | Key alias                                     |

## F-Droid compliance notes

- No Google Play Services, Firebase, Crashlytics, AdMob, or other
  proprietary SDKs anywhere in the dependency graph.
- Only two permissions requested: `INTERNET` and `ACCESS_NETWORK_STATE`.
- Eruda is bundled as a local asset (`assets/eruda.min.js`, MIT header
  intact) and evaluated in-page; the app never fetches executable JS
  from a remote CDN at runtime.
- No analytics, telemetry, or tracking of any kind.

## Project structure

```
app/src/main/java/com/devbrowser/psbdx/
├── MainActivity.kt
├── DevBrowserApplication.kt
├── data/AppDatabase.kt            # Room: bookmarks, history, JS snippets
├── viewmodel/BrowserViewModel.kt  # MVVM state holder
├── webview/DevWebView.kt          # Compose WebView wrapper + controller
├── webview/ErudaWebClient.kt      # Injects assets/eruda.min.js
└── ui/MainScreen.kt, AboutDialog.kt
```
