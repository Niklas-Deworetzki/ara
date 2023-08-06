package ara.cli

import picocli.CommandLine.Option

class ApplicationOptions {
    @Option(
        names = ["-h", "--help"], usageHelp = true,
        description = ["Display this help message and exit."]
    )
    var requestedHelp = false


    @Option(
        names = ["--version"],
        versionHelp = true,
        description = ["Display version information and exit."]
    )
    var requestedVersionInfo = false
}