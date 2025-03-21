package com.donut.mixfilecli

import com.donut.mixfiledesktop.server.UPLOADERS
import com.donut.mixfiledesktop.server.startServer
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class Config(
    val uploader: String = "A1",
    val uploadTask: Int = 10,
    val downloadTask: Int = 5,
    val port: Int = 4719,
    val uploadRetry: Int = 3,
    val customUrl: String = "",
    val customReferer: String = "",
    val host: String = "0.0.0.0",
)

var config: Config = Config()


@OptIn(ExperimentalHoplite::class, ExperimentalCoroutinesApi::class)
fun main(args: Array<String>) {
    checkConfig()
    config = ConfigLoaderBuilder.default()
        .addFileSource("config.yml")
        .withExplicitSealedTypes()
        .build()
        .loadConfigOrThrow<Config>()
    println(config)
    UPLOADERS
    startServer()
}


fun checkConfig() {
    val currentDir = System.getProperty("user.dir")
    val inputStream: InputStream? = object {}.javaClass.getResourceAsStream("/config.yml")
    val outputFile = File(currentDir, "config.yml")
    if (!outputFile.exists()) {
        FileOutputStream(outputFile).use { outputStream ->
            inputStream?.copyTo(outputStream)
        }
    }
}