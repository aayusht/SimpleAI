package com.aayush.simpleai.util

const val E4B_MODEL_FILE_NAME = "gemma-3n-E4B-it-int4.litertlm"
const val MODEL_DOWNLOAD_URL_BASE = "https://pub-19ca34c7d9fa4b248a55bf92f72dced6.r2.dev"

const val DOWNLOAD_URL = "$MODEL_DOWNLOAD_URL_BASE/$E4B_MODEL_FILE_NAME"

const val EXPECTED_MODEL_SIZE_BYTES: Long = 4_926_000_000L

const val REQUIRED_STORAGE_BYTES: Long = 8 * 1024 * 1024 * 1024

const val REQUIRED_MEMORY_BYTES: Long = 8 * 1024 * 1024 * 1024