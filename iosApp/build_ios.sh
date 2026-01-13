#!/bin/bash
# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Build script for LiteRT-LM iOS static library
# 
# This script builds the LiteRT-LM C API as a static library for iOS.
# 
# Usage:
#   ./build_ios.sh [--simulator|--device|--all] [--litertlm-dir <path>] [--clean]
#
# Options:
#   --simulator      Build for iOS Simulator (arm64, default)
#   --device         Build for iOS device (arm64)
#   --all            Build for both simulator and device
#   --litertlm-dir   Path to LiteRT-LM directory (default: ../)
#   --clean          Clean bazel build caches before building
#
# Output:
#   The built libraries will be placed in:
#   - build/ios_sim_arm64/ (simulator)
#   - build/ios_arm64/ (device)
#   - build/ios_xcframework/ (universal XCFramework, if --all)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_APP_ROOT="${SCRIPT_DIR}"
BUILD_DIR="${IOS_APP_ROOT}/build"

# Default LiteRT-LM directory (assuming it's in the parent directory)
LITERTLM_DIR="${IOS_APP_ROOT}/.."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Global flags
NEED_WRAPPED_CLANG_FIX=false
CLEAN_BUILD=false

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check for required tools
check_requirements() {
    if ! command -v bazel &> /dev/null && ! command -v bazelisk &> /dev/null; then
        log_error "Bazel or Bazelisk is required but not installed."
        log_info "Install Bazelisk: brew install bazelisk"
        exit 1
    fi
    
    # Use bazelisk if available, otherwise bazel
    if command -v bazelisk &> /dev/null; then
        BAZEL_CMD="bazelisk"
    else
        BAZEL_CMD="bazel"
    fi
    
    log_info "Using: $BAZEL_CMD"
}

# Xcode 16+ / macOS Tahoe compatibility workaround
setup_xcode_compat() {
    log_info "Checking Xcode version..."
    XCODE_VERSION=$(xcodebuild -version | head -1 | awk '{print $2}')
    log_info "Detected Xcode version: $XCODE_VERSION"
    
    # Set environment variable to help with Xcode compatibility
    export DEVELOPER_DIR="$(xcode-select -p)"
    
    MAJOR_VERSION=$(echo "$XCODE_VERSION" | cut -d. -f1)
    if [ "$MAJOR_VERSION" -ge 16 ]; then
        log_warn "Xcode 16+ detected. Using compatibility flags..."
        EXTRA_FLAGS="--xcode_version=${XCODE_VERSION} --macos_sdk_version=$(xcrun --show-sdk-version)"
    else
        EXTRA_FLAGS=""
    fi
}

# Clean bazel caches
clean_bazel_caches() {
    log_info "Cleaning bazel build caches..."
    
    BAZEL_CACHE_DIR="/private/var/tmp/_bazel_$(whoami)"
    
    if [ -d "${BAZEL_CACHE_DIR}" ]; then
        log_info "Removing ${BAZEL_CACHE_DIR}..."
        # Fix permissions first (bazel sets some files as read-only)
        chmod -R +w "${BAZEL_CACHE_DIR}" 2>/dev/null || true
        rm -rf "${BAZEL_CACHE_DIR}" 2>/dev/null || {
            log_warn "Could not fully remove bazel cache. Trying with sudo..."
            sudo rm -rf "${BAZEL_CACHE_DIR}" 2>/dev/null || log_warn "Failed to remove bazel cache - may need manual cleanup"
        }
        log_info "Bazel cache cleaned."
    else
        log_info "No bazel cache found at ${BAZEL_CACHE_DIR}"
    fi
    
    # Also clean bazel output in the LiteRT-LM directory
    if [ -d "${LITERTLM_DIR}" ]; then
        cd "${LITERTLM_DIR}"
        if [ -n "${BAZEL_CMD}" ]; then
            log_info "Running bazel clean in ${LITERTLM_DIR}..."
            ${BAZEL_CMD} clean --expunge 2>/dev/null || true
        fi
    fi
}

