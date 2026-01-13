# Xcode Setup Guide for Simple AI

## Prerequisites

Before setting up Xcode, you need to build the LiteRtLm framework:

```bash
cd /path/to/Simple\ AI

# Build the iOS static framework (this bundles all dependencies)
# By default, assumes LiteRT-LM is in the parent directory
./build_ios.sh --simulator

# Or specify a custom LiteRT-LM directory
./build_ios.sh --simulator --litertlm-dir /path/to/LiteRT-LM
```

**Note**: If you encounter "missing LC_UUID load command" errors, the build script handles this automatically.

---

## Step-by-Step Xcode Configuration

### 1. Open Your Project
```bash
open "Simple AI.xcodeproj"
```

---

## Part 1: Add the LiteRtLm Framework

### Step 1.1: Select Your Target
1. In Xcode, click on the **blue project icon** at the top of the left sidebar
2. In the main area, make sure **"Simple AI"** target is selected (under TARGETS)

### Step 1.2: Add the Framework
1. Click the **"General"** tab at the top
2. Scroll down to **"Frameworks, Libraries, and Embedded Content"**
3. Click the **"+"** button
4. Click **"Add Other..."** → **"Add Files..."**
5. Navigate to: `build/ios_sim_arm64/`
6. Select `LiteRtLm.framework` and click **"Open"**
7. Make sure it shows **"Do Not Embed"** in the Embed column (it's a static framework)

**Important**: Remove any old `libengine.a` reference if present.

### Step 1.3: Add the Constrained Decoding Library
This dynamic library provides support for function calling / structured output:

1. Still in **"Frameworks, Libraries, and Embedded Content"**
2. Click **"+"** → **"Add Other..."** → **"Add Files..."**
3. Navigate to: `build/ios_sim_arm64/`
4. Select `libGemmaModelConstraintProvider.dylib` → **Open**
5. **Important**: Change Embed to **"Embed & Sign"** (this is a dynamic library that must be bundled with your app)

---

## Part 2: Configure Build Settings

### Step 2.1: Open Build Settings
1. With your target selected, click the **"Build Settings"** tab
2. Make sure **"All"** is selected (not "Basic")
3. Make sure **"Combined"** is selected (not "Levels")

### Step 2.2: Set Bridging Header
1. Search for: `bridging`
2. Find **"Objective-C Bridging Header"**
3. Double-click and enter: `Simple AI/LiteRtLm-Bridging-Header.h`

### Step 2.3: Add Framework Search Paths
1. Search for: `framework search`
2. Find **"Framework Search Paths"**
3. Double-click and add: `$(SRCROOT)/build/ios_sim_arm64`

### Step 2.4: Verify Linker Flags
1. Search for: `other linker`
2. Find **"Other Linker Flags"**
3. Should contain: `-ObjC` (this ensures all Objective-C categories are loaded)
4. Remove duplicate `-lc++` entries if any

---

## Part 3: Required System Frameworks

The module.modulemap in LiteRtLm.framework should auto-link most dependencies, but verify these are present:

### Step 3.1: Go to Build Phases
1. Click the **"Build Phases"** tab
2. Expand **"Link Binary With Libraries"**

### Step 3.2: Verify Frameworks
You should see:
- ✓ LiteRtLm.framework
- ✓ Metal.framework (may be auto-linked)
- ✓ AVFoundation.framework (may be auto-linked)
- ✓ AudioToolbox.framework (may be auto-linked)
- ✓ Security.framework (may be auto-linked)
- ✓ Accelerate.framework (add if missing)

If any are missing, click **"+"** and add them.

---

## Part 4: Verify Setup

### Check Your Build Phases
In **"Link Binary With Libraries"**, you should have:
- ✓ LiteRtLm.framework (Do Not Embed - static framework with all dependencies)
- ✓ libGemmaModelConstraintProvider.dylib (Embed & Sign - for constrained decoding)
- Plus any required system frameworks

### Check Your Build Settings
- ✓ **Bridging Header**: `Simple AI/LiteRtLm-Bridging-Header.h`
- ✓ **Framework Search Paths**: Contains `$(SRCROOT)/build/ios_sim_arm64`
- ✓ **Other Linker Flags**: `-ObjC`

---

## Part 5: Build and Run

1. Select your target device:
   - Click the device selector in the top toolbar
   - Choose **"iPhone 16 Pro"** or any iOS Simulator

2. Press **⌘ + B** to build

3. If successful, press **⌘ + R** to run!

---

## Troubleshooting

### Build fails with "missing LC_UUID load command" (macOS Tahoe / Xcode 26+)

This is a known issue with Bazel on macOS Tahoe. The build script handles this automatically:

```bash
./build_ios.sh --simulator
```

Or manually fix it:
```bash
BAZEL_CACHE="/private/var/tmp/_bazel_$(whoami)"
SRC=$(find "$BAZEL_CACHE" -path "*apple_support/crosstool/wrapped_clang.cc" | head -1)
BIN=$(find "$BAZEL_CACHE" -path "*local_config_apple_cc/wrapped_clang" | head -1)
clang++ -std=c++17 -O2 -Wl,-random_uuid -arch x86_64 -arch arm64 "$SRC" -o "$BIN"
```

### "Framework not found LiteRtLm"
- Verify Framework Search Paths includes the correct path: `$(SRCROOT)/build/ios_sim_arm64`
- Make sure the framework was extracted: check `build/ios_sim_arm64/LiteRtLm.framework` exists
- Run the build script: `./build_ios.sh --simulator`

### "Undefined symbol _litert_lm_*"
- Make sure you're using `LiteRtLm.framework`, not the old `libengine.a`
- Verify the framework is listed in "Link Binary With Libraries"
- Add `-ObjC` to Other Linker Flags

### "Undefined symbol _LiteRtLmGemmaModelConstraintProvider_*"
- Add `libGemmaModelConstraintProvider.dylib` from `build/ios_sim_arm64/`
- Make sure it's set to **"Embed & Sign"** (not "Do Not Embed")
- Add Library Search Path: `$(SRCROOT)/build/ios_sim_arm64`

### "Bridging header not found"
- Make sure the path is exactly: `Simple AI/LiteRtLm-Bridging-Header.h`
- Check that the file exists in the file navigator

### Build warnings about "UIUtilities"
- Check "Link Binary With Libraries" and remove any `UIUtilities` reference

### Duplicate library warnings
- Check "Other Linker Flags" and remove duplicate entries

---

## Framework Architecture

The `LiteRtLm.framework` is a ~270MB static framework that bundles:
- LiteRT-LM C API
- TensorFlow Lite runtime
- Abseil libraries
- Protobuf
- HuggingFace Tokenizers (Rust)
- And all other dependencies

This is much larger than the thin `libengine.a` (~1MB) but solves all linking issues.

---

## Building for Device

To build for a physical iOS device:

```bash
./build_ios.sh --device

# Or with custom LiteRT-LM directory
./build_ios.sh --device --litertlm-dir /path/to/LiteRT-LM
```

Then update Framework Search Paths to include `$(SRCROOT)/build/ios_arm64` for Release builds.

---

## Using Simple AI in a Separate Repository

If you've moved the Simple AI directory to a separate repository:

1. **Clone or copy the Simple AI directory** to your desired location

2. **Build the framework** by specifying the LiteRT-LM directory:
   ```bash
   cd /path/to/Simple\ AI
   ./build_ios.sh --simulator --litertlm-dir /path/to/LiteRT-LM
   ```

3. **The build outputs** will be placed in `Simple AI/build/` (not in the LiteRT-LM repo)

4. **Xcode paths are relative** to the Simple AI directory, so no additional configuration is needed

5. **For CI/CD**, you can:
   - Clone both repos
   - Run the build script with `--litertlm-dir` pointing to the LiteRT-LM clone
   - Commit the built frameworks to your iOS repo, or rebuild on each CI run
