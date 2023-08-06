package ara.cli

import picocli.CommandLine.Option

class AnalysisOptions {
    @Option(
        names = ["--debug", "-d"],
        paramLabel = "<pass>",
        description = ["Specify for which analysis passes debug output should be generated."]
    )
    var debugEnabledPasses: Set<String> = emptySet()
}