# Fix wrapped_clang for macOS Tahoe (26+) / Xcode 26+
# The dyld on macOS Tahoe requires LC_UUID load command which is missing
# from the wrapped_clang binary built by Bazel's apple_support.
fix_wrapped_clang() {
    log_info "Checking for LC_UUID compatibility issue..."
    
    BAZEL_CACHE_DIR="/private/var/tmp/_bazel_$(whoami)"
    WRAPPED_CLANG_SOURCE=""
    WRAPPED_CLANG_BINARY=""
    
    # Find the wrapped_clang source and binary
    if [ -d "${BAZEL_CACHE_DIR}" ]; then
        WRAPPED_CLANG_SOURCE=$(find "${BAZEL_CACHE_DIR}" -path "*apple_support/crosstool/wrapped_clang.cc" -type f 2>/dev/null | head -1)
        WRAPPED_CLANG_BINARY=$(find "${BAZEL_CACHE_DIR}" -path "*local_config_apple_cc/wrapped_clang" -type f 2>/dev/null | head -1)
    fi
    
    if [ -z "${WRAPPED_CLANG_BINARY}" ] || [ -z "${WRAPPED_CLANG_SOURCE}" ]; then
        log_info "No wrapped_clang found (first build or cache cleared). Will fix after first build attempt."
        NEED_WRAPPED_CLANG_FIX=true
        return 0
    fi
    
    # Check if wrapped_clang has LC_UUID
    if otool -l "${WRAPPED_CLANG_BINARY}" 2>/dev/null | grep -q "LC_UUID"; then
        log_info "wrapped_clang already has LC_UUID. No fix needed."
        NEED_WRAPPED_CLANG_FIX=false
        return 0
    fi
    
    log_warn "wrapped_clang is missing LC_UUID. Rebuilding with fix..."
    
    # Compile wrapped_clang with LC_UUID
    clang++ -std=c++17 -O2 -Wl,-random_uuid -arch x86_64 -arch arm64 \
        "${WRAPPED_CLANG_SOURCE}" -o "${WRAPPED_CLANG_BINARY}" 2>&1
    
    if [ $? -eq 0 ]; then
        log_info "Successfully rebuilt wrapped_clang with LC_UUID"
        NEED_WRAPPED_CLANG_FIX=false
    else
        log_error "Failed to rebuild wrapped_clang. Build may fail."
        NEED_WRAPPED_CLANG_FIX=false
    fi
}

# Try build with wrapped_clang fix if needed
try_build_with_fix() {
    local BUILD_CMD="$1"
    local LOG_FILE="$2"
    
    # First attempt
    set +e  # Temporarily disable exit on error
    eval "${BUILD_CMD}" 2>&1 | tee "${LOG_FILE}"
    local BUILD_RESULT=$?
    set -e
    
    if [ $BUILD_RESULT -eq 0 ]; then
        return 0
    fi
    
    # Check if it failed due to LC_UUID issue
    if grep -q "missing LC_UUID load command" "${LOG_FILE}" 2>/dev/null; then
        log_warn "Build failed due to LC_UUID issue. Applying fix..."
        
        # Force fix wrapped_clang
        BAZEL_CACHE_DIR="/private/var/tmp/_bazel_$(whoami)"
        WRAPPED_CLANG_SOURCE=$(find "${BAZEL_CACHE_DIR}" -path "*apple_support/crosstool/wrapped_clang.cc" -type f 2>/dev/null | head -1)
        WRAPPED_CLANG_BINARY=$(find "${BAZEL_CACHE_DIR}" -path "*local_config_apple_cc/wrapped_clang" -type f 2>/dev/null | head -1)
        
        if [ -n "${WRAPPED_CLANG_BINARY}" ] && [ -n "${WRAPPED_CLANG_SOURCE}" ]; then
            log_info "Rebuilding wrapped_clang with LC_UUID..."
            clang++ -std=c++17 -O2 -Wl,-random_uuid -arch x86_64 -arch arm64 \
                "${WRAPPED_CLANG_SOURCE}" -o "${WRAPPED_CLANG_BINARY}" 2>&1 || {
                log_error "Failed to rebuild wrapped_clang"
                return 1
            }
            log_info "Successfully rebuilt wrapped_clang with LC_UUID"
            
            log_info "Retrying build..."
            set +e
            eval "${BUILD_CMD}" 2>&1 | tee "${LOG_FILE}"
            BUILD_RESULT=$?
            set -e
            
            if [ $BUILD_RESULT -eq 0 ]; then
                return 0
            fi
        else
            log_error "Could not find wrapped_clang to fix"
        fi
    fi
    
    return 1
}

