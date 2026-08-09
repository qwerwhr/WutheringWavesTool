#!/usr/bin/env python3
"""
WutheringWavesTool Native Package Builder
在 mvn package (shade) 之后自动执行：jpackage app-image -> 便携 zip
用法：由 exec-maven-plugin 自动调用，也可手动运行 python package.py
输出：build/WutheringWavesTool-windows-x64-{version}.zip
"""

import os
import sys
import subprocess
import zipfile
import shutil

# Windows cmd 兼容：强制 UTF-8 输出
if sys.stdout is not None:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# ── 配置（从环境变量或默认值读取）─────────────────────────────
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
JAVA_HOME = os.environ.get("JAVA_HOME", r"D:\Source\jdk\jdk-26.0.2")
VERSION = os.environ.get("PROJECT_VERSION", "1.5.2.1")
FINAL_NAME = os.environ.get("PROJECT_FINAL_NAME", f"wuthering-waves-tool-{VERSION}")

TARGET_DIR = os.path.join(PROJECT_DIR, "target")
BUILD_DIR = os.path.join(PROJECT_DIR, "build")
INPUT_DIR = os.path.join(BUILD_DIR, "input")
OUTPUT_DIR = os.path.join(BUILD_DIR, "out")
APP_IMAGE_DIR = os.path.join(OUTPUT_DIR, "WutheringWavesTool")

ICON_PATH = os.path.join(
    PROJECT_DIR,
    "src/main/resources/cn/tealc/wutheringwavestool/image/icon.ico",
)

FAT_JAR = os.path.join(TARGET_DIR, f"{FINAL_NAME}.jar")
ZIP_OUTPUT = os.path.join(BUILD_DIR, f"WutheringWavesTool-windows-x64-{VERSION}.zip")


def log(msg):
    print(f"[package.py] {msg}")


def find_jpackage():
    """查找 jpackage 可执行文件"""
    candidates = [
        os.path.join(JAVA_HOME, "bin", "jpackage.exe"),
        os.path.join(JAVA_HOME, "bin", "jpackage"),
    ]
    for c in candidates:
        if os.path.isfile(c):
            return c
    for cmd in ["jpackage.exe", "jpackage"]:
        p = shutil.which(cmd)
        if p:
            return p
    raise FileNotFoundError(
        f"Cannot find jpackage. Ensure JAVA_HOME points to a JDK (current={JAVA_HOME})"
    )


def main():
    log(f"version: {VERSION}")
    log(f"fat jar: {FAT_JAR}")

    # 1. 检查 fat jar 存在
    if not os.path.isfile(FAT_JAR):
        log("[ERROR] Fat jar not found, run mvn clean package first")
        sys.exit(1)
    jar_size = os.path.getsize(FAT_JAR) / (1024 * 1024)
    log(f"   size: {jar_size:.1f} MB")

    # 2. 准备 input 目录
    if os.path.exists(INPUT_DIR):
        shutil.rmtree(INPUT_DIR)
    os.makedirs(INPUT_DIR, exist_ok=True)
    shutil.copy2(FAT_JAR, os.path.join(INPUT_DIR, os.path.basename(FAT_JAR)))
    log("[OK] input ready")

    # 3. 清理旧的 app-image
    if os.path.exists(APP_IMAGE_DIR):
        shutil.rmtree(APP_IMAGE_DIR)
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # 4. jpackage 打 app-image
    jpackage = find_jpackage()
    log(f"jpackage: {jpackage}")

    icon_arg = []
    if os.path.isfile(ICON_PATH):
        icon_arg = ["--icon", ICON_PATH]
        log(f"   icon: {ICON_PATH}")
    else:
        log("   [WARN] no icon found, using default")

    cmd = [
        jpackage,
        "--type", "app-image",
        "--input", INPUT_DIR,
        "--main-jar", os.path.basename(FAT_JAR),
        "--main-class", "cn.tealc.wutheringwavestool.Launcher",
        "--name", "WutheringWavesTool",
        "--app-version", VERSION,
        "--vendor", "qwerwhr",
        "--copyright", "Copyright 2026 qwerwhr",
        "--dest", OUTPUT_DIR,
        *icon_arg,
        "--java-options", "-XX:+UseZGC",
        "--java-options", "-Djavafx.enablePreview=true",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
    ]

    log("   running jpackage...")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        log(f"[ERROR] jpackage failed (exit {result.returncode})")
        if result.stderr:
            log(f"   stderr: {result.stderr[-500:]}")
        if result.stdout:
            log(f"   stdout: {result.stdout[-500:]}")
        sys.exit(1)
    log("[OK] jpackage done")

    # 5. 打 zip
    if os.path.exists(ZIP_OUTPUT):
        os.remove(ZIP_OUTPUT)

    written = 0
    with zipfile.ZipFile(ZIP_OUTPUT, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, _dirs, files in os.walk(APP_IMAGE_DIR):
            for f in files:
                fp = os.path.join(root, f)
                arcname = os.path.relpath(fp, os.path.dirname(APP_IMAGE_DIR))
                zf.write(fp, arcname)
                written += 1

    zip_size = os.path.getsize(ZIP_OUTPUT) / (1024 * 1024)
    log(f"[OK] zip: {ZIP_OUTPUT} ({zip_size:.1f} MB, {written} files)")
    log("")
    log("=== DONE ===")
    log(f"   exe : {os.path.join(APP_IMAGE_DIR, 'WutheringWavesTool.exe')}")
    log(f"   zip : {ZIP_OUTPUT}")


if __name__ == "__main__":
    main()
