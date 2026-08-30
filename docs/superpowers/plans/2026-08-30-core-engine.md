# Core Sudoku Engine Implementation Plan (M0–M2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A tested, pure-Kotlin sudoku engine that models a grid, solves it, proves whether the solution is unique, and explains its reasoning in human terms.

**Architecture:** Two Gradle modules with no Android dependency — `core:model` holds the data types, `core:solver` holds the solving logic. Constraint propagation in the style of Norvig, backed by bitmask candidate sets, with backtracking for the general case. A separate technique solver reproduces human solving methods so hints can explain themselves. Everything runs and is tested on the JVM in milliseconds.

**Tech Stack:** Kotlin 2.2.20, Gradle 9.4.0, JDK 21 (auto-provisioned), `kotlin("test")` on JUnit Platform.

**Spec:** `docs/superpowers/specs/2026-08-30-camera-sudoku-solver-design.md` — this plan covers milestones M0, M1 and M2 only. Vision, recognition and the app UI are separate plans.

---

## Environment notes — already verified on this machine

These were tested before writing the plan; do not re-litigate them.

- Gradle 9.4.0 is cached at `C:\Users\anton\.gradle\wrapper\dists\gradle-9.4.0-bin\lcvyxq3t37f6mx9miaydrrgs\gradle-9.4.0\bin\gradle`. Use that absolute path for Task 1 only, to generate the wrapper. Every later task uses `./gradlew`.
- The only installed JDK is 25, which Gradle 9.4.0 runs on happily. The build targets JDK 21 via a toolchain, and the `foojay-resolver-convention` plugin downloads it automatically on first build. **Do not ask the user to install a JDK.**
- Maven Central is reachable.
- The first build downloads a JDK and takes a few minutes. Later builds are fast.
- The three puzzle fixtures in Task 7 were checked against an independent solver before this
  plan was written: EASY has 30 givens and exactly one solution, HARDEST has 21 givens and
  exactly one solution, AMBIGUOUS has 28 givens and more than one. A test that fails on those
  counts indicates a bug in the new code, not a bad fixture.

## Naming decision — confirm before Task 1

The plan uses the package root **`io.github.tonyxmelon.aisudoku`**. Reverse-DNS on the GitHub account is unique and valid for Play Store publication. It is cheap to change now and impossible to change after the app is published, so raise it with the user before Task 1 if there is any doubt.

## File structure

```
settings.gradle.kts                  module list, foojay resolver, repositories
build.gradle.kts                     root, holds nothing but the Kotlin plugin version
gradle/libs.versions.toml            single source of truth for versions
gradle.properties                    JVM args, Kotlin flags
.github/workflows/ci.yml             build + test on push
.gitignore                           already present

core/model/build.gradle.kts
core/model/src/main/kotlin/io/github/tonyxmelon/aisudoku/model/
    Coordinates.kt                   row/column/box/peer index tables. No state.
    Cell.kt                          Cell and CellSource, with invariants
    Grid.kt                          immutable 81-cell grid, parsing, conflict detection
core/model/src/test/kotlin/io/github/tonyxmelon/aisudoku/model/
    CoordinatesTest.kt  CellTest.kt  GridTest.kt

core/solver/build.gradle.kts
core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/
    CandidateSet.kt                  bitmask over digits 1..9
    SolverState.kt                   mutable candidate grid + constraint propagation
    Solver.kt                        backtracking search, solution counting, SolveResult
    Deduction.kt                     what a technique found, and why
    Technique.kt                     the technique interface and the ordered registry
    NakedSingle.kt                   technique: one digit left in a cell
    HiddenSingle.kt                  technique: one place left for a digit
    PointingPair.kt                  technique: digit confined to a line within a box
    BoxLineReduction.kt              technique: digit confined to a box within a line
    TechniqueSolver.kt               applies techniques in order, grades difficulty
    HintEngine.kt                    both hint styles, with fallback
core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/
    CandidateSetTest.kt  SolverStateTest.kt  SolverTest.kt
    TechniqueTestSupport.kt          builds a SolverState from explicit candidates
    NakedSingleTest.kt  HiddenSingleTest.kt  PointingPairTest.kt
    BoxLineReductionTest.kt  TechniqueSolverTest.kt  HintEngineTest.kt
    Puzzles.kt                       shared puzzle fixtures
```

Each file has one responsibility and stays small. `Coordinates` is pure lookup tables, `SolverState` is the only mutable thing in the codebase, and each technique is independently testable.

---

## Task 1: Gradle skeleton that builds and tests nothing

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`
- Create: `core/model/build.gradle.kts`, `core/solver/build.gradle.kts`
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Write `gradle/libs.versions.toml`**

```toml
[versions]
kotlin = "2.2.20"

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 2: Write `settings.gradle.kts`**

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "aisudoku"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

include(":core:model")
include(":core:solver")
```

The foojay plugin is what downloads JDK 21. `FAIL_ON_PROJECT_REPOS` stops modules declaring their own repositories, keeping resolution predictable.

- [ ] **Step 3: Write the root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
```

The root builds nothing. Applying `apply false` makes the plugin version available to subprojects without applying it here.

- [ ] **Step 4: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2g
org.gradle.caching=true
org.gradle.parallel=true
kotlin.code.style=official
```

- [ ] **Step 5: Write `core/model/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
```

- [ ] **Step 6: Write `core/solver/build.gradle.kts`**

Identical to the model module except it depends on it:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    api(project(":core:model"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
```

`api` rather than `implementation` because `Grid` appears in the solver's public signatures, so consumers need it on their compile classpath.

- [ ] **Step 7: Generate the Gradle wrapper**

Run, from the repository root, using the cached distribution:

```bash
"/c/Users/anton/.gradle/wrapper/dists/gradle-9.4.0-bin/lcvyxq3t37f6mx9miaydrrgs/gradle-9.4.0/bin/gradle" wrapper --gradle-version 9.4.0
```

Expected: `BUILD SUCCESSFUL`, and `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` now exist.

- [ ] **Step 8: Verify the build works**

```bash
./gradlew build --console=plain
```

Expected: `BUILD SUCCESSFUL`. The first run downloads JDK 21; allow several minutes. If it fails with "Cannot find a Java installation ... matching languageVersion=21", the foojay plugin is missing from `settings.gradle.kts` — fix that rather than installing a JDK.

- [ ] **Step 9: Write `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build --console=plain
```

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "Add Gradle skeleton for the pure-Kotlin core modules