# Create a framework from a dylib
create_dylib_framework() {
    local DYLIB_PATH="$1"
    local FRAMEWORK_NAME="$2"
    local OUTPUT_DIR="$3"
    local PLATFORM="$4"  # "iPhoneOS" or "iPhoneSimulator"
    
    if [ ! -f "${DYLIB_PATH}" ]; then
        log_warn "Dylib not found: ${DYLIB_PATH}"
        return 1
    fi
    
    local FRAMEWORK_DIR="${OUTPUT_DIR}/${FRAMEWORK_NAME}.framework"
    
    log_info "Creating ${FRAMEWORK_NAME}.framework from dylib..."
    
    # Clean and create framework structure
    rm -rf "${FRAMEWORK_DIR}"
    mkdir -p "${FRAMEWORK_DIR}/Headers"
    mkdir -p "${FRAMEWORK_DIR}/Modules"
    
    # Copy the dylib as the framework binary
    cp "${DYLIB_PATH}" "${FRAMEWORK_DIR}/${FRAMEWORK_NAME}"
    
    # Update the install name to match framework structure
    install_name_tool -id "@rpath/${FRAMEWORK_NAME}.framework/${FRAMEWORK_NAME}" \
        "${FRAMEWORK_DIR}/${FRAMEWORK_NAME}" 2>/dev/null || true
    
    # Create module.modulemap
    cat > "${FRAMEWORK_DIR}/Modules/module.modulemap" << EOF
framework module ${FRAMEWORK_NAME} {
    header "${FRAMEWORK_NAME}.h"
    export *
}
EOF
    
    # Create a minimal header file
    cat > "${FRAMEWORK_DIR}/Headers/${FRAMEWORK_NAME}.h" << EOF
// ${FRAMEWORK_NAME} Framework
// Auto-generated header for dynamic library framework wrapper

#ifndef ${FRAMEWORK_NAME}_h
#define ${FRAMEWORK_NAME}_h

// This framework wraps the ${FRAMEWORK_NAME} dynamic library.
// The library provides constrained decoding support for Gemma models.

#endif /* ${FRAMEWORK_NAME}_h */
EOF
    
    # Create Info.plist
    cat > "${FRAMEWORK_DIR}/Info.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>${FRAMEWORK_NAME}</string>
    <key>CFBundleIdentifier</key>
    <string>com.google.ai.edge.${FRAMEWORK_NAME}</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>${FRAMEWORK_NAME}</string>
    <key>CFBundlePackageType</key>
    <string>FMWK</string>
    <key>CFBundleShortVersionString</key>
    <string>0.8.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>CFBundleSupportedPlatforms</key>
    <array>
        <string>${PLATFORM}</string>
    </array>
    <key>MinimumOSVersion</key>
    <string>13.0</string>
</dict>
</plist>
EOF
    
    log_info "Created ${FRAMEWORK_DIR}"
    return 0
}

# Create LiteRtLm.framework from static library
create_litertlm_framework() {
    local STATIC_LIB="$1"
    local OUTPUT_DIR="$2"
    local PLATFORM="$3"  # "iPhoneOS" or "iPhoneSimulator"
    
    local FRAMEWORK_DIR="${OUTPUT_DIR}/LiteRtLm.framework"
    
    log_info "Creating LiteRtLm.framework..."
    
    # Clean and create framework structure
    rm -rf "${FRAMEWORK_DIR}"
    mkdir -p "${FRAMEWORK_DIR}/Headers"
    mkdir -p "${FRAMEWORK_DIR}/Modules"
    
    # Copy the static library as the framework binary
    cp "${STATIC_LIB}" "${FRAMEWORK_DIR}/LiteRtLm"
    
    # Copy headers
    cp "${LITERTLM_DIR}/c/engine.h" "${FRAMEWORK_DIR}/Headers/"
    cp "${LITERTLM_DIR}/c/litert_lm_logging.h" "${FRAMEWORK_DIR}/Headers/" 2>/dev/null || true
    
    # Create umbrella header
    cat > "${FRAMEWORK_DIR}/Headers/LiteRtLm.h" << 'EOF'
// LiteRtLm Framework
// Umbrella header for LiteRT LM C API

#ifndef LiteRtLm_h
#define LiteRtLm_h

#include "engine.h"

#endif /* LiteRtLm_h */
EOF
    
    # Create module.modulemap
    cat > "${FRAMEWORK_DIR}/Modules/module.modulemap" << 'EOF'
framework module LiteRtLm {
  umbrella header "LiteRtLm.h"
  export *
  module * { export * }
  link "System"
  link "dl"
  link "m"
  link "objc"
  link "pthread"
  link "resolv"
  link framework "AVFoundation"
  link framework "AudioToolbox"
  link framework "CoreFoundation"
  link framework "Foundation"
  link framework "Metal"
  link framework "Security"
}
EOF
    
    # Add Info.plist
    add_framework_info_plist "${FRAMEWORK_DIR}" "${PLATFORM}"
    
    log_info "Created ${FRAMEWORK_DIR}"
}

