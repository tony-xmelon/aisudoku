package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.vision.CorpusFixtures
import io.github.tonyxmelon.aisudoku.vision.GateVerdict
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import io.github.tonyxmelon.aisudoku.vision.StructuralGate
import java.io.File
import kotlin.test.Test

/**
 * Runs the whole pipeline over a folder of photographs that are not in the corpus.
 *
 * For looking at something new before deciding whether it belongs in the corpus at all:
 * it reports what the gate said, and what the reader made of anything the gate passed,
 * without any label to check against.
 *
 *   ./gradlew :core:recognize:test --tests '*ScanFolderDumpTest*' -Dscan=<folder> --rerun-tasks
 */
class ScanFolderDumpTest {

    private companion object {
        const val NARROW_ENOUGH_TO_QUESTION = 0.60
    }

    @Test
    fun `report what the pipeline makes of each photograph`() {
        val folder = System.getProperty("scan")?.takeIf { it.isNotEmpty() }?.let(::File) ?: return
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val files = folder.listFiles { f: File -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }
            ?.sortedBy { it.name }
            .orEmpty()
        println("scanning ${files.size} photographs in $folder")

        for (file in files) {
            val image = CorpusFixtures.load(file)
            println("\n=== ${file.name}  ${image.width}x${image.height} ===")

            // Where the grid is, even when the gate then refuses it: a refusal for size
            // says nothing about whether the grid was found, and on a photograph of a
            // screen that is the thing in doubt.
            val located = io.github.tonyxmelon.aisudoku.vision.GridLocator.locate(image)
            if (located is io.github.tonyxmelon.aisudoku.vision.GridLocation.Found) {
                val q = located.quad
                val side = minOf(q.topEdge, q.rightEdge, q.bottomEdge, q.leftEdge)
                println(
                    "  grid found: side %.0fpx (%.0f%% of the frame), score %.2f, tilt %.1f deg"
                        .format(side, 100.0 * side / minOf(image.width, image.height),
                                located.gridScore, q.rotationDegrees)
                )
            } else {
                // Why it gave up, not just that it did: how many shapes it looked at and
                // how close the best of them came to reading as nine rows and columns.
                val missed = located as io.github.tonyxmelon.aisudoku.vision.GridLocation.NoGrid
                println(
                    "  no grid: %d candidates considered, best scored %.2f against %.2f needed"
                        .format(
                            missed.candidatesConsidered, missed.bestScore,
                            io.github.tonyxmelon.aisudoku.vision.GridLocator.MIN_GRID_SCORE,
                        )
                )
                missed.best?.let { q ->
                    val edges = listOf(q.topEdge, q.rightEdge, q.bottomEdge, q.leftEdge)
                    println(
                        "    its best shape: %.0f by %.0f, squareness %.2f, skew %.1f deg"
                            .format(edges.max(), edges.min(), edges.min() / edges.max(),
                                    q.maxCornerAngleDeviation)
                    )
                }
            }

            when (val verdict = StructuralGate.assess(image)) {
                is GateVerdict.Rejected -> println("  GATE REFUSED: ${verdict.reason::class.simpleName}")
                is GateVerdict.Usable -> {
                    // The straightened grid, for reading by eye. A label transcribed off
                    // the original is a label transcribed off a photograph taken at an
                    // angle on creased paper; this is the same picture the reader works
                    // from, square and flat.
                    System.getProperty("write")?.takeIf { it.isNotEmpty() }?.let { out ->
                        val r = verdict.rectified
                        val image = java.awt.image.BufferedImage(
                            r.width, r.height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY,
                        )
                        image.raster.setDataElements(0, 0, r.width, r.height, r.pixels)
                        java.io.File(out).mkdirs()
                        javax.imageio.ImageIO.write(
                            image, "png",
                            java.io.File(out, file.nameWithoutExtension + "-flat.png"),
                        )
                    }

                    val side = minOf(
                        verdict.quad.topEdge, verdict.quad.rightEdge,
                        verdict.quad.bottomEdge, verdict.quad.leftEdge,
                    )
                    println(
                        "  gate: usable, grid side ${side.toInt()}px, score %.2f, luma %.0f"
                            .format(verdict.gridScore, verdict.quality.meanLuma)
                    )
                    // Anything read as a digit whose ink is a narrow upright sliver.
                    // A printed digit is roughly two thirds as wide as it is tall; a
                    // remnant of a rule between cells is a fraction of that, and reads
                    // as a 1 because that is what a vertical stroke looks like.
                    val inks = CellAnalyzer.inspect(verdict.cells)
                    inks.forEachIndexed { index, ink ->
                        val blob = ink?.blob ?: return@forEachIndexed
                        // No real digit in the corpus is narrower than 0.42 of its own
                        // height, and a printed 1 on a screen measures about 0.38, so
                        // anything under this is worth looking at. Reporting, not
                        // judging: what to do about it needs a photograph at full size.
                        if (blob.aspect < NARROW_ENOUGH_TO_QUESTION) {
                            println(
                                "    narrow ink at r%dc%d: aspect %.2f, height %.2f of cell, stroke %.1fpx"
                                    .format(index / 9 + 1, index % 9 + 1, blob.aspect,
                                            blob.heightRatio, blob.strokeWidth)
                            )
                        }
                    }

                    when (val result = GridReader().read(verdict.cells)) {
                        is ReadResult.Unreadable -> println("  READER REFUSED: ${result.reason}")
                        is ReadResult.Accepted -> {
                            println("  read, confident. The grid the user would be shown:")
                            printGrid(result.grid)
                        }
                        is ReadResult.NeedsConfirmation -> {
                            println("  read, unsure of ${result.uncertainCells.size} cells." +
                                " The grid the user would be shown:")
                            printGrid(result.grid)
                        }
                    }
                }
            }
        }
    }

    /**
     * The grid as assembled, which is not the same as the readings.
     *
     * A grid whose printed digits do not solve goes through a repair pass first, so
     * printing the readings shows what the classifier said rather than what the user
     * would see - and those differ in exactly the interesting case.
     */
    private fun printGrid(grid: io.github.tonyxmelon.aisudoku.model.Grid) {
        for (row in 0 until 9) {
            val line = (0 until 9).joinToString("") { column ->
                val cell = grid[row * 9 + column]
                // A digit the app took for print is the puzzle itself; one it took for
                // handwriting is shown as the user's own answer, and can be tapped and
                // corrected. Which of the two a phantom lands in matters a great deal.
                when {
                    cell.digit == null -> "."
                    cell.source == io.github.tonyxmelon.aisudoku.model.CellSource.GIVEN ->
                        cell.digit.toString()
                    else -> "(" + cell.digit + ")"
                }
            }
            println("    $line")
        }
    }
}
