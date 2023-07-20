package ara.cli

import picocli.CommandLine.Option

class AnalysisOptions {
    @Option(
        names = ["--debug", "-d"],
        description = ["Passes for which debug output should be printed."]
    )
    var debugEnabledPasses: Set<String> = emptySet()
}