# Build for iOS Simulator
build_simulator() {
    log_info "Building for iOS Simulator (arm64)..."
    
    cd "${LITERTLM_DIR}"
    
    # Clean previous build
    rm -rf "${BUILD_DIR}/ios_sim_arm64"
    mkdir -p "${BUILD_DIR}/ios_sim_arm64"
    
    # Try to fix wrapped_clang before build
    fix_wrapped_clang
    
    # Build command - build the engine static library
    BUILD_CMD="${BAZEL_CMD} build \
        --config=ios_sim_arm64 \
        --compilation_mode=opt \
        --copt=-fembed-bitcode \
        --apple_generate_dsym=false \
        ${EXTRA_FLAGS} \
        //c:engine"
    
    # Build with auto-fix for LC_UUID issue
    if ! try_build_with_fix "${BUILD_CMD}" "${BUILD_DIR}/ios_sim_arm64/build.log"; then
        log_error "Simulator build failed. Check ${BUILD_DIR}/ios_sim_arm64/build.log for details."
        exit 1
    fi
    
    # Find the static library
    BAZEL_BIN="${LITERTLM_DIR}/bazel-bin"
    STATIC_LIB="${BAZEL_BIN}/c/libengine.a"
    
    if [ ! -f "${STATIC_LIB}" ]; then
        log_error "Static library not found at ${STATIC_LIB}"
        exit 1
    fi
    
    # Create the framework from the static library
    create_litertlm_framework "${STATIC_LIB}" "${BUILD_DIR}/ios_sim_arm64" "iPhoneSimulator"
    
    # Create GemmaModelConstraintProvider.framework from prebuilt dylib
    if [ -f "${LITERTLM_DIR}/prebuilt/ios_sim_arm64/libGemmaModelConstraintProvider.dylib" ]; then
        create_dylib_framework \
            "${LITERTLM_DIR}/prebuilt/ios_sim_arm64/libGemmaModelConstraintProvider.dylib" \
            "GemmaModelConstraintProvider" \
            "${BUILD_DIR}/ios_sim_arm64" \
            "iPhoneSimulator"
    else
        log_warn "Prebuilt GemmaModelConstraintProvider dylib not found for simulator"
    fi
    
    log_info "iOS Simulator build complete: ${BUILD_DIR}/ios_sim_arm64/"
}

# Build for iOS Device
build_device() {
    log_info "Building for iOS Device (arm64)..."
    
    cd "${LITERTLM_DIR}"
    
    # Clean previous build
    rm -rf "${BUILD_DIR}/ios_arm64"
    mkdir -p "${BUILD_DIR}/ios_arm64"
    
    # Try to fix wrapped_clang before build
    fix_wrapped_clang
    
    # Build command - build the engine static library
    BUILD_CMD="${BAZEL_CMD} build \
        --config=ios_arm64 \
        --compilation_mode=opt \
        --copt=-fembed-bitcode \
        --apple_generate_dsym=false \
        ${EXTRA_FLAGS} \
        //c:engine"
    
    # Build with auto-fix for LC_UUID issue
    if ! try_build_with_fix "${BUILD_CMD}" "${BUILD_DIR}/ios_arm64/build.log"; then
        log_error "Device build failed. Check ${BUILD_DIR}/ios_arm64/build.log for details."
        exit 1
    fi
    
    # Find the static library
    BAZEL_BIN="${LITERTLM_DIR}/bazel-bin"
    STATIC_LIB="${BAZEL_BIN}/c/libengine.a"
    
    if [ ! -f "${STATIC_LIB}" ]; then
        log_error "Static library not found at ${STATIC_LIB}"
        exit 1
    fi
    
    # Create the framework from the static library
    create_litertlm_framework "${STATIC_LIB}" "${BUILD_DIR}/ios_arm64" "iPhoneOS"
    
    # Create GemmaModelConstraintProvider.framework from prebuilt dylib
    if [ -f "${LITERTLM_DIR}/prebuilt/ios_arm64/libGemmaModelConstraintProvider.dylib" ]; then
        create_dylib_framework \
            "${LITERTLM_DIR}/prebuilt/ios_arm64/libGemmaModelConstraintProvider.dylib" \
            "GemmaModelConstraintProvider" \
            "${BUILD_DIR}/ios_arm64" \
            "iPhoneOS"
    else
        log_warn "Prebuilt GemmaModelConstraintProvider dylib not found for device"
    fi
    
    log_info "iOS Device build complete: ${BUILD_DIR}/ios_arm64/"
}

