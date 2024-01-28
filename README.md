# ARA – Advanced assembly-like language for reversible computing

ARA is an experimental reversible programming language, designed as an abstraction layer for low-level reversible programming.
In its core it is based on RSSA, but aims to provide a better user-experience by omitting some of the SSA restrictions.

The main features of ARA include:

- A simple type system including structures.
- Routine-level scopes for variables.
- Dataflow-based detection of reversibility violations.

In its current state, ARA is still under development.
The compiler interface, error messages, semantics and syntax are therefore prone to change.

## Language Philosophy

This language is largely inspired from working with RSSA, a reversible intermediate language and SSA form designed by Torben Æ. Mogensen.
While there are syntactic resemblances and similar ideas, this language attempts to create an entirely new programming experience with reversible programming languages.
It borrows the following concepts from RSSA:

- instruction-based programming with instruction-level reversibility.
- initialization and finalization to ensure reversibility.
- control flow via entry and exit points.

However, key features are added to this language and some core concepts of RSSA are changed:

- a type system with structure types, allowing operations to act on multiple values.
- re-assignable variables, increasing the ease of programming by abandoning the SSA form.
- routine-level scopes in both syntax and semantic.

Still, this language aims to be a low-level reversible programming language.
It aims to provide all features necessary to develop comprehensible programs, but doesn't try to provide high-level features that aid large-scale projects.
As the name suggests, this language is attended as a reversible assembly language, allowing the programmer to structure data, manipulate data, re-use code as routines and assist with advanced analysis techniques.

## Language Features

### Type System

The type system is simple, allowing the user to specify two different types of types: *Integers* and *Structures* with named members.
Type aliases can be specified by introducing a type definition using the `type` keyword.
Every member of a structure type have a name and another type.
Types are specified either by name (referring to the primitive integer type `Int` or any user-defined type alias), as a structure type expression or as a reference to another type.
Two types are considered equivalent if they are structurally equivalent.

```
// An alias for the primitive type Int
type Alias = Int

// An alias for a structure type with two members.
type StructureTypeName = { member1: Int, member2: Alias }

// An alias for a reference to a type named 'T'
type Ref = &T
```

### Routines

Routines behave similarly to RSSA.
Their syntax has been changed to accommodate changes to the behavior of variables and to emphasize the signature of a routine.
As variables are allowed to have complex types, it is crucial to see not only the amount of input and output values but also their types.

```
routine example(input1: Int, input2: SomeType) -> (output: OtherType) {
    ...
}
```

Similar to RSSA, a routine is composed of a series of instructions which can be grouped into blocks.
These blocks start with an entry point (an instruction that can be the target of a branch) and end with an exit point (an instruction initiating a branch).
The first block is implicitly started by the routine and does not have an explicit entry point.
The last block is also implicitly terminated by the routine and does not have an explicit exit point.
A routine consisting of only a single block therefore requires no explicit entry and exit points.
These implicit entry and exit points are a difference to RSSA, where a pair of explicit but syntactically loosely coupled instructions is used to mark the boundaries of a routine.
The explicit syntactic structure of routines in ARA mirrors the semantic scope of variables, which are visible everywhere within a routine.
Without the SSA property, variables can be mutated and re-used.

### Control Flow

During forward execution, routines are usually executed by executing their instructions individually from top to bottom.
Control flow is realized with entry and exit points, as in RSSA.
Backwards execution behaves as expected with instructions being executed in reversed order, with inverse semantics.
Control flow is inverted by exchanging the semantics of entry and exit points.

A difference to RSSA is, that no parameter lists are specified as part of control flow.

```
routine loop(iterations: Int) -> (iterations: Int) {
    n := 0
    -> Enter
    
    <- Enter, Loop (n == 0)
    n := n + 1
    -> Exit, Loop (n == iterations)
    
    <- Exit
    0 := n - iterations
}
```

### Arithmetic Instructions & Assignments

The arithmetic and assignment instructions provided are very similar to RSSA.
Resources on the right-hand side are finalized and their values are used to initialize resources on the left-hand side of an assignment.

