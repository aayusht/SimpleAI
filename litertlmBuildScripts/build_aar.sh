#!/bin/bash
# Build LiteRT-LM with a patch to fix issues/1181
# 
# This script downloads the official Maven AAR (with working native libs),
# extracts the .so files, rebuilds with the patched file, and sticks in the libs
#
# Usage:
#   ./build_aar.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/.."
LITERTLM_DIR="${PROJECT_DIR}/LiteRT-LM"
OUTPUT_DIR="${SCRIPT_DIR}/build"
FINAL_AAR_DIR="${PROJECT_DIR}/composeApp/libs"

# Check ANDROID_NDK_HOME
if [ -z "${ANDROID_NDK_HOME}" ]; then
    echo "❌ ERROR: ANDROID_NDK_HOME is not defined."
    echo "   Please set ANDROID_NDK_HOME to your Android NDK path (required: version 28)"
    exit 1
elif [ ! -d "${ANDROID_NDK_HOME}" ]; then
    echo "❌ ERROR: ANDROID_NDK_HOME points to non-existent directory: ${ANDROID_NDK_HOME}"
    echo "   Please install Android NDK 28 (28.0.12433566 works) and set ANDROID_NDK_HOME accordingly."
    exit 1
else
    ndk_source_props="${ANDROID_NDK_HOME}/source.properties"
    if [ -f "$ndk_source_props" ]; then
        ndk_version=$(grep "Pkg.Revision" "$ndk_source_props" | cut -d' ' -f3)
        if [[ $ndk_version != 28.* ]]; then
            echo "⚠️  WARNING: NDK version is $ndk_version, expected 28.x (recommended: 28.0.12433566)"
            echo "   Please download and use NDK 28.0.12433566 to avoid compatibility issues."
        elif [[ $ndk_version != 28.0.12433566 ]]; then
            echo "   NDK minor version is $ndk_version"
            echo "   If there are issues, switch to 28.0.12433566."
        fi
    else
        echo "⚠️  WARNING: Cannot detect NDK version (missing source.properties at ${ndk_source_props})"
        echo "   Please ensure NDK 28 is installed and referenced by ANDROID_NDK_HOME."
    fi
fi

# Check ANDROID_SDK_HOME
if [ -z "${ANDROID_SDK_HOME}" ]; then
    echo "❌ ERROR: ANDROID_SDK_HOME is not defined."
    echo "   Please set ANDROID_SDK_HOME to your Android SDK path."
    exit 1
elif [ ! -d "${ANDROID_SDK_HOME}" ]; then
    echo "❌ ERROR: ANDROID_SDK_HOME points to non-existent directory: ${ANDROID_SDK_HOME}"
    echo "   Please install the Android SDK and set ANDROID_SDK_HOME accordingly."
    exit 1
fi

if [ -z "$LITERTLM_DIR" ]; then
    echo "❌ ERROR: --litertlm-dir is required"
    echo "Usage: $0 --litertlm-dir <path> [--output-dir <path>]"
    exit 1
fi

# Convert to absolute path
LITERTLM_DIR=$(cd "$LITERTLM_DIR" && pwd)

# Check if directory exists and contains expected files
if [ ! -d "$LITERTLM_DIR" ]; then
    echo "❌ ERROR: LiteRT-LM directory not found: $LITERTLM_DIR"
    exit 1
fi

if [ ! -f "$LITERTLM_DIR/WORKSPACE" ] || [ ! -d "$LITERTLM_DIR/kotlin" ]; then
    echo "❌ ERROR: $LITERTLM_DIR does not appear to be a LiteRT-LM directory"
    echo "   (missing WORKSPACE or kotlin/ folder)"
    exit 1
fi

# Create output directory if it doesn't exist to resolve relative paths
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR=$(cd "$OUTPUT_DIR" && pwd)

AAR_DIR="${OUTPUT_DIR}/aar"
MAVEN_AAR_URL="https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.9.0-alpha01/litertlm-android-0.9.0-alpha01.aar"
MAVEN_AAR_FILE="${OUTPUT_DIR}/maven-litertlm.aar"

