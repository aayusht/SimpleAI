Local, simple AI on both Android and iOS built with LiteRT-LM and compose multiplatform.

Tool use is supported and seems to work fine. For now the app downloads and uses Gemma E2B.
By installing this app you agree to gemma's license, I'll include that in the repo eventually.

## Setup

Before building the app, you have to run the build scripts in litertlmBuildScripts to create the framework and the aar. ~~You'd think the Android repo could just use LiteRT-LM directly as a dependency, but there's a bug in the repo [here](https://github.com/google-ai-edge/LiteRT-LM/issues/1181) that as of this commit hasn't been addressed.~~ <- While this is still true, I've since found that there's no way to prefill through the kotlin API, so this build script now just patches the JNI to be public and uses the session API to reconstruct the conversation API. I cannot for the life of me build LiteRT-LM myself due to missing GPU library deps so this is what I do for now.

I really have no clue how iOS stuff works but managed to hack together a script to build a framework for the ios app as well. This is lower level and is communicated with through C APIs, all the tool processing code is just on top of that. I've also patched in support for prefilling messages, which for whatever reason is excluded.

One major downside to this is that constrained decoding is lost. Doesn't seem to be an issue yet, but if people start having issues I'll go back to trying to extend the jni.

## Development

I mean I'm not even done yet but in general the tools are defined in ToolDefinition.kt, and the system prompt is in system_prompt.md.
