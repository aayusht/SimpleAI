Local, simple AI on both Android and iOS built with LiteRT-LM and compose multiplatform.

Tool use is supported and seems to work fine. For now the app downloads and uses Gemma E2B.
By installing this app you agree to gemma's license, I'll include that in the repo eventually.

## Setup

Before building the app, you have to run the build scripts in litertlmBuildScripts to create the framework and the aar. You'd think the Android repo could just use LiteRT-LM directly as a dependency, but there's a bug in the repo [here](https://github.com/google-ai-edge/LiteRT-LM/issues/1181) that as of this commit hasn't been addressed.

I really have no clue how iOS stuff works but managed to hack together a script to build a framework for the ios app as well. This is lower level and is communicated with through C APIs, all the tool processing code is just on top of that.

## Development

I mean I'm not even done yet but in general the tools are going to be defined in tools.json in composeResources, and their respective executor and system prompt should be updated respectively.
