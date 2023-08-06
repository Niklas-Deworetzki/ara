package ara.cli

import java.util.Properties

object BuildInfo {

    private val buildProperties = Properties()

    init {
        javaClass.getResourceAsStream("/build/build.properties").use {
            buildProperties.load(it)
        }
    }

    val version: String
        get() = buildProperties.getProperty("version")

    val buildTime: String
        get() = buildProperties.getProperty("build.time")
}