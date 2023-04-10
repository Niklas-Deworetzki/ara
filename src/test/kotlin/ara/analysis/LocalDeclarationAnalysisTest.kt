package ara.analysis.ara.analysis

import ara.Direction
import ara.analysis.LocalDeclarationAnalysis
import ara.syntax.Syntax
import ara.types.Type
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldContainKeys
import org.junit.jupiter.api.Test

class LocalDeclarationAnalysisTest {

    private val intType = Syntax.NamedType(
        Syntax.Identifier("Int")
    )

    @Test
    fun reportsDuplicatesInInputParameters() {
        val routine = Syntax.RoutineDeclaration(
            Syntax.Identifier(
                "invalidInputList"
            ),
            listOf(
                Syntax.Parameter(Syntax.Identifier("duplicate"), intType),
                Syntax.Parameter(Syntax.Identifier("duplicate"), intType)
            ),
            emptyList(),
            emptyList()
        )

        shouldReportError(routine)
    }

    @Test
    fun reportsDuplicatesInOutputParameters() {
        val routine = Syntax.RoutineDeclaration(
            Syntax.Identifier(
                "invalidOutputList"
            ),
            emptyList(),
            listOf(
                Syntax.Parameter(Syntax.Identifier("duplicate"), intType),
                Syntax.Parameter(Syntax.Identifier("duplicate"), intType)
            ),
            emptyList()
        )

        shouldReportError(routine)
    }


    @Test
    fun declaresParameterVariables() {
        val routine = Syntax.RoutineDeclaration(
            Syntax.Identifier(
                "parameters"
            ),
            listOf(Syntax.Parameter(Syntax.Identifier("a"), intType)),
            listOf(Syntax.Parameter(Syntax.Identifier("b"), intType)),
            emptyList()
        )

        shouldNotReportError(routine)
            .shouldContainKeys(
                Syntax.Identifier("a"),
                Syntax.Identifier("b")
            )
    }

    private val instructionCtors = listOf<(Syntax.Expression) -> Syntax.Instruction>(
        { a -> Syntax.Assignment(a, Syntax.IntegerLiteral(2), null) },
        { a -> Syntax.Assignment(Syntax.IntegerLiteral(2), a, null) },
        { a -> Syntax.Call(listOf(a), emptyList(), Direction.FORWARD, Syntax.Identifier("r")) },
        { a -> Syntax.Call(emptyList(), listOf(a), Direction.FORWARD, Syntax.Identifier("r")) }
    )

    private val expressionCtors = listOf<(Syntax.NamedStorage) -> Syntax.Expression>(
        { a -> a },
        { a -> Syntax.ReferenceExpression(a) },
        { a -> Syntax.TypedStorage(a, intType) },
        { a -> Syntax.MemberAccess(a, Syntax.Identifier("member")) },
    )


    private fun makeRoutineDeclaring(
        name: String,
        expressionCtor: (Syntax.NamedStorage) -> Syntax.Expression,
        instructionCtor: (Syntax.Expression) -> Syntax.Instruction,
    ): Syntax.RoutineDeclaration {
        val variable = Syntax.NamedStorage(Syntax.Identifier(name))
        val instruction = instructionCtor(expressionCtor(variable))
        return Syntax.RoutineDeclaration(
            Syntax.Identifier("routine-$name"),
            emptyList(),
            emptyList(),
            listOf(instruction)
        )
    }

    @Test
    fun declaresLocalVariables() {
        for (expressionCtor in expressionCtors) {
            for (instructionCtor in instructionCtors) {
                val routine = makeRoutineDeclaring("x", expressionCtor, instructionCtor)
                val declaredVariables = shouldNotReportError(routine)
                declaredVariables.shouldContainKey(
                    Syntax.Identifier("x")
                )
            }
        }
    }


    private fun shouldReportError(routineDeclaration: Syntax.RoutineDeclaration) {
        val program = Syntax.Program(
            listOf(routineDeclaration)
        )
        val analysis = LocalDeclarationAnalysis(program)

        analysis.runAnalysis()

        analysis.reportedMessages.shouldNotBeEmpty()
    }

    private fun shouldNotReportError(routineDeclaration: Syntax.RoutineDeclaration): Map<Syntax.Identifier, Type> {
        val program = Syntax.Program(
            listOf(routineDeclaration)
        )
        val analysis = LocalDeclarationAnalysis(program)

        analysis.runAnalysis()

        analysis.reportedMessages.shouldBeEmpty()
        return routineDeclaration.localScope
    }
}