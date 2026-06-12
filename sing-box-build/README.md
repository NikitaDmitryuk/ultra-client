# sing-box build artifacts

The app is sing-box-only. Source control does not store generated native artifacts.

## Android

Build the Android mobile binding:

```bash
ANDROID_HOME=/path/to/android-sdk \
ANDROID_NDK_HOME=/path/to/android-sdk/ndk/<version> \
SING_BOX_VERSION=v1.13.13 \
bash sing-box-build/build-android.sh
```

The script uses upstream `go run ./cmd/internal/build_libbox -target android`, which creates `libbox.aar`.
It defaults to `GOTOOLCHAIN=go1.24.7`, matching sing-box 1.13 requirements and avoiding current gomobile issues on newer Go toolchains.
It copies the result to:

```text
sing-box-build/output/android/SingBoxCore.aar
androidApp/libs/SingBoxCore.aar
```

`androidApp/libs/SingBoxCore.aar` is intentionally ignored by git. Release builds require it.

## Desktop

Build a host desktop binary:

```bash
SING_BOX_VERSION=v1.13.13 bash sing-box-build/build-desktop.sh
```

The current desktop engine discovers `sing-box` from:

1. `ULTRA_SING_BOX_PATH`
2. `~/.ultra-client/bin/sing-box`
3. `PATH`

Production packaging should install the binary plus a privileged helper/service for TUN, routes, and DNS.