An arithmetic instruction allows the modification of the finalized value with some arbitrary computed value.
This value can either be a simple atomic expression (such as a variable or literal) or a binary expression combining two atomic expressions with some basic arithmetic operator.
The computed arithmetic value is used to modify the finalized value using one of the three injective modification operators:
- Addition (`+`)
- Subtraction (`-`)
- Exclusive Or (`^`)

```
dst := src + (n * 3)
```

It is possible to elide the modification operator and the subsequent expression in order to transfer values from one resource to another.
In order to exchange values between variables, multi-assignments are possible that exchange multiple resources at once.
This is done by finalizing all resources on the right-hand side and then initializing all resources on the left-hand side.

```
a, b, c := x, y, z
```

Call instructions behave as they do in RSSA, finalizing resources on the right-hand side, passing their values to the called routine, executing the routine and using the values returned by the routine to initialize resources on the left-hand side.

```
(0) := call f(x, y)
```

### Resource Expressions

The concept of *Atoms* in RSSA is extended upon to represent the complex values available in ARA.
Values that can be initialized or finalized are called *Resource* and resource expressions are used to describe them.
Resources are split into two categories: Literals and storage.

A literal represents some value in ARA.
Integer literals behave exactly as they do in RSSA.
When finalized, their value is simply made available.
When initialized, the value used for initialization is verified to be the same as the literal.
Additionally, structure literals can be used to express structure values.
They associate the names of their members with additional resource expressions.
In order to initialize a structure literal, all its members are initialized.
In order to finalize a structure literal, all its members are finalized.
As structure literals are resource expressions, initialization and finalization can occur recursively.

```
// An integer literal:
3

// A structure literal:
{ x = 3, y = { a = 2 } } 
```

Storage expressions represent some value stored in a memory location and are an extension of variables.
In addition to initializing the entire structure represented by some variable, it is possible to initialize only some of its members.
The same is true for finalization.
Every storage expression describes exactly one memory location, the size of which is determined by the storage's type.

```
// point is a { x: Int, y: Int }
point.x := point.x + 3
```

When some storage is initialized, it or the structure it is part of must have been finalized before, similar to how variables can only be initialized if they are not initialized already.
Similarly, finalizing some storage is only possible if it or the structure it is part of has been initialized before.

### Structured Managed Memory

There are two ways to interact with memory:
Memory is allocated (and therefore also released) using a resource expression `&()`.
It finalizes the expression between the parentheses and places its value into some newly allocated memory on the right-hand side of an assignment.
Or initializes the said expression using the value referenced by a reference value before releasing the reference.

```
// Allocates some memory, writes the literal 4 to it and assigns the reference to 'allocated'.
ref := &(4)

// Releases the reference 'ref' and uses the referenced value to initialize 'x'.
&(x) := ref
```

In-memory values can be accessed similar to resource path expressions, with the addition of using `&` as a suffix operator to follow a reference.
Values in memory must **always be live**.
If they are consumed on the right-hand side of an assignment, they must be initialized on the left-hand side.

```
routine increment_reference( ref: &{ mem: Int } -> ref ) {
    ref&.mem := ref&.mem + 1
}
```

### Automated Liveness Analysis

As variables are available over the entire scope of a routine, some automated analysis is required to infer which storage is initialized and which is finalized.
This information decides which operations are valid to perform on some storage.
Liveness analysis is implemented as a dataflow analysis, marking storage on the left-hand side of assignments as initialized and storage on the right-hand side of assignments as finalized.
This analysis is complicated by the nature of structure values, which can be partially initialized and partially finalized.
Partial initialization in itself is no error condition and a feature of this language.
However, in order to finalize a structure, all of its members must be initialized and in order to initialize a structure none of its members must be initialized.
That is, initialization and finalization is only possible on fully initialized of finalized members.

The liveness checker reports any violations of reversibility that occur from:
- Initialization of already initialized resources.
- Initialization of partially initialized resources.
- Finalization of already finalized resources.
- Finalization of partially finalized resources.
- Use of fully or partially finalized resources.
- Use of only partially initialized resources.

Error messages produced by the checker aim to support programmers on their error hunt and pin down the location of erroneous initializers.

---

Copyright (C) 2023, Niklas Deworetzki