# Add Info.plist to a framework if missing or update platform
add_framework_info_plist() {
    local FRAMEWORK_DIR="$1"
    local PLATFORM_NAME="$2"
    local FRAMEWORK_NAME=$(basename "${FRAMEWORK_DIR}" .framework)
    
    log_info "Setting up Info.plist for ${FRAMEWORK_NAME} (${PLATFORM_NAME})..."
    
    cat > "${FRAMEWORK_DIR}/Info.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>${FRAMEWORK_NAME}</string>
    <key>CFBundleIdentifier</key>
    <string>com.google.ai.edge.litertlm</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>${FRAMEWORK_NAME}</string>
    <key>CFBundlePackageType</key>
    <string>FMWK</string>
    <key>CFBundleShortVersionString</key>
    <string>0.8.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>CFBundleSupportedPlatforms</key>
    <array>
        <string>${PLATFORM_NAME}</string>
    </array>
    <key>MinimumOSVersion</key>
    <string>13.0</string>
</dict>
</plist>
EOF
}

# Create XCFramework
create_xcframework() {
    log_info "Creating XCFrameworks..."
    
    XCFRAMEWORK_DIR="${BUILD_DIR}/ios_xcframework"
    rm -rf "${XCFRAMEWORK_DIR}"
    mkdir -p "${XCFRAMEWORK_DIR}"
    
    # Check if both framework builds exist
    if [ ! -d "${BUILD_DIR}/ios_sim_arm64/LiteRtLm.framework" ]; then
        log_error "Simulator framework not found at ${BUILD_DIR}/ios_sim_arm64/LiteRtLm.framework"
        exit 1
    fi
    
    if [ ! -d "${BUILD_DIR}/ios_arm64/LiteRtLm.framework" ]; then
        log_error "Device framework not found at ${BUILD_DIR}/ios_arm64/LiteRtLm.framework"
        exit 1
    fi
    
    # Verify the frameworks have different platform targets
    log_info "Verifying framework architectures..."
    
    SIM_ARCH=$(lipo -archs "${BUILD_DIR}/ios_sim_arm64/LiteRtLm.framework/LiteRtLm" 2>/dev/null || echo "unknown")
    DEV_ARCH=$(lipo -archs "${BUILD_DIR}/ios_arm64/LiteRtLm.framework/LiteRtLm" 2>/dev/null || echo "unknown")
    
    log_info "Simulator framework arch: ${SIM_ARCH}"
    log_info "Device framework arch: ${DEV_ARCH}"
    
    # Create LiteRtLm XCFramework
    log_info "Creating LiteRtLm.xcframework..."
    xcodebuild -create-xcframework \
        -framework "${BUILD_DIR}/ios_arm64/LiteRtLm.framework" \
        -framework "${BUILD_DIR}/ios_sim_arm64/LiteRtLm.framework" \
        -output "${XCFRAMEWORK_DIR}/LiteRtLm.xcframework"
    
    if [ $? -ne 0 ]; then
        log_error "Failed to create LiteRtLm.xcframework"
        exit 1
    fi
    
    log_info "LiteRtLm.xcframework created successfully"
    
    # Create GemmaModelConstraintProvider XCFramework if both frameworks exist
    if [ -d "${BUILD_DIR}/ios_arm64/GemmaModelConstraintProvider.framework" ] && \
       [ -d "${BUILD_DIR}/ios_sim_arm64/GemmaModelConstraintProvider.framework" ]; then
        
        log_info "Creating GemmaModelConstraintProvider.xcframework..."
        xcodebuild -create-xcframework \
            -framework "${BUILD_DIR}/ios_arm64/GemmaModelConstraintProvider.framework" \
            -framework "${BUILD_DIR}/ios_sim_arm64/GemmaModelConstraintProvider.framework" \
            -output "${XCFRAMEWORK_DIR}/GemmaModelConstraintProvider.xcframework"
        
        if [ $? -ne 0 ]; then
            log_warn "Failed to create GemmaModelConstraintProvider.xcframework"
        else
            log_info "GemmaModelConstraintProvider.xcframework created successfully"
        fi
    else
        log_warn "GemmaModelConstraintProvider frameworks not found, skipping xcframework creation"
    fi
    
    log_info "XCFrameworks created in: ${XCFRAMEWORK_DIR}/"
    ls -la "${XCFRAMEWORK_DIR}/"
}

