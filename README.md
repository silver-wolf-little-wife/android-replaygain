# 音量平衡（ReplayGain）Android App

扫描自定义工作目录下的 FLAC/MP3 文件，写入 `REPLAYGAIN_TRACK_GAIN` 和 `REPLAYGAIN_ALBUM_GAIN` 标签，让 Poweramp、Foobar2000 等播放器自动实现音量平衡。

## 技术栈

- **标签读写**：JAudioTagger 2.2.5（`net.jthink:jaudiotagger:2.2.5`）
- **响度计算**：FFmpeg `loudnorm` filter（-18 LUFS 参考）
- **目标平台**：Android 16（API 36），最低 Android 9（API 28）
- **架构**：Kotlin + Android View system + ViewModel + Coroutines

## 项目结构

```
Volume Normalization/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── jniLibs/
│   │   ├── arm64-v8a/libffmpeg.so    # Android arm64 FFmpeg 可执行文件
│   │   └── x86_64/libffmpeg.so       # Android x86_64 FFmpeg 可执行文件
│   ├── java/com/example/replaygain/
│   │   ├── MainActivity.kt          # 主界面与目录选择
│   │   ├── ReplayGainApplication.kt
│   │   ├── data/
│   │   │   ├── AudioFileScanner.kt
│   │   │   ├── FFmpegAnalyzer.kt
│   │   │   ├── ReplayGainProcessor.kt
│   │   │   └── ReplayGainTagger.kt
│   │   ├── ui/
│   │   │   └── MainViewModel.kt
│   │   └── util/
│   │       ├── FFmpegBinaryHelper.kt
│   │       └── PermissionHelper.kt
│   └── res/
│       ├── layout/activity_main.xml
│       ├── layout/dialog_directory_picker.xml
│       └── values/
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew / gradlew.bat
```

## 如何构建 APK

### 方式一：Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio)（Ladybug 2024.2.1 或更新版本）。
2. 打开本项目目录 `音量平衡/`。
3. 首次打开时，Android Studio 会自动下载 Gradle 和依赖。
4. 连接 Android 设备或启动 Android 16 模拟器。
5. 点击菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**。
6. 生成的 APK 位于：
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### 方式二：命令行

如果你已经配置了 `ANDROID_HOME` 和 JDK 17+：

```bash
./gradlew assembleDebug
```

Windows：

```powershell
.\gradlew.bat assembleDebug
```

## 使用说明

1. 安装 APK 后首次打开，会跳转到系统设置页，开启「**允许访问所有文件**」。
2. 回到 App，点击「**选择工作目录**」，浏览并选择存放 FLAC/MP3 的文件夹。
3. 可选勾选「**跳过已有 ReplayGain 标签的文件**」。
4. 点击「**开始扫描并写入标签**」。
5. 等待处理完成，下方日志会显示进度。

## 算法说明

- **Track Gain**：`ffmpeg -af loudnorm=I=-18...` 得到的 `input_i`，`gain = -18 - input_i`
- **Album Gain**：同一目录下所有文件 track loudness 的平均值对应的增益
- 标签格式示例：`REPLAYGAIN_TRACK_GAIN=-2.50 dB`

## 注意事项

- 当前 FFmpeg 二进制是用 **Android NDK r27d 从源码自行编译**的 16KB 页对齐"瘦身"版本（`tools/build_ffmpeg_16kb.sh`），只保留 FLAC/MP3 解码 + aresample/loudnorm 滤波，单文件仅约 1.5MB（原全家桶约 15MB），运行内存占用低，可支撑更高并发。静态链接，仅依赖系统库，**无需 `libc++_shared.so`**。通过 `useLegacyPackaging` 解压到可执行目录，符合 Android 10+ W^X 安全要求及 Android 15/16 的 16KB 页要求。
- 重新编译命令（在 MSYS2 中）：
  ```bash
  bash /d/project/"Volume Normalization"/tools/build_ffmpeg_16kb.sh
  ```
  编译产物需满足 `llvm-readelf -lW` 的 LOAD 段 `Align 0x4000`（=16KB）。
- `MANAGE_EXTERNAL_STORAGE` 权限用于直接读写用户选择的工作目录；Google Play 上架需填写权限声明表。
- 写入标签会修改原文件，建议先备份重要音乐文件。

## 许可证

FFmpeg 由本项目基于 FFmpeg 7.1 官方源码编译，LGPL v2.1 或更新版本。JAudioTagger 为 LGPL/Apache 授权（以其实际许可证为准）。本 App 源码仅作示例和学习用途。