Gradle 9.4.0 with a JDK 21 toolchain provisioned by the foojay resolver,
so no JDK needs installing by hand. Two empty modules, warnings as
errors, and CI on push."
```

---

## Task 2: Coordinates — row, column, box and peer lookup tables

A sudoku index is `0..80`, row-major. Every later component asks the same questions about indices, so they get answered once, in tables built at class-load time.

"Peers" of a cell are the 20 other cells that share its row, column or box. A digit placed in a cell can be eliminated from exactly its peers.

**Files:**
- Create: `core/model/src/main/kotlin/io/github/tonyxmelon/aisudoku/model/Coordinates.kt`
- Test: `core/model/src/test/kotlin/io/github/tonyxmelon/aisudoku/model/CoordinatesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordinatesTest {

    @Test
    fun `maps an index to its row column and box`() {
        assertEquals(0, Coordinates.rowOf(0))
        assertEquals(0, Coordinates.colOf(0))
        assertEquals(0, Coordinates.boxOf(0))

        assertEquals(4, Coordinates.rowOf(40))
        assertEquals(4, Coordinates.colOf(40))
        assertEquals(4, Coordinates.boxOf(40))

        assertEquals(8, Coordinates.rowOf(80))
        assertEquals(8, Coordinates.colOf(80))
        assertEquals(8, Coordinates.boxOf(80))
    }

    @Test
    fun `index round trips through row and column`() {
        for (i in 0 until 81) {
            assertEquals(i, Coordinates.indexOf(Coordinates.rowOf(i), Coordinates.colOf(i)))
        }
    }

    @Test
    fun `box 1 holds the top middle three by three block`() {
        assertEquals(listOf(3, 4, 5, 12, 13, 14, 21, 22, 23), Coordinates.boxIndices[1])
    }

    @Test
    fun `there are twenty seven units of nine cells each`() {
        assertEquals(27, Coordinates.units.size)
        assertTrue(Coordinates.units.all { it.size == 9 })
    }

    @Test
    fun `every cell has exactly twenty peers and is not its own peer`() {
        for (i in 0 until 81) {
            assertEquals(20, Coordinates.peers[i].size, "cell $i")
            assertTrue(i !in Coordinates.peers[i], "cell $i is its own peer")
        }
    }

    @Test
    fun `peers of the top left cell are its row column and box`() {
        val expected = (setOf(0, 1, 2, 3, 4, 5, 6, 7, 8) +   // row 0
            setOf(9, 18, 27, 36, 45, 54, 63, 72) +           // column 0
            setOf(10, 11, 19, 20)) - 0                       // rest of box 0
        assertEquals(expected, Coordinates.peers[0])
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:model:test --console=plain
```

Expected: FAIL — compilation error, `Unresolved reference: Coordinates`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.model

/**
 * Index arithmetic for a 9x9 grid stored row-major in `0..80`.
 *
 * Everything here is a precomputed table. These lookups sit in the innermost
 * loop of the solver, so they are built once rather than recomputed.
 */
object Coordinates {

    const val SIZE = 9
    const val CELL_COUNT = SIZE * SIZE

    fun rowOf(index: Int): Int = index / SIZE

    fun colOf(index: Int): Int = index % SIZE

    fun boxOf(index: Int): Int = (rowOf(index) / 3) * 3 + colOf(index) / 3

    fun indexOf(row: Int, col: Int): Int = row * SIZE + col

    /** `rowIndices[r]` holds the nine indices of row `r`, left to right. */
    val rowIndices: List<List<Int>> =
        (0 until SIZE).map { r -> (0 until SIZE).map { c -> indexOf(r, c) } }

    /** `colIndices[c]` holds the nine indices of column `c`, top to bottom. */
    val colIndices: List<List<Int>> =
        (0 until SIZE).map { c -> (0 until SIZE).map { r -> indexOf(r, c) } }

    /** `boxIndices[b]` holds the nine indices of box `b`, boxes numbered left to right, top to bottom. */
    val boxIndices: List<List<Int>> =
        (0 until SIZE).map { b ->
            val firstRow = (b / 3) * 3
            val firstCol = (b % 3) * 3
            (0 until 3).flatMap { dr -> (0 until 3).map { dc -> indexOf(firstRow + dr, firstCol + dc) } }
        }

    /** All 27 units: nine rows, then nine columns, then nine boxes. */
    val units: List<List<Int>> = rowIndices + colIndices + boxIndices

    /** `unitsOf[i]` holds the three units containing cell `i`: its row, column and box. */
    val unitsOf: List<List<List<Int>>> =
        (0 until CELL_COUNT).map { i ->
            listOf(rowIndices[rowOf(i)], colIndices[colOf(i)], boxIndices[boxOf(i)])
        }

    /** The 20 cells sharing a row, column or box with cell `i`, excluding `i` itself. */
    val peers: List<Set<Int>> =
        (0 until CELL_COUNT).map { i -> unitsOf[i].flatten().toSet() - i }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:model:test --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/model
git commit -m "Add grid coordinate and peer lookup tables"
```

---

## Task 3: Cell — a digit with a provenance

Per the spec, a cell is a given (printed), a guess (handwritten), or empty. The invariant worth enforcing is that emptiness and having no digit are the same thing — nothing downstream should ever have to handle "empty but holds a 4".

**Files:**
- Create: `core/model/src/main/kotlin/io/github/tonyxmelon/aisudoku/model/Cell.kt`
- Test: `core/model/src/test/kotlin/io/github/tonyxmelon/aisudoku/model/CellTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CellTest {

    @Test
    fun `a given carries its digit`() {
        val cell = Cell.given(7)
        assertEquals(7, cell.digit)
        assertEquals(CellSource.GIVEN, cell.source)
        assertTrue(cell.isFilled)
    }

    @Test
    fun `a guess carries its digit`() {
        val cell = Cell.guess(3)
        assertEquals(3, cell.digit)
        assertEquals(CellSource.GUESS, cell.source)
        assertTrue(cell.isFilled)
    }

    @Test
    fun `the empty cell has no digit`() {
        assertNull(Cell.Empty.digit)
        assertEquals(CellSource.EMPTY, Cell.Empty.source)
        assertFalse(Cell.Empty.isFilled)
    }

    @Test
    fun `digits outside one to nine are rejected`() {
        assertFailsWith<IllegalArgumentException> { Cell.given(0) }
        assertFailsWith<IllegalArgumentException> { Cell.given(10) }
        assertFailsWith<IllegalArgumentException> { Cell.guess(-1) }
    }

    @Test
    fun `a filled cell cannot claim to be empty and an empty cell cannot hold a digit`() {
        assertFailsWith<IllegalArgumentException> { Cell(4, CellSource.EMPTY) }
        assertFailsWith<IllegalArgumentException> { Cell(null, CellSource.GIVEN) }
        assertFailsWith<IllegalArgumentException> { Cell(null, CellSource.GUESS) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:model:test --console=plain
```

Expected: FAIL — `Unresolved reference: Cell`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.model

/** Where a digit in a cell came from. */
enum class CellSource {
    /** Printed in the puzzle. Defines the puzzle and is never wrong. */
    GIVEN,

    /** Written in by hand. May be wrong; this is what "check my answers" checks. */
    GUESS,

    /** No digit. */
    EMPTY,
}

/**
 * One cell of a grid.
 *
 * Invariant: [digit] is null exactly when [source] is [CellSource.EMPTY]. Constructing a
 * cell that breaks this throws, so no consumer has to handle the contradiction.
 */
data class Cell(val digit: Int?, val source: CellSource) {

    init {
        if (digit != null) {
            require(digit in 1..9) { "digit must be 1..9 but was $digit" }
            require(source != CellSource.EMPTY) { "a cell holding $digit cannot be EMPTY" }
        } else {
            require(source == CellSource.EMPTY) { "a cell with no digit must be EMPTY but was $source" }
        }
    }

    val isFilled: Boolean get() = digit != null

    companion object {
        val Empty: Cell = Cell(null, CellSource.EMPTY)

        fun given(digit: Int): Cell = Cell(digit, CellSource.GIVEN)

        fun guess(digit: Int): Cell = Cell(digit, CellSource.GUESS)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:model:test --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/model
git commit -m "Add Cell with a given, guess or empty provenance"
```

---

## Task 4: Grid — an immutable 81-cell board

Note `fromRows`, which takes nine nine-character strings. Every later test writes fixtures that way, because a single 81-character string is unreadable and impossible to review. That readability is worth the extra factory.

**Files:**
- Create: `core/model/src/main/kotlin/io/github/tonyxmelon/aisudoku/model/Grid.kt`
- Test: `core/model/src/test/kotlin/io/github/tonyxmelon/aisudoku/model/GridTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GridTest {

    @Test
    fun `the empty grid holds eighty one empty cells`() {
        assertEquals(81, Grid.Empty.cells.size)
        assertTrue(Grid.Empty.cells.all { it == Cell.Empty })
        assertEquals(0, Grid.Empty.filledCount)
        assertFalse(Grid.Empty.isComplete)
    }

    @Test
    fun `cells are addressable by index and by row and column`() {
        val grid = Grid.Empty.with(40, Cell.given(5))
        assertEquals(Cell.given(5), grid[40])
        assertEquals(Cell.given(5), grid[4, 4])
    }

    @Test
    fun `with returns a new grid and leaves the original alone`() {
        val original = Grid.Empty
        val updated = original.with(0, Cell.guess(1))
        assertEquals(Cell.Empty, original[0])
        assertEquals(Cell.guess(1), updated[0])
    }

    @Test
    fun `parses nine rows of givens using dot for empty`() {
        val grid = Grid.fromRows(
            "12345678.",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
        )
        assertEquals(Cell.given(1), grid[0])
        assertEquals(Cell.given(8), grid[7])
        assertEquals(Cell.Empty, grid[8])
        assertEquals(8, grid.givenCount)
    }

    @Test
    fun `rejects malformed input`() {
        assertFailsWith<IllegalArgumentException> { Grid.fromRows("12345678") }        // 8 rows
        assertFailsWith<IllegalArgumentException> { Grid.fromGivens("123") }           // too short
        assertFailsWith<IllegalArgumentException> { Grid.fromGivens("x".repeat(81)) }  // bad character
    }

    @Test
    fun `round trips through its string form`() {
        val text = "12345678." + ".".repeat(72)
        assertEquals(text, Grid.fromGivens(text).toGivensString())
    }

    @Test
    fun `givensOnly discards guesses`() {
        val grid = Grid.Empty
            .with(0, Cell.given(1))
            .with(1, Cell.guess(2))
        val givens = grid.givensOnly()
        assertEquals(Cell.given(1), givens[0])
        assertEquals(Cell.Empty, givens[1])
    }

    @Test
    fun `a grid with no repeats has no conflicts`() {
        val grid = Grid.fromRows(
            "12345678.",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
        )
        assertTrue(grid.conflicts().isEmpty())
        assertTrue(grid.isValid)
    }

    @Test
    fun `conflicts reports every cell involved in a repeat`() {
        val row = Grid.Empty.with(0, Cell.given(5)).with(1, Cell.given(5))
        assertEquals(setOf(0, 1), row.conflicts())
        assertFalse(row.isValid)

        val column = Grid.Empty.with(0, Cell.given(5)).with(9, Cell.guess(5))
        assertEquals(setOf(0, 9), column.conflicts())

        val box = Grid.Empty.with(0, Cell.given(5)).with(10, Cell.given(5))
        assertEquals(setOf(0, 10), box.conflicts())
    }

    @Test
    fun `a full grid is complete`() {
        val full = Grid.of(List(81) { Cell.given((it % 9) + 1) })
        assertEquals(81, full.filledCount)
        assertTrue(full.isComplete)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:model:test --console=plain
```

Expected: FAIL — `Unresolved reference: Grid`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.model

import io.github.tonyxmelon.aisudoku.model.Coordinates.CELL_COUNT

/**
 * An immutable 9x9 grid. Every mutation returns a new grid.
 *
 * This is the boundary type between recognition, solving and the UI, so it stays
 * dumb on purpose: it knows how to describe itself and how to spot a rule violation,
 * and nothing else.
 */
class Grid private constructor(val cells: List<Cell>) {

    init {
        require(cells.size == CELL_COUNT) { "a grid needs $CELL_COUNT cells but got ${cells.size}" }
    }

    operator fun get(index: Int): Cell = cells[index]

    operator fun get(row: Int, col: Int): Cell = cells[Coordinates.indexOf(row, col)]

    fun with(index: Int, cell: Cell): Grid = Grid(cells.toMutableList().also { it[index] = cell })

    val filledCount: Int get() = cells.count { it.isFilled }

    val givenCount: Int get() = cells.count { it.source == CellSource.GIVEN }

    val isComplete: Boolean get() = filledCount == CELL_COUNT

    /** The puzzle as printed: guesses removed, givens kept. */
    fun givensOnly(): Grid =
        Grid(cells.map { if (it.source == CellSource.GIVEN) it else Cell.Empty })

    /**
     * Indices of every cell that shares a digit with another cell in the same row,
     * column or box. Empty when the grid breaks no rules.
     */
    fun conflicts(): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (unit in Coordinates.units) {
            val byDigit = unit.filter { cells[it].isFilled }.groupBy { cells[it].digit }
            for ((_, indices) in byDigit) {
                if (indices.size > 1) bad += indices
            }
        }
        return bad
    }

    val isValid: Boolean get() = conflicts().isEmpty()

    /** 81 characters, `.` for empty. Filled cells lose their provenance. */
    fun toGivensString(): String =
        cells.joinToString("") { it.digit?.toString() ?: "." }

    override fun equals(other: Any?): Boolean = other is Grid && other.cells == cells

    override fun hashCode(): Int = cells.hashCode()

    override fun toString(): String =
        (0 until 9).joinToString("\n") { r ->
            (0 until 9).joinToString("") { c -> this[r, c].digit?.toString() ?: "." }
        }

    companion object {
        val Empty: Grid = Grid(List(CELL_COUNT) { Cell.Empty })

        fun of(cells: List<Cell>): Grid = Grid(cells)

        /**
         * Parses 81 characters, `.` or `0` for an empty cell. Every digit becomes a GIVEN.
         * Whitespace is ignored so callers may format for readability.
         */
        fun fromGivens(text: String): Grid {
            val cleaned = text.filterNot { it.isWhitespace() }
            require(cleaned.length == CELL_COUNT) {
                "expected $CELL_COUNT characters but got ${cleaned.length}"
            }
            return Grid(cleaned.map { ch ->
                when (ch) {
                    '.', '0' -> Cell.Empty
                    in '1'..'9' -> Cell.given(ch - '0')
                    else -> throw IllegalArgumentException("unexpected character '$ch'")
                }
            })
        }

        /** Nine rows of nine characters. Far easier to read and review than one long string. */
        fun fromRows(vararg rows: String): Grid {
            require(rows.size == 9) { "expected 9 rows but got ${rows.size}" }
            rows.forEachIndexed { i, row ->
                require(row.length == 9) { "row $i has ${row.length} characters, expected 9" }
            }
            return fromGivens(rows.joinToString(""))
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:model:test --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/model
git commit -m "Add immutable Grid with parsing and conflict detection"
```

---

## Task 5: CandidateSet — the digits still possible in a cell

A set of digits `1..9` held in the low nine bits of an `Int`. The solver touches this millions of times in a hard search, so it is a value class and never allocates.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/CandidateSet.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/CandidateSetTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CandidateSetTest {

    @Test
    fun `all holds every digit and none holds nothing`() {
        assertEquals(9, CandidateSet.ALL.size)
        assertEquals((1..9).toList(), CandidateSet.ALL.digits())
        assertEquals(0, CandidateSet.NONE.size)
        assertTrue(CandidateSet.NONE.isEmpty)
    }

    @Test
    fun `membership follows addition and removal`() {
        val set = CandidateSet.NONE.plus(3).plus(7)
        assertTrue(3 in set)
        assertTrue(7 in set)
        assertFalse(4 in set)
        assertEquals(listOf(3, 7), set.digits())

        val fewer = set.minus(3)
        assertFalse(3 in fewer)
        assertTrue(7 in fewer)
    }

    @Test
    fun `removing a digit that is absent changes nothing`() {
        val set = CandidateSet.NONE.plus(5)
        assertEquals(set, set.minus(2))
    }

    @Test
    fun `single returns the only digit or null`() {
        assertEquals(6, CandidateSet.NONE.plus(6).single)
        assertNull(CandidateSet.NONE.plus(6).plus(2).single)
        assertNull(CandidateSet.NONE.single)
    }

    @Test
    fun `of builds a set from digits`() {
        assertEquals(listOf(2, 4, 9), CandidateSet.of(9, 2, 4).digits())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --console=plain
```

Expected: FAIL — `Unresolved reference: CandidateSet`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

/**
 * The digits `1..9` still possible in a cell, held as bits 0..8 of an `Int`.
 *
 * A value class, so it costs exactly one `Int` at runtime and allocates nothing.
 * The solver reads and rebuilds these constantly during search.
 */
@JvmInline
value class CandidateSet(val bits: Int) {

    operator fun contains(digit: Int): Boolean = bits and maskOf(digit) != 0

    fun plus(digit: Int): CandidateSet = CandidateSet(bits or maskOf(digit))

    fun minus(digit: Int): CandidateSet = CandidateSet(bits and maskOf(digit).inv())

    val size: Int get() = bits.countOneBits()

    val isEmpty: Boolean get() = bits == 0

    /** The only remaining digit, or null when there are none or more than one. */
    val single: Int? get() = if (size == 1) bits.countTrailingZeroBits() + 1 else null

    fun digits(): List<Int> = (1..9).filter { it in this }

    override fun toString(): String = digits().joinToString(prefix = "{", postfix = "}")

    companion object {
        val ALL: CandidateSet = CandidateSet(0b1_1111_1111)
        val NONE: CandidateSet = CandidateSet(0)

        fun of(vararg digits: Int): CandidateSet =
            digits.fold(NONE) { acc, d -> acc.plus(d) }

        private fun maskOf(digit: Int): Int {
            require(digit in 1..9) { "digit must be 1..9 but was $digit" }
            return 1 shl (digit - 1)
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:solver:test --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/solver
git commit -m "Add CandidateSet bitmask over digits one to nine"
```

---

## Task 6: SolverState — candidates plus constraint propagation

The heart of the engine, and the one mutable type in the codebase.

The algorithm is Peter Norvig's. Rather than storing values and candidates separately, a cell's *value* is simply a candidate set that has been reduced to one digit. Two operations drive everything:

- **assign(cell, digit)** — eliminate every *other* digit from that cell.
- **eliminate(cell, digit)** — remove one digit, then propagate two consequences:
  1. if the cell is now down to one digit, eliminate that digit from all its peers;
  2. for each unit containing the cell, if the eliminated digit now has only one possible place left in that unit, assign it there.

Both return `false` on contradiction, which is how the search backtracks.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/SolverState.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/SolverStateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SolverStateTest {

    @Test
    fun `an empty grid starts with every digit possible everywhere`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        for (i in 0 until 81) {
            assertEquals(CandidateSet.ALL, state.candidatesAt(i))
        }
        assertEquals(0, state.solvedCount)
    }

    @Test
    fun `assigning a digit removes it from every peer`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        assertTrue(state.assign(0, 5))

        assertEquals(5, state.valueAt(0))
        assertFalse(5 in state.candidatesAt(1))   // same row
        assertFalse(5 in state.candidatesAt(9))   // same column
        assertFalse(5 in state.candidatesAt(10))  // same box
        assertTrue(5 in state.candidatesAt(80))   // unrelated
    }

    @Test
    fun `a cell reduced to one digit propagates to its own peers`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        // Leave only 9 possible at index 8 by filling the rest of row 0.
        for (d in 1..8) assertTrue(state.assign(d - 1, d))

        assertEquals(9, state.valueAt(8))
        assertFalse(9 in state.candidatesAt(17))  // 17 shares box 2 with cell 8
    }

    @Test
    fun `a digit with one place left in a unit is assigned there`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        // Remove 5 from every cell of row 0 except index 4.
        for (i in Coordinates0.row0 - 4) assertTrue(state.eliminate(i, 5))
        assertEquals(5, state.valueAt(4))
    }

    @Test
    fun `eliminating the last candidate is a contradiction`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        for (d in 1..8) assertTrue(state.eliminate(0, d))
        assertFalse(state.eliminate(0, 9))
    }

    @Test
    fun `a grid that breaks the rules cannot start a state`() {
        val broken = Grid.Empty.with(0, Cell.given(5)).with(1, Cell.given(5))
        assertNull(SolverState.from(broken))
    }

    @Test
    fun `guesses are ignored so only givens constrain the state`() {
        val grid = Grid.Empty.with(0, Cell.given(5)).with(40, Cell.guess(7))
        val state = assertNotNull(SolverState.from(grid))
        assertEquals(5, state.valueAt(0))
        assertNull(state.valueAt(40))
    }

    @Test
    fun `a copy does not share mutation with its source`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        val copy = state.copy()
        assertTrue(copy.assign(0, 5))
        assertEquals(5, copy.valueAt(0))
        assertNull(state.valueAt(0))
    }

    @Test
    fun `toGrid reports solved cells as guesses`() {
        val state = assertNotNull(SolverState.from(Grid.Empty))
        assertTrue(state.assign(0, 5))
        val grid = state.toGrid()
        assertEquals(Cell.guess(5), grid[0])
        assertEquals(Cell.Empty, grid[1])
    }
}

/** Small helper so the test above reads clearly. */
private object Coordinates0 {
    val row0 = (0..8).toList()
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --console=plain
```

Expected: FAIL — `Unresolved reference: SolverState`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/**
 * A working grid of candidate sets, with constraint propagation.
 *
 * A cell is "solved" when its candidate set is down to a single digit, so there is no
 * separate value array to keep in step. Every mutating call returns `false` if it drove
 * the grid into a contradiction, at which point the state is spent and the caller must
 * fall back to a copy taken earlier. This is how [Solver] backtracks.
 */
class SolverState private constructor(private val candidates: IntArray) {

    fun candidatesAt(index: Int): CandidateSet = CandidateSet(candidates[index])

    /** The digit at [index], or null while more than one remains possible. */
    fun valueAt(index: Int): Int? = CandidateSet(candidates[index]).single

    val solvedCount: Int get() = candidates.count { CandidateSet(it).size == 1 }

    val isSolved: Boolean get() = candidates.all { CandidateSet(it).size == 1 }

    fun copy(): SolverState = SolverState(candidates.copyOf())

    /** Fix [digit] in [index] by eliminating every other digit there. */
    fun assign(index: Int, digit: Int): Boolean {
        val others = CandidateSet(candidates[index]).minus(digit)
        for (other in others.digits()) {
            if (!eliminate(index, other)) return false
        }
        return true
    }

    /** Remove [digit] from [index] and propagate. Returns false on contradiction. */
    fun eliminate(index: Int, digit: Int): Boolean {
        val current = CandidateSet(candidates[index])
        if (digit !in current) return true  // already gone, nothing to propagate

        val reduced = current.minus(digit)
        candidates[index] = reduced.bits

        if (reduced.isEmpty) return false

        // (1) The cell is down to one digit, so no peer may hold it.
        reduced.single?.let { only ->
            for (peer in Coordinates.peers[index]) {
                if (!eliminate(peer, only)) return false
            }
        }

        // (2) The eliminated digit may now have only one home left in a unit.
        for (unit in Coordinates.unitsOf[index]) {
            val places = unit.filter { digit in CandidateSet(candidates[it]) }
            when (places.size) {
                0 -> return false
                1 -> if (!assign(places[0], digit)) return false
            }
        }
        return true
    }

    /** Solved cells become guesses; unsolved cells stay empty. */
    fun toGrid(): Grid = Grid.of(
        (0 until Coordinates.CELL_COUNT).map { i ->
            valueAt(i)?.let { Cell.guess(it) } ?: Cell.Empty
        }
    )

    companion object {
        /**
         * Builds a state from the GIVENS of [grid]. Guesses are deliberately ignored —
         * the puzzle is defined by what was printed, and the whole point of solving is
         * to judge the guesses against it.
         *
         * Returns null when the givens already break the rules.
         */
        fun from(grid: Grid): SolverState? {
            val state = SolverState(IntArray(Coordinates.CELL_COUNT) { CandidateSet.ALL.bits })
            for (i in 0 until Coordinates.CELL_COUNT) {
                val cell = grid[i]
                if (cell.source == CellSource.GIVEN) {
                    val digit = cell.digit ?: continue
                    if (!state.assign(i, digit)) return null
                }
            }
            return state
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:solver:test --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/solver
git commit -m "Add SolverState with Norvig-style constraint propagation"
```

---

## Task 7: Solver — search, and proof of uniqueness

Uniqueness is what makes the recognizer checkable, so the solver must distinguish "no solution", "exactly one" and "more than one" rather than just returning an answer. `Multiple` carries both solutions it found, because the cells where they differ are precisely the ambiguous ones — the recognition repair path in a later plan uses that.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/Solver.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/Puzzles.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/SolverTest.kt`

- [ ] **Step 1: Write the shared puzzle fixtures**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Grid

/** Puzzles shared across solver and technique tests. */
object Puzzles {

    /** Solvable by naked and hidden singles alone. */
    val EASY: Grid = Grid.fromRows(
        "53..7....",
        "6..195...",
        ".98....6.",
        "8...6...3",
        "4..8.3..1",
        "7...2...6",
        ".6....28.",
        "...419..5",
        "....8..79",
    )

    /** Arto Inkala's 2012 puzzle, widely used as a worst case for backtracking. */
    val HARDEST: Grid = Grid.fromRows(
        "8........",
        "..36.....",
        ".7..9.2..",
        ".5...7...",
        "....457..",
        "...1...3.",
        "..1....68",
        "..85...1.",
        ".9....4..",
    )

    /**
     * EASY with the two givens of row 0 removed, leaving 28. Verified to have more than
     * one solution, so the solver must report ambiguity rather than pick a favourite.
     */
    val AMBIGUOUS: Grid = Grid.fromRows(
        "....7....",
        "6..195...",
        ".98....6.",
        "8...6...3",
        "4..8.3..1",
        "7...2...6",
        ".6....28.",
        "...419..5",
        "....8..79",
    )

    /** Two fives in the top row. */
    val CONTRADICTORY: Grid = Grid.fromRows(
        "55.......",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
    )
}
```

- [ ] **Step 2: Write the failing test**

Note the shape of `assertSolves`. Rather than hard-coding a solution string — which is unreviewable and easy to get wrong — it checks the three properties a correct solution must have: it is complete, it breaks no rules, and it agrees with every given. That catches a wrong answer just as well and cannot itself be wrong.

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SolverTest {

    /** A solution must be full, legal, and consistent with the puzzle it came from. */
    private fun assertSolves(puzzle: Grid, solution: Grid) {
        assertTrue(solution.isComplete, "solution has empty cells:\n$solution")
        assertTrue(solution.isValid, "solution breaks the rules:\n$solution")
        for (i in 0 until 81) {
            val given = puzzle[i]
            if (given.source == CellSource.GIVEN) {
                assertEquals(given.digit, solution[i].digit, "cell $i disagrees with its given")
            }
        }
    }

    @Test
    fun `solves an easy puzzle uniquely`() {
        val result = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY))
        assertSolves(Puzzles.EASY, result.solution)
    }

    @Test
    fun `solves the hardest known puzzle uniquely`() {
        val result = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.HARDEST))
        assertSolves(Puzzles.HARDEST, result.solution)
    }

    @Test
    fun `reports no solution when the givens contradict`() {
        assertIs<SolveResult.None>(Solver.solve(Puzzles.CONTRADICTORY))
    }

    @Test
    fun `reports multiple solutions and they genuinely differ`() {
        val result = assertIs<SolveResult.Multiple>(Solver.solve(Puzzles.AMBIGUOUS))
        assertSolves(Puzzles.AMBIGUOUS, result.first)
        assertSolves(Puzzles.AMBIGUOUS, result.second)
        assertTrue(result.first != result.second)
        assertTrue(result.ambiguousCells.isNotEmpty())
        assertTrue(result.ambiguousCells.all { result.first[it].digit != result.second[it].digit })
    }

    @Test
    fun `an empty grid has many solutions`() {
        assertIs<SolveResult.Multiple>(Solver.solve(Grid.Empty))
    }

    @Test
    fun `a completed grid solves to itself`() {
        val solved = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        val asGivens = Grid.of(solved.cells.map { Cell.given(it.digit!!) })
        val again = assertIs<SolveResult.Unique>(Solver.solve(asGivens))
        assertEquals(asGivens.toGivensString(), again.solution.toGivensString())
    }

    @Test
    fun `guesses do not influence the solution`() {
        // Write a deliberately wrong guess into an empty cell; the answer must not change.
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        val wrongDigit = (1..9).first { it != truth[2].digit }
        val withGuess = Puzzles.EASY.with(2, Cell.guess(wrongDigit))
        val result = assertIs<SolveResult.Unique>(Solver.solve(withGuess))
        assertEquals(truth.toGivensString(), result.solution.toGivensString())
    }

    @Test
    fun `every puzzle produced by removing cells from a solution is still solvable`() {
        val random = Random(seed = 20260830)
        val full = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        repeat(20) {
            val keep = (0 until 81).shuffled(random).take(40).toSet()
            val puzzle = Grid.of(full.cells.mapIndexed { i, c ->
                if (i in keep) Cell.given(c.digit!!) else Cell.Empty
            })
            val result = Solver.solve(puzzle)
            assertTrue(
                result is SolveResult.Unique || result is SolveResult.Multiple,
                "a puzzle carved from a real solution must be solvable, got $result",
            )
        }
    }

    @Test
    fun `the hardest puzzle solves well inside the interactive budget`() {
        val start = System.nanoTime()
        Solver.solve(Puzzles.HARDEST)
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue(millis < 1000, "took ${millis}ms, which is too slow to sit behind a button")
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --console=plain
```

Expected: FAIL — `Unresolved reference: Solver`.

- [ ] **Step 4: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/** What the solver made of a puzzle. */
sealed interface SolveResult {

    /** Exactly one solution. A properly set puzzle always lands here. */
    data class Unique(val solution: Grid) : SolveResult

    /** The givens contradict, or no completion exists. */
    data object None : SolveResult

    /**
     * At least two solutions. Both are carried because the cells where they disagree
     * are exactly the under-determined ones, which is useful diagnostic information.
     */
    data class Multiple(val first: Grid, val second: Grid) : SolveResult {
        val ambiguousCells: Set<Int>
            get() = (0 until Coordinates.CELL_COUNT)
                .filter { first[it].digit != second[it].digit }
                .toSet()
    }
}

/**
 * Solves from the GIVENS of a grid, ignoring guesses.
 *
 * Constraint propagation does most of the work; search only picks up what propagation
 * cannot settle. The search always branches on the cell with the fewest candidates
 * remaining, which keeps the tree narrow — without it, hard puzzles take minutes.
 *
 * Search stops after the second solution, since "more than one" is all any caller needs.
 */
object Solver {

    fun solve(grid: Grid): SolveResult {
        val state = SolverState.from(grid) ?: return SolveResult.None
        val found = mutableListOf<Grid>()
        search(state, found)
        return when (found.size) {
            0 -> SolveResult.None
            1 -> SolveResult.Unique(found[0])
            else -> SolveResult.Multiple(found[0], found[1])
        }
    }

    /** True when the puzzle has exactly one solution. */
    fun hasUniqueSolution(grid: Grid): Boolean = solve(grid) is SolveResult.Unique

    /** Depth-first search. Returns true once enough solutions have been collected to stop. */
    private fun search(state: SolverState, found: MutableList<Grid>): Boolean {
        if (state.isSolved) {
            found += state.toGrid()
            return found.size >= 2
        }

        val branchCell = mostConstrainedCell(state) ?: return false

        for (digit in state.candidatesAt(branchCell).digits()) {
            val attempt = state.copy()
            if (attempt.assign(branchCell, digit)) {
                if (search(attempt, found)) return true
            }
        }
        return false
    }

    /** The unsolved cell with the fewest candidates, or null when none are left. */
    private fun mostConstrainedCell(state: SolverState): Int? {
        var best: Int? = null
        var bestSize = 10
        for (i in 0 until Coordinates.CELL_COUNT) {
            val size = state.candidatesAt(i).size
            if (size in 2 until bestSize) {
                best = i
                bestSize = size
                if (size == 2) break  // cannot do better among unsolved cells
            }
        }
        return best
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :core:solver:test --console=plain
```

Expected: `BUILD SUCCESSFUL`. If `solves the hardest known puzzle` hangs, the MRV heuristic in `mostConstrainedCell` is wrong — a plain left-to-right scan will not finish in reasonable time.

- [ ] **Step 6: Commit**

```bash
git add core/solver
git commit -m "Add backtracking solver reporting none, unique or multiple solutions"
```

---

## Task 8: Deduction and Technique — the vocabulary of an explanation

Two kinds of thing a human solver concludes: a digit definitely goes in a cell, or a digit definitely does not. Both need to name the cells that prove them, because the UI highlights exactly those.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/Deduction.kt`
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/Technique.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/TechniqueTestSupport.kt`

- [ ] **Step 1: Write `Deduction.kt`**

There is no test for these types on their own — they are pure data with no behaviour, and the technique tests in Tasks 9 to 12 exercise them thoroughly. Adding a test that a data class stores its arguments tests the Kotlin compiler, not this code.

```kotlin
package io.github.tonyxmelon.aisudoku.solver

/** Ranks techniques from easiest to hardest. Also grades puzzles: see [TechniqueSolver]. */
enum class Difficulty { EASY, MEDIUM, HARD, VERY_HARD }

/**
 * One step of human reasoning.
 *
 * [supportingCells] are the cells a person would point at to justify the step. The
 * overlay highlights them, so they must be the actual evidence and not merely related.
 */
sealed interface Deduction {
    val technique: String
    val difficulty: Difficulty
    val explanation: String
    val supportingCells: Set<Int>

    /** A digit belongs in a cell. */
    data class Placement(
        override val technique: String,
        override val difficulty: Difficulty,
        override val explanation: String,
        override val supportingCells: Set<Int>,
        val index: Int,
        val digit: Int,
    ) : Deduction

    /** A digit can be ruled out of some cells, without placing anything yet. */
    data class Elimination(
        override val technique: String,
        override val difficulty: Difficulty,
        override val explanation: String,
        override val supportingCells: Set<Int>,
        val digit: Int,
        val fromCells: Set<Int>,
    ) : Deduction {
        init { require(fromCells.isNotEmpty()) { "an elimination that removes nothing is not a deduction" } }
    }
}
```

- [ ] **Step 2: Write `Technique.kt`**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

/**
 * One human solving method.
 *
 * Implementations must be pure: given a state, return the first deduction they can find,
 * or null. They never mutate the state — [TechniqueSolver] decides whether to apply what
 * they find, which is what lets the hint engine describe a step without taking it.
 */
interface Technique {
    val name: String
    val difficulty: Difficulty
    fun find(state: SolverState): Deduction?
}

/**
 * Every technique, easiest first. Order matters: a hint should offer the simplest
 * reasoning that works, not the cleverest.
 */
val ALL_TECHNIQUES: List<Technique> = listOf(
    NakedSingle,
    HiddenSingle,
    PointingPair,
    BoxLineReduction,
)
```

- [ ] **Step 3: Write the technique test support**

Techniques are tested against states built from explicit candidate sets rather than from puzzles. Constructing a real puzzle that isolates one technique is difficult and the resulting fixture is unreadable — you cannot tell by looking whether it tests what it claims. Stating the candidates directly makes each test exact and obvious.

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.assertNotNull

/**
 * Builds a [SolverState] with hand-specified candidates, bypassing propagation.
 *
 * Every cell starts with all nine digits. Each pair overrides one cell. This produces
 * states that could not arise from a real puzzle, which is exactly the point: a
 * technique must be judged on the candidate pattern it claims to recognise, in isolation.
 */
fun stateWithCandidates(vararg overrides: Pair<Int, CandidateSet>): SolverState {
    val state = assertNotNull(SolverState.from(Grid.Empty))
    for ((index, candidates) in overrides) {
        for (digit in 1..9) {
            if (digit !in candidates) state.forceEliminate(index, digit)
        }
    }
    return state
}
```

- [ ] **Step 4: Add the test-only elimination hook to `SolverState`**

`eliminate` propagates, which would cascade and undo the isolation the helper is trying to create. Add a non-propagating setter beside it, in `SolverState.kt`:

```kotlin
    /**
     * Removes a candidate without propagating.
     *
     * Only for building test fixtures. Production code must use [eliminate] so the
     * consequences of a removal are followed through.
     */
    internal fun forceEliminate(index: Int, digit: Int) {
        candidates[index] = CandidateSet(candidates[index]).minus(digit).bits
    }
```

- [ ] **Step 5: Verify it compiles**

`ALL_TECHNIQUES` refers to four objects that do not exist yet, so add a temporary empty list to get a clean compile, and restore the real list in Task 12. Replace the body with:

```kotlin
val ALL_TECHNIQUES: List<Technique> = emptyList()
```

Run:

```bash
./gradlew :core:solver:build --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add core/solver
git commit -m "Add Deduction and Technique vocabulary for explained hints"
```

---

## Task 9: NakedSingle — one digit left in a cell

The simplest deduction there is: a cell with exactly one remaining candidate must hold it.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/NakedSingle.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/NakedSingleTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NakedSingleTest {

    @Test
    fun `finds the cell with a single remaining candidate`() {
        val state = stateWithCandidates(
            40 to CandidateSet.of(7),
            41 to CandidateSet.of(2, 5),
        )
        val deduction = assertIs<Deduction.Placement>(NakedSingle.find(state))
        assertEquals(40, deduction.index)
        assertEquals(7, deduction.digit)
        assertEquals(Difficulty.EASY, deduction.difficulty)
    }

    @Test
    fun `points at the cell itself as the evidence`() {
        val state = stateWithCandidates(40 to CandidateSet.of(7))
        val deduction = assertIs<Deduction.Placement>(NakedSingle.find(state))
        assertEquals(setOf(40), deduction.supportingCells)
    }

    @Test
    fun `explains itself in terms a person would use`() {
        val state = stateWithCandidates(40 to CandidateSet.of(7))
        val deduction = assertIs<Deduction.Placement>(NakedSingle.find(state))
        assertTrue(deduction.explanation.contains("7"), deduction.explanation)
        assertTrue(deduction.explanation.isNotBlank())
    }

    @Test
    fun `finds nothing when every cell has options`() {
        val state = stateWithCandidates(
            0 to CandidateSet.of(1, 2),
            1 to CandidateSet.of(3, 4),
        )
        assertNull(NakedSingle.find(state))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --tests '*NakedSingleTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: NakedSingle`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * A cell with one candidate left must hold it.
 *
 * [SolverState] already treats a one-candidate cell as solved, so this technique reports
 * cells that propagation has settled but the user has not yet been told about.
 */
object NakedSingle : Technique {

    override val name = "Naked single"
    override val difficulty = Difficulty.EASY

    override fun find(state: SolverState): Deduction? {
        for (index in 0 until Coordinates.CELL_COUNT) {
            val digit = state.candidatesAt(index).single ?: continue
            if (state.isReported(index)) continue
            return Deduction.Placement(
                technique = name,
                difficulty = difficulty,
                explanation = "Only $digit can go here — every other digit already appears " +
                    "in this row, column or box.",
                supportingCells = setOf(index),
                index = index,
                digit = digit,
            )
        }
        return null
    }
}
```

- [ ] **Step 4: Add the reporting flag to `SolverState`**

Without this, `NakedSingle` re-reports the puzzle's givens forever, because a given is also a one-candidate cell. Add to `SolverState.kt`, alongside the candidates array:

```kotlin
    private val reported = BooleanArray(Coordinates.CELL_COUNT)

    /** True when this cell was solved from the start, or has already been offered as a hint. */
    fun isReported(index: Int): Boolean = reported[index]

    fun markReported(index: Int) { reported[index] = true }
```

Include it in `copy()`:

```kotlin
    fun copy(): SolverState = SolverState(candidates.copyOf()).also {
        reported.copyInto(it.reported)
    }
```

And mark the givens as reported at the end of `from`, just before `return state`:

```kotlin
            for (i in 0 until Coordinates.CELL_COUNT) {
                if (state.valueAt(i) != null) state.markReported(i)
            }
            return state
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :core:solver:test --tests '*NakedSingleTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add core/solver
git commit -m "Add naked single technique"
```

---

## Task 10: HiddenSingle — one place left for a digit

A digit that can only go in one cell of a row, column or box goes there — even when that cell still has other candidates of its own. This is the technique people find hardest to spot and so the most valuable to explain.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/HiddenSingle.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/HiddenSingleTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HiddenSingleTest {

    /**
     * Row 0: only cell 4 can still be a 5, though cell 4 could also be a 6.
     * A naked single would miss this; the cell is not down to one candidate.
     */
    private fun rowWithOnePlaceForFive(): SolverState = stateWithCandidates(
        0 to CandidateSet.of(1, 2),
        1 to CandidateSet.of(1, 2),
        2 to CandidateSet.of(3, 4),
        3 to CandidateSet.of(3, 4),
        4 to CandidateSet.of(5, 6),
        5 to CandidateSet.of(6, 7),
        6 to CandidateSet.of(7, 8),
        7 to CandidateSet.of(8, 9),
        8 to CandidateSet.of(9, 1),
    )

    @Test
    fun `finds the only cell in a unit that can hold a digit`() {
        val deduction = assertIs<Deduction.Placement>(HiddenSingle.find(rowWithOnePlaceForFive()))
        assertEquals(4, deduction.index)
        assertEquals(5, deduction.digit)
        assertEquals(Difficulty.MEDIUM, deduction.difficulty)
    }

    @Test
    fun `offers the whole unit as evidence`() {
        val deduction = assertIs<Deduction.Placement>(HiddenSingle.find(rowWithOnePlaceForFive()))
        assertEquals((0..8).toSet(), deduction.supportingCells)
    }

    @Test
    fun `names the unit it reasoned about`() {
        val deduction = assertIs<Deduction.Placement>(HiddenSingle.find(rowWithOnePlaceForFive()))
        assertTrue(deduction.explanation.contains("row"), deduction.explanation)
        assertTrue(deduction.explanation.contains("5"), deduction.explanation)
    }

    @Test
    fun `finds nothing when every digit has several homes`() {
        // Two cells, each able to hold either digit, so neither digit is pinned.
        val state = stateWithCandidates(
            *(0..8).map { it to CandidateSet.ALL }.toTypedArray()
        )
        assertNull(HiddenSingle.find(state))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --tests '*HiddenSingleTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: HiddenSingle`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * A digit with only one possible home in a unit belongs there, whatever else that cell
 * might also have accepted.
 */
object HiddenSingle : Technique {

    override val name = "Hidden single"
    override val difficulty = Difficulty.MEDIUM

    override fun find(state: SolverState): Deduction? {
        for ((unitIndex, unit) in Coordinates.units.withIndex()) {
            for (digit in 1..9) {
                if (unit.any { state.valueAt(it) == digit }) continue  // already placed here

                val places = unit.filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
                if (places.size != 1) continue

                val index = places[0]
                if (state.isReported(index)) continue

                return Deduction.Placement(
                    technique = name,
                    difficulty = difficulty,
                    explanation = "$digit has to go here: it is the only cell in this " +
                        "${unitName(unitIndex)} that can still take a $digit.",
                    supportingCells = unit.toSet(),
                    index = index,
                    digit = digit,
                )
            }
        }
        return null
    }

    /** Units are stored as nine rows, then nine columns, then nine boxes. */
    private fun unitName(unitIndex: Int): String = when {
        unitIndex < 9 -> "row"
        unitIndex < 18 -> "column"
        else -> "box"
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:solver:test --tests '*HiddenSingleTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/solver
git commit -m "Add hidden single technique"
```

---

## Task 11: PointingPair — a digit confined to one line within a box

When every possible home for a digit inside a box falls in the same row, that digit must be somewhere in that row *inside the box*, so it can be struck out of the rest of the row. Same for columns. This eliminates rather than places.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/PointingPair.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/PointingPairTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PointingPairTest {

    /**
     * In box 0, digit 3 can only sit at cells 0 and 1 — both in row 0.
     * So 3 belongs somewhere in row 0 inside box 0, and can go from cells 3..8.
     */
    private fun threeConfinedToRowZeroOfBoxZero(): SolverState {
        val boxCellsWithoutThree = listOf(2, 9, 10, 11, 18, 19, 20)
        return stateWithCandidates(
            0 to CandidateSet.of(3, 4),
            1 to CandidateSet.of(3, 5),
            *boxCellsWithoutThree.map { it to CandidateSet.of(1, 2) }.toTypedArray(),
        )
    }

    @Test
    fun `eliminates the digit from the rest of the line`() {
        val deduction = assertIs<Deduction.Elimination>(PointingPair.find(threeConfinedToRowZeroOfBoxZero()))
        assertEquals(3, deduction.digit)
        assertEquals(setOf(3, 4, 5, 6, 7, 8), deduction.fromCells)
        assertEquals(Difficulty.HARD, deduction.difficulty)
    }

    @Test
    fun `points at the confined cells as evidence`() {
        val deduction = assertIs<Deduction.Elimination>(PointingPair.find(threeConfinedToRowZeroOfBoxZero()))
        assertEquals(setOf(0, 1), deduction.supportingCells)
    }

    @Test
    fun `explains the confinement`() {
        val deduction = assertIs<Deduction.Elimination>(PointingPair.find(threeConfinedToRowZeroOfBoxZero()))
        assertTrue(deduction.explanation.contains("3"), deduction.explanation)
        assertTrue(deduction.explanation.contains("box"), deduction.explanation)
    }

    @Test
    fun `finds nothing when the digit is spread across rows of the box`() {
        val state = stateWithCandidates(
            0 to CandidateSet.of(3, 4),
            10 to CandidateSet.of(3, 5),   // row 1, so not confined to one row
            *listOf(1, 2, 9, 11, 18, 19, 20)
                .map { it to CandidateSet.of(1, 2) }.toTypedArray(),
        )
        assertNull(PointingPair.find(state))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --tests '*PointingPairTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: PointingPair`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * If every home for a digit inside a box shares one row or column, the digit lies on
 * that line within the box, so it can be eliminated from the rest of the line.
 */
object PointingPair : Technique {

    override val name = "Pointing pair"
    override val difficulty = Difficulty.HARD

    override fun find(state: SolverState): Deduction? {
        for (box in 0 until 9) {
            val boxCells = Coordinates.boxIndices[box]
            for (digit in 1..9) {
                if (boxCells.any { state.valueAt(it) == digit }) continue

                val places = boxCells.filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
                if (places.size !in 2..3) continue

                val row = Coordinates.rowOf(places[0])
                if (places.all { Coordinates.rowOf(it) == row }) {
                    eliminationAlong(state, places, digit, Coordinates.rowIndices[row], "row")
                        ?.let { return it }
                }

                val col = Coordinates.colOf(places[0])
                if (places.all { Coordinates.colOf(it) == col }) {
                    eliminationAlong(state, places, digit, Coordinates.colIndices[col], "column")
                        ?.let { return it }
                }
            }
        }
        return null
    }

    private fun eliminationAlong(
        state: SolverState,
        places: List<Int>,
        digit: Int,
        lineCells: List<Int>,
        lineName: String,
    ): Deduction.Elimination? {
        val targets = lineCells
            .filter { it !in places }
            .filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
            .toSet()
        if (targets.isEmpty()) return null

        return Deduction.Elimination(
            technique = name,
            difficulty = difficulty,
            explanation = "Inside this box, $digit can only go in this $lineName. " +
                "So $digit is somewhere here, and can be ruled out of the rest of the $lineName.",
            supportingCells = places.toSet(),
            digit = digit,
            fromCells = targets,
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:solver:test --tests '*PointingPairTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/solver
git commit -m "Add pointing pair technique"
```

---

## Task 12: BoxLineReduction — a digit confined to one box within a line

The mirror image of Task 11. When every home for a digit in a row falls inside one box, the digit is in that box, so it can be struck from the box's other cells.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/BoxLineReduction.kt`
- Modify: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/Technique.kt` — restore `ALL_TECHNIQUES`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/BoxLineReductionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoxLineReductionTest {

    /**
     * In row 0, digit 4 can only sit at cells 0 and 1 — both in box 0.
     * So 4 is in box 0 on row 0, and can go from the box's other cells.
     */
    private fun fourConfinedToBoxZeroOfRowZero(): SolverState = stateWithCandidates(
        0 to CandidateSet.of(4, 7),
        1 to CandidateSet.of(4, 8),
        *(2..8).map { it to CandidateSet.of(1, 2) }.toTypedArray(),
        *listOf(9, 10, 11, 18, 19, 20).map { it to CandidateSet.of(4, 9) }.toTypedArray(),
    )

    @Test
    fun `eliminates the digit from the rest of the box`() {
        val deduction = assertIs<Deduction.Elimination>(BoxLineReduction.find(fourConfinedToBoxZeroOfRowZero()))
        assertEquals(4, deduction.digit)
        assertEquals(setOf(9, 10, 11, 18, 19, 20), deduction.fromCells)
        assertEquals(Difficulty.HARD, deduction.difficulty)
    }

    @Test
    fun `points at the confined cells as evidence`() {
        val deduction = assertIs<Deduction.Elimination>(BoxLineReduction.find(fourConfinedToBoxZeroOfRowZero()))
        assertEquals(setOf(0, 1), deduction.supportingCells)
    }

    @Test
    fun `explains the confinement`() {
        val deduction = assertIs<Deduction.Elimination>(BoxLineReduction.find(fourConfinedToBoxZeroOfRowZero()))
        assertTrue(deduction.explanation.contains("4"), deduction.explanation)
        assertTrue(deduction.explanation.contains("box"), deduction.explanation)
    }

    @Test
    fun `finds nothing when the digit spans two boxes of the line`() {
        val state = stateWithCandidates(
            0 to CandidateSet.of(4, 7),
            4 to CandidateSet.of(4, 8),   // box 1, so the row's fours span two boxes
            *listOf(1, 2, 3, 5, 6, 7, 8).map { it to CandidateSet.of(1, 2) }.toTypedArray(),
            *listOf(9, 10, 11, 18, 19, 20).map { it to CandidateSet.of(4, 9) }.toTypedArray(),
        )
        assertNull(BoxLineReduction.find(state))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --tests '*BoxLineReductionTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: BoxLineReduction`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * If every home for a digit in a row or column falls inside one box, the digit is in
 * that box, so it can be eliminated from the box's cells off that line.
 *
 * The mirror of [PointingPair], which reasons from the box outwards.
 */
object BoxLineReduction : Technique {

    override val name = "Box line reduction"
    override val difficulty = Difficulty.HARD

    override fun find(state: SolverState): Deduction? {
        for (line in 0 until 9) {
            findIn(state, Coordinates.rowIndices[line], "row")?.let { return it }
            findIn(state, Coordinates.colIndices[line], "column")?.let { return it }
        }
        return null
    }

    private fun findIn(state: SolverState, lineCells: List<Int>, lineName: String): Deduction.Elimination? {
        for (digit in 1..9) {
            if (lineCells.any { state.valueAt(it) == digit }) continue

            val places = lineCells.filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
            if (places.size !in 2..3) continue

            val box = Coordinates.boxOf(places[0])
            if (places.any { Coordinates.boxOf(it) != box }) continue

            val targets = Coordinates.boxIndices[box]
                .filter { it !in places }
                .filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
                .toSet()
            if (targets.isEmpty()) continue

            return Deduction.Elimination(
                technique = name,
                difficulty = difficulty,
                explanation = "In this $lineName, $digit can only go inside one box. " +
                    "So $digit is in that box on this $lineName, and can be ruled out of " +
                    "the rest of the box.",
                supportingCells = places.toSet(),
                digit = digit,
                fromCells = targets,
            )
        }
        return null
    }
}
```

- [ ] **Step 4: Restore the technique registry**

In `Technique.kt`, replace the temporary `emptyList()` from Task 8 Step 5 with:

```kotlin
val ALL_TECHNIQUES: List<Technique> = listOf(
    NakedSingle,
    HiddenSingle,
    PointingPair,
    BoxLineReduction,
)
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew :core:solver:test --console=plain
```

Expected: `BUILD SUCCESSFUL`, all technique tests green.

- [ ] **Step 6: Commit**

```bash
git add core/solver
git commit -m "Add box line reduction technique and restore the registry"
```

---

## Task 13: TechniqueSolver — solve by reasoning, and grade the result

Applies techniques in order, simplest first, until the puzzle is done or nothing more can be deduced. The hardest technique it needed is the puzzle's difficulty.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/TechniqueSolver.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/TechniqueSolverTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TechniqueSolverTest {

    @Test
    fun `solves an easy puzzle by reasoning alone`() {
        val outcome = TechniqueSolver.solve(Puzzles.EASY)
        assertIs<TechniqueOutcome.Solved>(outcome)
        assertTrue(outcome.solution.isComplete)
        assertTrue(outcome.solution.isValid)
    }

    @Test
    fun `agrees with the backtracking solver`() {
        val byLogic = assertIs<TechniqueOutcome.Solved>(TechniqueSolver.solve(Puzzles.EASY))
        val bySearch = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY))
        assertEquals(bySearch.solution.toGivensString(), byLogic.solution.toGivensString())
    }

    @Test
    fun `grades a puzzle by the hardest technique it needed`() {
        val outcome = assertIs<TechniqueOutcome.Solved>(TechniqueSolver.solve(Puzzles.EASY))
        assertEquals(outcome.steps.maxOf { it.difficulty }, outcome.difficulty)
    }

    @Test
    fun `records the steps it took in order`() {
        val outcome = assertIs<TechniqueOutcome.Solved>(TechniqueSolver.solve(Puzzles.EASY))
        assertTrue(outcome.steps.isNotEmpty())
        assertTrue(outcome.steps.any { it.technique == NakedSingle.name || it.technique == HiddenSingle.name })
    }

    @Test
    fun `gives up gracefully on a puzzle beyond its techniques`() {
        // The hardest known puzzle needs methods this engine does not implement.
        val outcome = TechniqueSolver.solve(Puzzles.HARDEST)
        if (outcome is TechniqueOutcome.Stuck) {
            assertTrue(outcome.partial.filledCount >= Puzzles.HARDEST.givenCount)
        } else {
            assertIs<TechniqueOutcome.Solved>(outcome)
        }
    }

    @Test
    fun `reports an unsolvable puzzle rather than looping`() {
        assertIs<TechniqueOutcome.Invalid>(TechniqueSolver.solve(Puzzles.CONTRADICTORY))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --tests '*TechniqueSolverTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: TechniqueSolver`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Grid

/** What reasoning alone made of a puzzle. */
sealed interface TechniqueOutcome {

    /** Finished without guessing. [difficulty] is the hardest technique required. */
    data class Solved(
        val solution: Grid,
        val steps: List<Deduction>,
        val difficulty: Difficulty,
    ) : TechniqueOutcome

    /** Ran out of techniques. [partial] is as far as reasoning got. */
    data class Stuck(
        val partial: Grid,
        val steps: List<Deduction>,
    ) : TechniqueOutcome

    /** The givens break the rules. */
    data object Invalid : TechniqueOutcome
}

/**
 * Solves the way a person does: apply the simplest technique that yields something,
 * repeat, and stop when nothing applies.
 *
 * This never guesses. A puzzle it cannot finish is a puzzle that needs a technique the
 * engine does not know, which is a normal and expected outcome — [Solver] handles those.
 */
object TechniqueSolver {

    fun solve(grid: Grid): TechniqueOutcome {
        val state = SolverState.from(grid) ?: return TechniqueOutcome.Invalid
        val steps = mutableListOf<Deduction>()

        while (!state.isSolved) {
            val deduction = nextDeduction(state) ?: break
            if (!apply(state, deduction)) return TechniqueOutcome.Invalid
            steps += deduction
        }

        return if (state.isSolved) {
            TechniqueOutcome.Solved(
                solution = state.toGrid(),
                steps = steps,
                difficulty = steps.maxOfOrNull { it.difficulty } ?: Difficulty.EASY,
            )
        } else {
            TechniqueOutcome.Stuck(partial = state.toGrid(), steps = steps)
        }
    }

    /** The easiest available deduction, since a hint should offer the simplest route. */
    fun nextDeduction(state: SolverState): Deduction? =
        ALL_TECHNIQUES.firstNotNullOfOrNull { it.find(state) }

    /** Applies a deduction to the state. Returns false if it produced a contradiction. */
    fun apply(state: SolverState, deduction: Deduction): Boolean = when (deduction) {
        is Deduction.Placement -> {
            state.markReported(deduction.index)
            state.assign(deduction.index, deduction.digit)
        }

        is Deduction.Elimination ->
            deduction.fromCells.all { state.eliminate(it, deduction.digit) }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:solver:test --tests '*TechniqueSolverTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

If `solves an easy puzzle by reasoning alone` fails as `Stuck`, the likely cause is `NakedSingle.isReported` filtering too aggressively — propagation solves cells as a side effect and they must still be reportable once.

- [ ] **Step 5: Commit**

```bash
git add core/solver
git commit -m "Add technique solver with difficulty grading"
```

---

## Task 14: HintEngine — both hint styles behind one interface

The spec makes hint style a user setting, so both are built. `RevealHintEngine` names a digit. `ExplainedHintEngine` names the technique and the evidence, and only reveals the digit when asked a second time — and falls back to a plain reveal when no known technique applies, so the user is never left with nothing.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/HintEngine.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/HintEngineTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HintEngineTest {

    @Test
    fun `reveal names a cell and its correct digit`() {
        val hint = assertIs<Hint.Reveal>(RevealHintEngine.nextHint(Puzzles.EASY))
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        assertEquals(truth[hint.index].digit, hint.digit)
        assertEquals(Cell.Empty, Puzzles.EASY[hint.index])
    }

    @Test
    fun `explained names the technique and its evidence without giving the digit away`() {
        val hint = assertIs<Hint.Explained>(ExplainedHintEngine.nextHint(Puzzles.EASY))
        assertTrue(hint.technique.isNotBlank())
        assertTrue(hint.explanation.isNotBlank())
        assertTrue(hint.supportingCells.isNotEmpty())
    }

    @Test
    fun `an explained hint can be pressed for the answer`() {
        val hint = assertIs<Hint.Explained>(ExplainedHintEngine.nextHint(Puzzles.EASY))
        val reveal = assertIs<Hint.Reveal>(hint.answer)
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        assertEquals(truth[reveal.index].digit, reveal.digit)
    }

    @Test
    fun `explained falls back to a reveal when no known technique applies`() {
        // A grid where reasoning stalls immediately still has to produce something.
        val hint = ExplainedHintEngine.nextHint(Puzzles.HARDEST)
        assertTrue(hint is Hint.Explained || hint is Hint.Reveal, "got $hint")
    }

    @Test
    fun `there is no hint for a finished puzzle`() {
        val solved = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        val asGivens = Grid.of(solved.cells.map { Cell.given(it.digit!!) })
        assertNull(RevealHintEngine.nextHint(asGivens))
        assertNull(ExplainedHintEngine.nextHint(asGivens))
    }

    @Test
    fun `there is no hint for a puzzle that cannot be solved`() {
        assertNull(RevealHintEngine.nextHint(Puzzles.CONTRADICTORY))
        assertNull(ExplainedHintEngine.nextHint(Puzzles.CONTRADICTORY))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --tests '*HintEngineTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: HintEngine`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/** A suggestion for the user's next move. */
sealed interface Hint {

    /** Straight to the answer. */
    data class Reveal(val index: Int, val digit: Int) : Hint

    /**
     * The reasoning, without the answer. [supportingCells] are highlighted in the
     * overlay; [reveal] gives up the digit when the user asks again.
     */
    data class Explained(
        val technique: String,
        val explanation: String,
        val supportingCells: Set<Int>,
        val difficulty: Difficulty,
        /** The digit, withheld until the user asks a second time. Null when the step only eliminates. */
        val answer: Reveal?,
    ) : Hint
}

/** Produces the next hint for a puzzle, or null when there is nothing useful to say. */
interface HintEngine {
    fun nextHint(grid: Grid): Hint?
}

/**
 * Names a cell and its digit, choosing the most constrained empty cell so the hint
 * lands somewhere the user could plausibly have worked out next.
 */
object RevealHintEngine : HintEngine {

    override fun nextHint(grid: Grid): Hint? {
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution ?: return null
        val state = SolverState.from(grid) ?: return null

        val target = (0 until Coordinates.CELL_COUNT)
            .filter { !grid[it].isFilled }
            .minByOrNull { state.candidatesAt(it).size }
            ?: return null

        val digit = solution[target].digit ?: return null
        return Hint.Reveal(target, digit)
    }
}

/**
 * Explains the next step in human terms.
 *
 * Falls back to [RevealHintEngine] when the puzzle needs a technique this engine does
 * not know. A user who asked for help must always get help, even if the app cannot
 * dress it up as reasoning.
 */
object ExplainedHintEngine : HintEngine {

    override fun nextHint(grid: Grid): Hint? {
        if (Solver.solve(grid) !is SolveResult.Unique) return null
        val state = SolverState.from(grid) ?: return null

        val deduction = TechniqueSolver.nextDeduction(state)
            ?: return RevealHintEngine.nextHint(grid)

        val answer = when (deduction) {
            is Deduction.Placement -> Hint.Reveal(deduction.index, deduction.digit)
            // An elimination has no digit to reveal, so fall back to a real answer.
            is Deduction.Elimination -> RevealHintEngine.nextHint(grid) as? Hint.Reveal
        }

        return Hint.Explained(
            technique = deduction.technique,
            explanation = deduction.explanation,
            supportingCells = deduction.supportingCells,
            difficulty = deduction.difficulty,
            answer = answer,
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:solver:test --tests '*HintEngineTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run the whole suite**

```bash
./gradlew build --console=plain
```

Expected: `BUILD SUCCESSFUL` with every test green.

- [ ] **Step 6: Commit**

```bash
git add core/solver
git commit -m "Add hint engines for plain reveal and explained reasoning"
```

---

## Task 15: Answer checking — the API the UI will call

The spec's "check my answers" needs one call that compares a user's guesses against the truth. It lives here rather than in the UI because it is logic, and because it is easy to get subtly wrong.

**Files:**
- Create: `core/solver/src/main/kotlin/io/github/tonyxmelon/aisudoku/solver/AnswerCheck.kt`
- Test: `core/solver/src/test/kotlin/io/github/tonyxmelon/aisudoku/solver/AnswerCheckTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnswerCheckTest {

    private val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution

    @Test
    fun `a correct guess is marked correct`() {
        val firstEmpty = (0 until 81).first { !Puzzles.EASY[it].isFilled }
        val grid = Puzzles.EASY.with(firstEmpty, Cell.guess(truth[firstEmpty].digit!!))

        val result = assertIs<AnswerCheck.Checked>(AnswerChecker.check(grid))
        assertEquals(setOf(firstEmpty), result.correct)
        assertTrue(result.incorrect.isEmpty())
    }

    @Test
    fun `a wrong guess is marked wrong`() {
        val firstEmpty = (0 until 81).first { !Puzzles.EASY[it].isFilled }
        val wrong = (1..9).first { it != truth[firstEmpty].digit }
        val grid = Puzzles.EASY.with(firstEmpty, Cell.guess(wrong))

        val result = assertIs<AnswerCheck.Checked>(AnswerChecker.check(grid))
        assertEquals(setOf(firstEmpty), result.incorrect)
        assertTrue(result.correct.isEmpty())
    }

    @Test
    fun `givens are never judged`() {
        val result = assertIs<AnswerCheck.Checked>(AnswerChecker.check(Puzzles.EASY))
        assertTrue(result.correct.isEmpty())
        assertTrue(result.incorrect.isEmpty())
    }

    @Test
    fun `a puzzle without a unique solution cannot be checked`() {
        assertIs<AnswerCheck.NotCheckable>(AnswerChecker.check(Puzzles.AMBIGUOUS))
        assertIs<AnswerCheck.NotCheckable>(AnswerChecker.check(Puzzles.CONTRADICTORY))
    }

    @Test
    fun `the solution is offered alongside the verdict`() {
        val result = assertIs<AnswerCheck.Checked>(AnswerChecker.check(Puzzles.EASY))
        assertEquals(truth.toGivensString(), result.solution.toGivensString())
        assertNull(result.solution.cells.firstOrNull { !it.isFilled })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:solver:test --tests '*AnswerCheckTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: AnswerCheck`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/** The verdict on a user's handwritten answers. */
sealed interface AnswerCheck {

    /**
     * Every guess judged against the one true solution.
     *
     * There is no third state. The puzzle has exactly one solution, so a guess either
     * matches it or does not.
     */
    data class Checked(
        val solution: Grid,
        val correct: Set<Int>,
        val incorrect: Set<Int>,
    ) : AnswerCheck

    /**
     * The givens do not define a single puzzle, so no guess can be judged. In practice
     * this means recognition misread a given, and the caller should say so rather than
     * marking the user wrong.
     */
    data object NotCheckable : AnswerCheck
}

/** Judges handwritten guesses against the solution implied by the givens. */
object AnswerChecker {

    fun check(grid: Grid): AnswerCheck {
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution
            ?: return AnswerCheck.NotCheckable

        val correct = mutableSetOf<Int>()
        val incorrect = mutableSetOf<Int>()

        for (i in 0 until Coordinates.CELL_COUNT) {
            val cell = grid[i]
            if (cell.source != CellSource.GUESS) continue
            if (cell.digit == solution[i].digit) correct += i else incorrect += i
        }

        return AnswerCheck.Checked(solution, correct, incorrect)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:solver:test --tests '*AnswerCheckTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run the whole suite and commit**

```bash
./gradlew build --console=plain
git add core/solver
git commit -m "Add answer checking for handwritten guesses"
```

---

## Task 16: Close out the milestone

- [ ] **Step 1: Confirm the whole build is green from clean**

```bash
./gradlew clean build --console=plain
```

Expected: `BUILD SUCCESSFUL`. Do not claim the milestone is done without seeing this output.

- [ ] **Step 2: Check the module boundary held**

```bash
grep -rn "android" core/model/src core/solver/src --include=*.kt
```

Expected: no matches. `core:model` and `core:solver` must stay free of Android, or the JVM tests and the later iOS port both break.

- [ ] **Step 3: Update the spec's milestone table**

In `docs/superpowers/specs/2026-08-30-camera-sudoku-solver-design.md`, mark M0, M1 and M2 complete in the section 10 table by prefixing their deliverable text with `DONE — `.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Mark M0 to M2 complete: the core engine is built and tested"
```

---

## What this plan deliberately leaves out

- **Naked and hidden pairs, X-wing, and harder techniques.** Four techniques cover most puzzles, and `ExplainedHintEngine` falls back to a plain reveal when they run out, so the user is never stranded. Add more only if real puzzles show the fallback firing often.
- **Puzzle generation.** Nothing in the spec needs it. The app reads paper puzzles.
- **Serialization beyond `toGivensString`.** No storage in v1.
- **The `app` module.** The spec's M0 mentions an empty app that builds and runs; that moves to
  plan 2 with the rest of the Android work. Keeping AGP out of this plan means the whole milestone
  builds and tests on any machine with nothing but Gradle, which is worth more than an empty
  activity.
- **Android, CameraX, OpenCV, LiteRT.** Separate plans, and none of them can start until this engine exists — the recognizer's error repair depends on `Solver.hasUniqueSolution`.
