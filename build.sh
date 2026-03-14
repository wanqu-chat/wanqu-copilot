#!/usr/bin/env bash

set -euo pipefail

# Build jar first
mvn -DskipTests clean package

PACKAGE_DIR="packaging/mac-amd64"
DIST_DIR="dist/mac-amd64"

APP_NAME="WanquCopilot"

MAIN_CLASS="org.springframework.boot.loader.launch.JarLauncher"

ARTIFACT_ID="wanqu-copilot"
VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version | tr -d '\r' | tr -cd '0-9A-Za-z.\-')"
FAT_JAR="${ARTIFACT_ID}-${VERSION}.jar"

rm -rf "${PACKAGE_DIR}" "${DIST_DIR}"
mkdir -p "${PACKAGE_DIR}" "${DIST_DIR}"

cp "target/${FAT_JAR}" "${PACKAGE_DIR}/"

# Optional: bundle pre-downloaded JCEF into app.
# Set JCEF_SRC_DIR to a directory (e.g. ./jcef) that contains the JCEF install root.
JCEF_SRC_DIR="${JCEF_SRC_DIR:-}"

JAVA_OPTIONS=(
  "--add-exports=java.desktop/sun.lwawt=ALL-UNNAMED"
  "--add-exports=java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
  "--add-exports=java.desktop/sun.awt=ALL-UNNAMED"
  "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED"
  "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
  "--add-opens=java.desktop/java.awt=ALL-UNNAMED"
)

JAVA_OPTIONS_ARGS=()
for opt in "${JAVA_OPTIONS[@]}"; do
  JAVA_OPTIONS_ARGS+=("--java-options" "${opt}")
done

jpackage \
  --name "${APP_NAME}" \
  --app-version "${VERSION}" \
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

if [[ -n "${JCEF_SRC_DIR}" && -d "${JCEF_SRC_DIR}" ]]; then
  echo "Copying ${JCEF_SRC_DIR} -> ${APP_RES_DIR}/jcef"
  rm -rf "${APP_RES_DIR}/jcef"
  mkdir -p "${APP_RES_DIR}"
  cp -R "${JCEF_SRC_DIR}" "${APP_RES_DIR}/jcef"
else
  echo "WARN: JCEF_SRC_DIR not set or not found; app may download JCEF at runtime."
fi

echo "Validating app-image..."

APP_JAR_PATH="${APP_IMAGE_DIR}/Contents/app/${FAT_JAR}"
if [[ ! -f "${APP_JAR_PATH}" ]]; then
  echo "ERROR: bundled jar not found at: ${APP_JAR_PATH}" >&2
  exit 1
fi

if ! jar tf "${APP_JAR_PATH}" | grep -q "org/springframework/boot/loader/launch/JarLauncher.class"; then
  echo "ERROR: Spring Boot JarLauncher not found inside bundled jar: ${APP_JAR_PATH}" >&2
  exit 1
fi

echo "Validation OK: ${APP_IMAGE_DIR}"
echo "Built app-image at: ${APP_IMAGE_DIR}"
