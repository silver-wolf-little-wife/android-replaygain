#!/usr/bin/env bash
# 用 Android NDK 编译 16KB 对齐的 FFmpeg（arm64-v8a + x86_64）
# 用法：在 MSYS2 bash 中运行  bash /d/project/"Volume Normalization"/tools/build_ffmpeg_16kb.sh
set -e

NDK="/c/Users/13370/AppData/Local/Android/Sdk/ndk/27.3.13750724"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/windows-x86_64"
SYSROOT="$TOOLCHAIN/sysroot"
API=28
FFMPEG_SRC="/c/Users/13370/AppData/Local/Temp/ffmpeg-7.1"
PROJECT_DIR="/d/project/Volume Normalization"
BUILD_DIR="/c/Users/13370/AppData/Local/Temp/ffmpeg-16kb-build"

mkdir -p "$BUILD_DIR"

build_abi() {
    local ARCH=$1
    local TRIPLE=$2
    local OUT_DIR=$3
    local CC="$TOOLCHAIN/bin/${TRIPLE}${API}-clang"
    local PREFIX="$BUILD_DIR/$ARCH"

    echo "==================== 编译 $ARCH ===================="
    cd "$FFMPEG_SRC"
    make distclean 2>/dev/null || true

    local EXTRA_CFG=""
    if [ "$ARCH" = "x86_64" ]; then
        EXTRA_CFG="--disable-x86asm"
    fi

    ./configure \
        --prefix="$PREFIX" \
        --target-os=android \
        --arch="$ARCH" \
        --enable-cross-compile \
        --cc="$CC" \
        --sysroot="$SYSROOT" \
        --ar="$TOOLCHAIN/bin/llvm-ar" \
        --nm="$TOOLCHAIN/bin/llvm-nm" \
        --strip="$TOOLCHAIN/bin/llvm-strip" \
        --enable-small \
        --disable-doc \
        --disable-htmlpages --disable-manpages --disable-podpages --disable-txtpages \
        --disable-debug \
        --disable-ffplay --disable-ffprobe \
        --disable-network \
        $EXTRA_CFG \
        --extra-ldflags="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

    make -j"$(nproc)"
    make install

    mkdir -p "$PROJECT_DIR/app/src/main/jniLibs/$OUT_DIR"
    cp "$PREFIX/bin/ffmpeg" "$PROJECT_DIR/app/src/main/jniLibs/$OUT_DIR/libffmpeg.so"
    echo "=== $ARCH 完成 -> $PROJECT_DIR/app/src/main/jniLibs/$OUT_DIR/libffmpeg.so ==="
}

build_abi aarch64 aarch64-linux-android arm64-v8a
build_abi x86_64  x86_64-linux-android  x86_64

echo "全部编译完成"