echo "=== Building LiteRT-LM (Kotlin Only) ==="
echo "    Source directory: $LITERTLM_DIR"
echo "    Output directory: $OUTPUT_DIR"
echo "    Using official Maven AAR for native libraries"
echo "    Rebuilding only Kotlin classes from source"
echo ""

# Create output directories (will reuse if they exist)
mkdir -p "${AAR_DIR}"
mkdir -p "${OUTPUT_DIR}/maven_extract"
mkdir -p "${AAR_DIR}/classes"

echo "=== Step 1: Downloading official Maven AAR ==="
echo "URL: ${MAVEN_AAR_URL}"

curl -L -o "${MAVEN_AAR_FILE}" "${MAVEN_AAR_URL}"

if [ ! -f "${MAVEN_AAR_FILE}" ]; then
    echo "❌ ERROR: Failed to download Maven AAR"
    exit 1
fi

echo ""
echo "=== Step 2: Extracting native libraries from Maven AAR ==="

cd "${OUTPUT_DIR}/maven_extract"
unzip -q "${MAVEN_AAR_FILE}"
cd - > /dev/null

# Copy the native libraries (jni folder) to our AAR
if [ -d "${OUTPUT_DIR}/maven_extract/jni" ]; then
    cp -r "${OUTPUT_DIR}/maven_extract/jni" "${AAR_DIR}/"
else
    echo "❌ ERROR: No jni folder found in Maven AAR"
    exit 1
fi

# Also copy the LICENSE and THIRD_PARTY_NOTICE if present
if [ -f "${OUTPUT_DIR}/maven_extract/LICENSE" ]; then
    cp -f "${OUTPUT_DIR}/maven_extract/LICENSE" "${AAR_DIR}/"
fi
if [ -f "${OUTPUT_DIR}/maven_extract/THIRD_PARTY_NOTICE.txt" ]; then
    cp -f "${OUTPUT_DIR}/maven_extract/THIRD_PARTY_NOTICE.txt" "${AAR_DIR}/"
fi

echo ""
echo "=== Step 3: Building YOUR Kotlin classes from source ==="
echo "    Source: $LITERTLM_DIR"

# Path to the file we want to patch in the submodule
CONVERSATION_KT_DEST="$LITERTLM_DIR/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt"
CONVERSATION_KT_SRC="$SCRIPT_DIR/Conversation.kt"

# Ensure we have a cleanup trap to restore the file
restore_conversation() {
    if [ -f "${CONVERSATION_KT_DEST}.bak" ]; then
        echo "Restoring original Conversation.kt..."
        # Use cp then rm to avoid issues with symlinks if any exist
        cp "${CONVERSATION_KT_DEST}.bak" "${CONVERSATION_KT_DEST}"
        rm "${CONVERSATION_KT_DEST}.bak"
    fi
}
trap restore_conversation EXIT

# Apply the patch
if [ -f "$CONVERSATION_KT_SRC" ]; then
    echo "Patching Conversation.kt from $CONVERSATION_KT_SRC..."
    # Only backup if we haven't already (e.g. from a previous failed run)
    if [ ! -f "${CONVERSATION_KT_DEST}.bak" ]; then
        cp "$CONVERSATION_KT_DEST" "${CONVERSATION_KT_DEST}.bak"
    fi
    cp "$CONVERSATION_KT_SRC" "$CONVERSATION_KT_DEST"
else
    echo "⚠️ Warning: Patch file not found at $CONVERSATION_KT_SRC, building with original source."
fi

# Build the Kotlin classes (must run from LiteRT-LM directory)
cd "$LITERTLM_DIR"
bazel build -c opt \
    --config=android_arm64 \
    //kotlin/java/com/google/ai/edge/litertlm:litertlm-android_kt