# Print usage
usage() {
    echo "Usage: $0 [--simulator|--device|--all] [--litertlm-dir <path>] [--clean]"
    echo ""
    echo "Options:"
    echo "  --simulator      Build for iOS Simulator (arm64, default)"
    echo "  --device         Build for iOS device (arm64)"
    echo "  --all            Build for both and create XCFramework"
    echo "  --litertlm-dir   Path to LiteRT-LM directory (default: ../)"
    echo "  --clean          Clean bazel build caches before building"
    echo ""
    echo "Examples:"
    echo "  $0 --simulator                              # Build for simulator (LiteRT-LM in ../)"
    echo "  $0 --all --litertlm-dir /path/to/LiteRT-LM  # Build both with custom path"
    echo "  $0 --clean --all --litertlm-dir /path/to/LiteRT-LM  # Clean and rebuild"
}

# Main
main() {
    log_info "LiteRT-LM iOS Build Script"
    log_info "iOS App root: ${IOS_APP_ROOT}"
    
    BUILD_SIM=false
    BUILD_DEVICE=false
    
    # Parse arguments
    if [ $# -eq 0 ]; then
        BUILD_SIM=true
    fi
    
    while [ $# -gt 0 ]; do
        case "$1" in
            --simulator)
                BUILD_SIM=true
                ;;
            --device)
                BUILD_DEVICE=true
                ;;
            --all)
                BUILD_SIM=true
                BUILD_DEVICE=true
                ;;
            --litertlm-dir)
                shift
                if [ $# -eq 0 ]; then
                    log_error "--litertlm-dir requires a path argument"
                    usage
                    exit 1
                fi
                LITERTLM_DIR="$1"
                ;;
            --clean)
                CLEAN_BUILD=true
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                usage
                exit 1
                ;;
        esac
        shift
    done
    
    # Normalize and validate LiteRT-LM directory
    LITERTLM_DIR="$(cd "${LITERTLM_DIR}" 2>/dev/null && pwd)"
    if [ $? -ne 0 ]; then
        log_error "LiteRT-LM directory not found: ${LITERTLM_DIR}"
        exit 1
    fi
    
    log_info "LiteRT-LM directory: ${LITERTLM_DIR}"
    
    # Verify it looks like a LiteRT-LM directory
    if [ ! -f "${LITERTLM_DIR}/c/engine.h" ] || [ ! -f "${LITERTLM_DIR}/WORKSPACE" ]; then
        log_error "Directory does not appear to be a LiteRT-LM workspace: ${LITERTLM_DIR}"
        log_error "Expected to find c/engine.h and WORKSPACE"
        exit 1
    fi
    
    check_requirements
    setup_xcode_compat
    
    # Clean bazel caches if requested
    if [ "$CLEAN_BUILD" = true ]; then
        clean_bazel_caches
    fi
    
    # Create build directory
    mkdir -p "${BUILD_DIR}"
    
    # Run builds
    if [ "$BUILD_SIM" = true ]; then
        build_simulator
    fi
    
    if [ "$BUILD_DEVICE" = true ]; then
        build_device
    fi
    
    # Create XCFramework if both builds were done
    if [ "$BUILD_SIM" = true ] && [ "$BUILD_DEVICE" = true ]; then
        create_xcframework
    fi
    
    log_info "Build complete!"
    log_info ""
    log_info "Next steps:"
    log_info "1. Add the frameworks to your Xcode project:"
    if [ "$BUILD_SIM" = true ] && [ "$BUILD_DEVICE" = true ]; then
        log_info "   - Drag build/ios_xcframework/LiteRtLm.xcframework to Xcode"
        log_info "   - Drag build/ios_xcframework/GemmaModelConstraintProvider.xcframework to Xcode"
    else
        log_info "   - Add build/ios_*/LiteRtLm.framework to 'Link Binary With Libraries'"
        log_info "   - Add build/ios_*/GemmaModelConstraintProvider.framework to 'Embed Frameworks'"
    fi
    log_info "2. Set up the bridging header in Build Settings"
    log_info "3. Add required frameworks: Accelerate, Metal, MetalPerformanceShaders"
}

main "$@"
