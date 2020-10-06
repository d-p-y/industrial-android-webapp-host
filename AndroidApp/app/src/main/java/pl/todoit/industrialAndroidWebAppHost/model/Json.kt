package pl.todoit.industrialAndroidWebAppHost.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

val jsonForgiving = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

val jsonStrict = Json {}
