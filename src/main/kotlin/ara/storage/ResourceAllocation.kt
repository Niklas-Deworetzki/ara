package ara.storage

import ara.storage.extensions.ForMemoryPaths
import ara.storage.extensions.ForResourcePath
import ara.storage.extensions.ForResources
import ara.syntax.Syntax

object ResourceAllocation {

    fun Syntax.Instruction.resourcesCreated(): Collection<Syntax.ResourceExpression> =
        ForResources.resourcesCreated(this)

    fun Syntax.Instruction.resourcesDestroyed(): Collection<Syntax.ResourceExpression> =
        ForResources.resourcesDestroyed(this)

    fun Syntax.Instruction.resources(): Collection<Syntax.ResourceExpression> =
        resourcesCreated() + resourcesDestroyed()


    fun Syntax.Instruction.variablesCreated(): Collection<ResourcePath> =
        this.resourcesCreated().flatMap { it.asResourcePaths() }

    fun Syntax.Instruction.variablesDestroyed(): Collection<ResourcePath> =
        this.resourcesDestroyed().flatMap { it.asResourcePaths() }


    fun Syntax.Storage.asResourcePath(): ResourcePath =
        ForResourcePath.asResourcePath(this)

    fun Syntax.ResourceExpression.asResourcePaths(): Collection<ResourcePath> =
        ForResourcePath.asResourcePaths(this)

    fun Syntax.ArithmeticExpression.asResourcePaths(): Collection<ResourcePath> =
        ForResourcePath.asResourcePaths(this)

    fun Syntax.ConditionalExpression.asResourcePaths(): Collection<ResourcePath> =
        ForResourcePath.asResourcePaths(this)


    fun Syntax.ResourceExpression.allMemoryReferences(): Collection<Syntax.Memory> =
        ForMemoryPaths.allMemoryReferences(this)

    fun Syntax.ResourceExpression.asMemoryPaths(): Collection<MemoryPath> =
        ForMemoryPaths.asMemoryPaths(this)
}