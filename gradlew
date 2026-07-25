#!/usr/bin/env sh
# Lightweight Gradle launcher for this generated source package.
# It downloads the official Gradle distribution and verifies its SHA-256.
set -eu

GRADLE_VERSION="9.5.0"
GRADLE_SHA256="553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"
GRADLE_BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/autotask-wrapper"
CACHE_DIR="$GRADLE_BASE/gradle-$GRADLE_VERSION"
ZIP_FILE="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_HOME="$CACHE_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

verify_sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        ACTUAL="$(sha256sum "$ZIP_FILE" | awk '{print $1}')"
    elif command -v shasum >/dev/null 2>&1; then
        ACTUAL="$(shasum -a 256 "$ZIP_FILE" | awk '{print $1}')"
    else
        echo "无法校验 Gradle 文件：系统缺少 sha256sum 或 shasum。" >&2
        return 1
    fi
    [ "$ACTUAL" = "$GRADLE_SHA256" ]
}

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$CACHE_DIR"

    if [ ! -f "$ZIP_FILE" ] || ! verify_sha256; then
        rm -f "$ZIP_FILE"
        echo "正在下载 Gradle $GRADLE_VERSION ..." >&2
        if command -v curl >/dev/null 2>&1; then
            curl --fail --location --retry 2 --output "$ZIP_FILE" "$DIST_URL"
        elif command -v wget >/dev/null 2>&1; then
            wget -O "$ZIP_FILE" "$DIST_URL"
        else
            echo "请安装 curl 或 wget，或在 Android Studio 中指定本地 Gradle $GRADLE_VERSION。" >&2
            exit 1
        fi
        if ! verify_sha256; then
            echo "Gradle 下载文件校验失败，已停止执行。" >&2
            rm -f "$ZIP_FILE"
            exit 1
        fi
    fi

    if ! command -v unzip >/dev/null 2>&1; then
        echo "请安装 unzip，或在 Android Studio 中指定本地 Gradle $GRADLE_VERSION。" >&2
        exit 1
    fi

    rm -rf "$GRADLE_HOME"
    unzip -q "$ZIP_FILE" -d "$CACHE_DIR"
fi

exec "$GRADLE_BIN" "$@"
