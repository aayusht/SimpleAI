# Simple AI - LiteRT-LM iOS Demo App

A simple iOS chat application demonstrating how to use LiteRT-LM for on-device inference with Gemma 3n models.

## Prerequisites

- **Xcode 16.2+** (or 15.0+)
- **Bazel 7.6.1** (install via Bazelisk: `brew install bazelisk`)
- **macOS** with Apple Silicon (for iOS Simulator builds)

## Setup Instructions

### Step 1: Build the LiteRT-LM Static Library

From the Simple AI directory, run:

```bash
# Build for iOS Simulator (for testing)
# Assumes LiteRT-LM is in the parent directory
./build_ios.sh --simulator

# Or specify a custom LiteRT-LM directory
./build_ios.sh --simulator --litertlm-dir /path/to/LiteRT-LM

# Or build for both simulator and device
./build_ios.sh --all
```

This will create:
- `build/ios_sim_arm64/LiteRtLm.framework` - Simulator framework
- `build/ios_arm64/LiteRtLm.framework` - Device framework  
- `build/ios_xcframework/LiteRtLm.xcframework` - Universal framework (if --all)

### Step 2: Configure Xcode Project

1. **Open the project** in Xcode:
   ```bash
   open "Simple AI/Simple AI.xcodeproj"
   ```

2. **Add the framework**:
   - Go to the target's **General** tab
   - Under **Frameworks, Libraries, and Embedded Content**
   - Click **+** and choose **Add Other... > Add Files...**
   - Navigate to `build/ios_sim_arm64/LiteRtLm.framework`
   - Make sure it shows **"Do Not Embed"** (it's a static framework)

3. **Add the Constrained Decoding Library**:
   - Still in **Frameworks, Libraries, and Embedded Content**
   - Click **+** → **Add Other...** → **Add Files...**
   - Navigate to `build/ios_sim_arm64/libGemmaModelConstraintProvider.dylib`
   - Change Embed to **"Embed & Sign"**

4. **Set the Bridging Header**:
   - Go to **Build Settings**
   - Search for "Bridging Header"
   - Set **Objective-C Bridging Header** to:
     ```
     Simple AI/LiteRtLm-Bridging-Header.h
     ```

5. **Add Framework Search Paths**:
   - In **Build Settings**, search for "Framework Search Paths"
   - Add: `$(SRCROOT)/build/ios_sim_arm64` (for Simulator)
   - Add: `$(SRCROOT)/build/ios_arm64` (for Device)

6. **Add Required Frameworks**:
   - Go to **Build Phases > Link Binary With Libraries**
   - Verify these are present (most should be auto-linked):
     - `Accelerate.framework`
     - `Metal.framework`
     - `AVFoundation.framework`
     - `AudioToolbox.framework`
     - `Security.framework`

7. **Verify Linker Flags**:
   - In **Build Settings**, search for "Other Linker Flags"
   - Should contain: `-ObjC`
   - Remove duplicate `-lc++` entries if any

### Step 3: Download the Model

The app will prompt you to download the model on first launch. Alternatively:

1. Download manually from: https://pub-19ca34c7d9fa4b248a55bf92f72dced6.r2.dev/gemma-3n-E2B-it-int4.litertlm
2. Place it in the app's Documents/LiteRtLmModels directory

### Step 4: Run the App

1. Select your target device (Simulator or physical device)
2. Build and run (⌘R)

## Project Structure

```
Simple AI/
├── Simple AI/
│   ├── ContentView.swift          # Main chat UI
│   ├── Simple_AIApp.swift         # App entry point
│   ├── LiteRtLm-Bridging-Header.h # C API bridging header
│   └── LiteRtLm/
│       ├── LiteRtLmEngine.swift       # Engine wrapper
│       ├── LiteRtLmConversation.swift # Conversation wrapper
│       ├── LiteRtLmError.swift        # Error types
│       └── LiteRtLmModelDownloader.swift # Model download utility
├── LiteRtLm.xcconfig              # Build configuration
└── README.md                      # This file
```

## Troubleshooting

### Build Errors

**"Undefined symbols for architecture..."**
- Ensure all required frameworks are linked
- Check that library search paths are correct

**"Bridging header not found"**
- Verify the path in Build Settings matches the actual file location

**Bazel build fails with Xcode 16**
- Try adding `--xcode_version=16.2` to the build command
- Ensure you have the latest .bazelrc from the repo

### Runtime Errors

**"Model not found"**
- Use the in-app download feature or manually place the model file

**"Failed to create engine"**
- Check that the model file is not corrupted
- Ensure sufficient device storage (model is ~3GB)

**Slow first inference**
- First run compiles the model for your device; subsequent runs are faster
- This can take 30-60 seconds

## API Reference

### LiteRtLmEngine

```swift
let config = LiteRtLmEngineConfig(
    modelPath: "/path/to/model.litertlm",
    backend: .cpu
)
let engine = try LiteRtLmEngine(config: config)
```

### LiteRtLmConversation

```swift
let conversation = try engine.createConversation(
    config: LiteRtLmConversationConfig(
        systemMessage: "You are a helpful assistant."
    )
)

// Synchronous
let response = try conversation.sendMessage("Hello!")

// Streaming
for try await chunk in conversation.sendMessageStream("Tell me a story") {
    print(chunk, terminator: "")
}
```

## License

MIT License - see the root LICENSE file.