# Extract classes from the Kotlin JAR
KOTLIN_JAR="$LITERTLM_DIR/bazel-bin/kotlin/java/com/google/ai/edge/litertlm/litertlm-android_kt.jar"
if [ -f "$KOTLIN_JAR" ]; then
    echo "✅ Kotlin build successful"
    echo "   Extracting classes from: $KOTLIN_JAR"
    
    # Extract all files from JAR, then remove any .so files that shouldn't be in classes
    unzip -q -o "$KOTLIN_JAR" -d "${AAR_DIR}/classes/"
    # Remove any .so files that may have been bundled (we get these from Maven AAR instead)
    find "${AAR_DIR}/classes" -name "*.so" -delete 2>/dev/null || true
else
    echo "❌ ERROR: Kotlin JAR not found at $KOTLIN_JAR"
    exit 1
fi

# Verify key classes exist
if [ -f "${AAR_DIR}/classes/com/google/ai/edge/litertlm/Conversation.class" ]; then
    echo "✅ Conversation.class found (with your modifications)"
else
    echo "⚠️  Warning: Conversation.class not found"
fi

if [ -f "${AAR_DIR}/classes/com/google/ai/edge/litertlm/Engine.class" ]; then
    echo "✅ Engine.class found"
fi

# Check for .so files that might have leaked into classes
if find "${AAR_DIR}/classes" -name "*.so" | grep -q .; then
    echo "⚠️  WARNING: .so files found in classes directory (this may cause issues)"
    echo "   If you encounter problems, manually clean ${AAR_DIR}/classes and rebuild"
fi

echo ""
echo "=== Step 4: Creating combined AAR package ==="

# Create AndroidManifest.xml (same as Maven's)
cat > "${AAR_DIR}/AndroidManifest.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android
"
    package="com.google.ai.edge.litertlm">
    
    <application>
        <uses-native-library android:name="libvndksupport.so" android:required="false"/>
        <uses-native-library android:name="libOpenCL.so" android:required="false"/>
    </application>
</manifest>
EOF

# Create R.txt (required for AAR format)
touch "${AAR_DIR}/R.txt"

# Create classes.jar from our rebuilt classes
cd "${AAR_DIR}/classes"
jar cvf ../classes.jar . > /dev/null
cd - > /dev/null

echo "✅ Created classes.jar with your Kotlin modifications"
echo "   (Note: ${AAR_DIR}/classes directory preserved for inspection)"

# Create the final AAR
cd "${AAR_DIR}"

# Add non-native files with compression
zip "${FINAL_AAR_DIR}/litertlm-android-modified.aar" AndroidManifest.xml classes.jar R.txt

# Add LICENSE files if present
[ -f "LICENSE" ] && zip "${FINAL_AAR_DIR}/litertlm-android-modified.aar" LICENSE
[ -f "THIRD_PARTY_NOTICE.txt" ] && zip "${FINAL_AAR_DIR}/litertlm-android-modified.aar" THIRD_PARTY_NOTICE.txt

# Add native libraries UNCOMPRESSED (critical for Android!)
zip -0 -r "${FINAL_AAR_DIR}/litertlm-android-modified.aar" jni/

cd - > /dev/null

# Verify
echo ""
echo "Verifying AAR structure..."
if zipinfo "${FINAL_AAR_DIR}/litertlm-android-modified.aar" | grep "\.so" | grep -q "defN"; then
    echo "⚠️  Warning: Some .so files are compressed"
else
    echo "✅ Native libraries stored uncompressed (correct)"
fi

# Show final contents
echo ""
echo "Final AAR contents:"
unzip -l "${FINAL_AAR_DIR}/litertlm-android-modified.aar" | grep -E "\.so|\.jar|\.xml|\.txt" | awk '{print "  " $4 " (" $1 " bytes)"}'

echo ""
echo "=============================================="
echo "=== Build Complete! ==="
echo "=============================================="
echo ""
echo "Output: ${FINAL_AAR_DIR}/litertlm-android-modified.aar"
echo ""
echo "This AAR contains:"
echo "  ✅ Official Maven native libraries (tested & working)"
echo "  ✅ Patched Conversation.kt"
echo ""
echo "Android build should be good to go!"]
