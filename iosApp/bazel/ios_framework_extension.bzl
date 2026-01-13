# iOS Static Framework extension for LiteRT-LM
# This file is appended to the LiteRT-LM c/BUILD file during the build process
# to create a fat static framework that bundles all dependencies.

load("@build_bazel_rules_apple//apple:ios.bzl", "ios_static_framework")

# iOS static framework that bundles all dependencies into a single archive.
# This is needed because Swift/Xcode can't easily link against thin Bazel libraries.
ios_static_framework(
    name = "LiteRtLm",
    hdrs = ["engine.h"],
    bundle_name = "LiteRtLm",
    minimum_os_version = "13.0",
    visibility = ["//visibility:public"],
    deps = [":engine"],
)
