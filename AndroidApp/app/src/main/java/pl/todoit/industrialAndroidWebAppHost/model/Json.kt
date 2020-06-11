package pl.todoit.industrialAndroidWebAppHost.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

val jsonForgiving = Json(
    JsonConfiguration(
    encodeDefaults = true,
    ignoreUnknownKeys = true, //!!!
    isLenient = false,
    serializeSpecialFloatingPointValues = false,
    allowStructuredMapKeys = true,
    prettyPrint = false,
    unquotedPrint = false,
    indent = "    ",
    useArrayPolymorphism = false,
    classDiscriminator = "type"
))

val jsonStrict = Json(JsonConfiguration.Stable)
