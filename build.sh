#!/usr/bin/env bash

set -euo pipefail

mvn package
PACKAGE_DIR="packaging/mac-amd64"
DIST_DIR="dist/mac-amd64"

APP_NAME="WanquCopilot"
# macOS CFBundleShortVersionString/CFBundleVersion require first component >= 1
APP_VERSION="1.0.0"

# Spring Boot fat-jar is best launched via JarLauncher.
# NOTE: In Spring Boot 3.x the class is under .boot.loader.launch.*
MAIN_CLASS="org.springframework.boot.loader.launch.JarLauncher"

# Jar produced by Maven build
FAT_JAR="wanqu-copilot-1.0.0-SNAPSHOT.jar"

rm -rf "${PACKAGE_DIR}" "${DIST_DIR}"
mkdir -p "${PACKAGE_DIR}" "${DIST_DIR}"

# Copy only the runnable jar (avoid copying *original which may confuse jpackage)
cp "target/${FAT_JAR}" "${PACKAGE_DIR}/"

# Optional: bundle pre-downloaded JCEF into app.
# Set JCEF_SRC_DIR to a directory (e.g. jcef) to copy it into the .app bundle.
JCEF_SRC_DIR="${JCEF_SRC_DIR:-}"

JAVA_OPTIONS=(
  "--add-exports=java.desktop/sun.lwawt=ALL-UNNAMED"
  "--add-exports=java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
  "--add-exports=java.desktop/sun.awt=ALL-UNNAMED"
  "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED"
  "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
  "--add-opens=java.desktop/java.awt=ALL-UNNAMED"
)

# Convert JAVA_OPTIONS array to repeated --java-options
JAVA_OPTIONS_ARGS=()
for opt in "${JAVA_OPTIONS[@]}"; do
  JAVA_OPTIONS_ARGS+=("--java-options" "${opt}")
done

jpackage \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --type app-image \
  --input "${PACKAGE_DIR}" \
  --dest "${DIST_DIR}" \
  --main-class "${MAIN_CLASS}" \
  --main-jar "${FAT_JAR}" \
  --icon ./icon.icns \
  "${JAVA_OPTIONS_ARGS[@]}" \
  --verbose

APP_IMAGE_DIR="${DIST_DIR}/${APP_NAME}.app"
APP_RES_DIR="${APP_IMAGE_DIR}/Contents/Resources"

# Bundle JCEF resources if present
if [[ -n "${JCEF_SRC_DIR}" && -d "${JCEF_SRC_DIR}" ]]; then
  echo "Copying ${JCEF_SRC_DIR} -> ${APP_RES_DIR}/jcef"
  rm -rf "${APP_RES_DIR}/jcef"
  mkdir -p "${APP_RES_DIR}"
  cp -R "${JCEF_SRC_DIR}" "${APP_RES_DIR}/jcef"
else
  echo "WARN: JCEF_SRC_DIR not set or not found; app may download JCEF at runtime."
fi

# ---- Post-build sanity checks (fail fast) ----
echo "Validating app-image..."

if [[ ! -d "${APP_IMAGE_DIR}" ]]; then
  echo "ERROR: app-image not found: ${APP_IMAGE_DIR}" >&2
  exit 1
fi

# jpackage mac layout puts app jars under Contents/app
APP_JAR_PATH="${APP_IMAGE_DIR}/Contents/app/${FAT_JAR}"
if [[ ! -f "${APP_JAR_PATH}" ]]; then
  echo "ERROR: bundled jar not found at: ${APP_JAR_PATH}" >&2
  exit 1
fi

# Ensure the fat jar contains Spring Boot launcher and our app class.
if ! jar tf "${APP_JAR_PATH}" | grep -q "org/springframework/boot/loader/launch/JarLauncher.class"; then
  echo "ERROR: Spring Boot JarLauncher not found inside bundled jar: ${APP_JAR_PATH}" >&2
  exit 1
fi

if ! jar tf "${APP_JAR_PATH}" | grep -q "BOOT-INF/classes/com/wanqu/copilot/CopilotApplication.class"; then
  echo "ERROR: CopilotApplication not found inside bundled jar: ${APP_JAR_PATH}" >&2
  exit 1
fi

echo "Validation OK: ${APP_IMAGE_DIR}"
echo "Built app-image at: ${APP_IMAGE_DIR}"
