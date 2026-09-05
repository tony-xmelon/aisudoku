package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Where the grid is, or why it could not be found. */
sealed interface GridLocation {

    data class Found(
        val quad: Quad,
        val gridScore: Double,
        val rectified: GrayImage,
    ) : GridLocation

    /**
     * No candidate contained anything resembling a 9x9 grid.
     *
     * [best] is the shape it came closest on, kept so the camera can draw what it is
     * looking at. Being shown the app outlining the book instead of the puzzle explains a
     * failure that no wording ever could.
     */
    data class NoGrid(
        val bestScore: Double,
        val candidatesConsidered: Int,
        val best: Quad? = null,
    ) : GridLocation
}

/**
 * Finds the sudoku grid in a photograph and straightens it.
 *
 * Candidates come from [QuadDetector] ordered by size; the one chosen is whichever most
 * looks like a grid once rectified, which is not usually the largest.
 */
object GridLocator {

    const val RECTIFIED_SIZE = 1152          // 128 pixels per cell
    const val MIN_GRID_SCORE = 0.35

    /**
     * Smallest square a candidate is scored on: 32 pixels a cell, enough for lines.
     *
     * Scoring happens at the grid's own size rather than at [RECTIFIED_SIZE], and this is
     * the floor for that. Below it there is not enough of a line left to measure.
     */
    private const val MIN_SCORING_SIZE = 288

    /** How many of the failed candidates to look at again, best score first. */
    private const val RESCUE_CANDIDATES = 3

    /** And how much larger to try them. Beyond these the quad takes in the page around it. */
    private val RESCUE_GROWTHS = listOf(1.02, 1.04)

    /**
     * How many of the ten lines on an axis may be hidden, on the very last attempt.
     *
     * One, because one is what an occlusion costs and two starts forgiving grids that are
     * not there. Measured on the photograph this exists for, one line takes it from 0.22
     * to 0.45 and two takes it to 0.46, so the second buys nothing and only lowers the bar.
     */
    private const val OBSCURED_LINES_ALLOWED = 1

    fun locate(image: GrayImage): GridLocation {
        // Each working size in turn, stopping at the first that finds a grid. A grid that
        // fills the frame is found at the smallest and cheapest; one that takes a sixth of
        // a large capture needs the larger, and paying for that only when the first comes
        // up empty keeps the common case as quick as it was.
        var nearest: GridLocation.NoGrid? = null
        for (workingEdge in QuadDetector.workingEdges()) {
            when (val attempt = look(image, workingEdge)) {
                is GridLocation.Found -> return attempt
                is GridLocation.NoGrid ->
                    // Starting from a made-up miss and only replacing it on a better score
                    // reported nothing considered whenever every attempt scored zero, which
                    // reads as "there was nothing square in shot" when there plainly was.
                    if (nearest == null || attempt.bestScore > nearest.bestScore) {
                        nearest = attempt
                    }
            }
        }

        // Every working size gets its chance at an outline before any of them falls back to
        // the cells. Asking the cells inside the loop instead makes them a last resort only
        // within one size, which is not the same thing: on one newsprint page the cells
        // answer at a thousand pixels and the answer is worse than the outline that would
        // have been found at sixteen hundred, and because the loop returns on the first
        // success the better answer is never reached. The cells are there for a photograph
        // no outline fits at any size.
        for (workingEdge in QuadDetector.workingEdges()) {
            val cells = askTheCells(image, image.toMat(), workingEdge) ?: continue
            return GridLocation.Found(
                cells.quad, cells.score, cells.straightened(image.toMat()).toGrayImage(),
            )
        }

        val obscured = askAgainForAnObscuredGrid(image)
        if (obscured != null) {
            val rectified = rectify(image.toMat(), obscured.first, RECTIFIED_SIZE.toDouble())
            return GridLocation.Found(obscured.first, obscured.second, rectified.toGrayImage())
        }
        return nearest ?: GridLocation.NoGrid(0.0, 0)
    }

    /**
     * The rectified image a candidate would be scored on.
     *
     * Exposed so that a measurement can score a shape the detector did not choose, which
     * is the only way to ask why a photograph came back with no grid.
     */
    internal fun rectifyFor(image: GrayImage, quad: Quad): GrayImage =
        rectify(image.toMat(), quad, scoringSize(quad)).toGrayImage()

    private fun look(image: GrayImage, workingEdge: Double): GridLocation {
        val full = image.toMat()
        val candidates = QuadDetector.detect(image, workingEdge)

        // Scored at the size the grid actually is in the photograph, not blown up to the
        // working size first.
        //
        // Every candidate used to be stretched to 1152 square before scoring, which is
        // three or four times life size for a grid on a preview frame - and a line
        // upscaled that far is a soft ramp rather than a line. The score is the weakest of
        // the twenty lines a grid must have, so one line lost to blur takes the whole
        // score down with it: a perfectly framed puzzle scored 0.71 filling the frame and
        // 0.32 at half that size, and the second one was rejected. Reported from the phone
        // as a grid the app would not see however it was held.
        //
        // Scoring small also means only the winner is stretched to the working size, which
        // is most of the work this function used to do on every frame.
        val scored = candidates.map { quad -> quad to GridScorer.score(rectify(full, quad, scoringSize(quad))) }

        val winner = scored.maxByOrNull { it.second }

        // The outline first, then the same outlines grown a little, and only then the
        // cells. In that order because the first two are what every photograph that reads
        // today takes, and asking the cells costs another pass over the contours - so it
        // is worth paying for exactly when there is otherwise no answer at all. See
        // [CellGrid] for why the cells can be there when the border is not.
        val best = winner?.takeIf { it.second >= MIN_GRID_SCORE }
            ?: rescueByGrowing(full, scored)
            ?: return GridLocation.NoGrid(
                winner?.second ?: 0.0, candidates.size, winner?.first,
            )

        val rectified = rectify(full, best.first, RECTIFIED_SIZE.toDouble())
        return GridLocation.Found(best.first, best.second, rectified.toGrayImage())
    }

    /**
     * A last look at candidates that scored too low, grown very slightly.
     *
     * A contour traced round the grid's own rule can come back a shade inside it, and the
     * score is the weakest of the twenty lines a grid must have - so the two outer rules
     * falling a few pixels outside the quad take the whole score down with them. On one
     * newsprint photograph the grid scored 0.11 as traced and 0.38 two percent larger,
     * against 0.35 needed: not a grid that was missed, a rectification a hair out of
     * place. Straightened at that size it is the whole puzzle, square and complete.
     *
     * Only when everything has already failed, so a photograph that reads today is scored
     * exactly as often as before and takes the same path. Two percent and four are the
     * only sizes tried: at six the quad has swallowed enough of the page around it that
     * the score falls back to zero, so this cannot wander far from what was traced.
     */
    private fun rescueByGrowing(
        full: Mat,
        scored: List<Pair<Quad, Double>>,
    ): Pair<Quad, Double>? {
        val attempts = scored.sortedByDescending { it.second }.take(RESCUE_CANDIDATES)
            .flatMap { (quad, _) -> RESCUE_GROWTHS.map { grown(quad, it) } }
            .map { quad -> quad to GridScorer.score(rectify(full, quad, scoringSize(quad))) }
        return attempts.maxByOrNull { it.second }?.takeIf { it.second >= MIN_GRID_SCORE }
    }

    /**
     * The grid as its cells describe it, when nothing traced round it was a grid.
     *
     * Scored on the same scale as everything else and held to the same threshold, so this
     * can only ever add an answer where there was none - it cannot outvote an outline that
     * already scored.
     *
     * The plane first, and the surface only where the plane will not do - the same order,
     * and for the same reason, as outlines before cells.
     *
     * Taking whichever of the two scored higher was tried and is wrong, because the score
     * is not what it would be being used for. It measures whether twenty lines are present,
     * which is the right question for "is this a grid" and a poor one for "is this
     * straightened well enough to cut into cells". On one newsprint page the surface scores
     * 0.54 against the plane's own, wins on that, and reads worse: the page was flat, the
     * plane had it right, and the surface bought a better score with a worse rectification.
     * So the surface is only reached when the plane has not cleared the bar at all.
     */
    private fun askTheCells(image: GrayImage, full: Mat, workingEdge: Double): FromCells? {
        for (lattice in CellGrid.lattices(image, workingEdge)) {
            val quad = lattice.quad()
            val size = scoringSize(quad)
            val flat = GridScorer.score(rectify(full, quad, size))
            if (flat >= MIN_GRID_SCORE) return FromCells(quad, flat, lattice, curved = false)
        }
        for (lattice in CellGrid.lattices(image, workingEdge)) {
            val quad = lattice.quad()
            val curved = lattice.flatten(full, scoringSize(quad))?.let { GridScorer.score(it) }
            if (curved != null && curved >= MIN_GRID_SCORE) {
                return FromCells(quad, curved, lattice, curved = true)
            }
        }
        return null
    }

    /**
     * The last question asked of a photograph: is this a grid with something across it?
     *
     * The score is the weakest of the twenty lines a grid must have, which is strict on
     * purpose and has one consequence that is not intended - a single line hidden behind
     * a thumb, a fold, or a highlight on a screen takes the whole score down with it. The
     * photograph that showed this is an advertisement on a phone screen with a tan overlay
     * across the lower left of the puzzle: nineteen lines present, one covered, 0.22
     * against the 0.35 it needs, and 0.22 again cropped to the screen alone. A person sees
     * that grid immediately.
     *
     * So one line an axis may be missing, and only here. Not for choosing between
     * candidates, which was tried and cost a corpus page: with a line forgiven, a worse
     * quad that has no single terrible line outranks the right one, and the grid that
     * comes back cannot be fitted. Ranking stays strict; this only decides whether to
     * accept something nothing else would, after the outlines, the growing and the cells
     * have all come up empty.
     */
    private fun askAgainForAnObscuredGrid(image: GrayImage): Pair<Quad, Double>? {
        val full = image.toMat()
        for (workingEdge in QuadDetector.workingEdges()) {
            val best = QuadDetector.detect(image, workingEdge)
                .map { quad ->
                    quad to GridScorer.score(
                        rectify(full, quad, scoringSize(quad)), OBSCURED_LINES_ALLOWED,
                    )
                }
                .maxByOrNull { it.second }
                ?.takeIf { it.second >= MIN_GRID_SCORE }
            if (best != null) return best
        }
        return null
    }

    /** A grid the cells found, and the straightening that scored best on it. */
    private class FromCells(
        val quad: Quad,
        val score: Double,
        private val lattice: CellGrid.Lattice,
        private val curved: Boolean,
    ) {
        fun straightened(full: Mat): Mat =
            (if (curved) lattice.flatten(full, RECTIFIED_SIZE.toDouble()) else null)
                ?: rectify(full, quad, RECTIFIED_SIZE.toDouble())
    }

    /** The same quad, larger about its own centre. */
    private fun grown(quad: Quad, by: Double): Quad {
        val cx = quad.corners.sumOf { it.x } / 4
        val cy = quad.corners.sumOf { it.y } / 4
        val c = quad.corners.map { Corner(cx + (it.x - cx) * by, cy + (it.y - cy) * by) }
        return Quad(c[0], c[1], c[2], c[3])
    }



    /** The grid's own size in the photograph, within the bounds worth working at. */
    private fun scoringSize(quad: Quad): Double {
        val longest = maxOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
        return longest.coerceIn(MIN_SCORING_SIZE.toDouble(), RECTIFIED_SIZE.toDouble())
    }

    /** Warps [quad] out of the full-resolution image onto a square of [side] pixels. */
    internal fun rectify(full: Mat, quad: Quad, side: Double = RECTIFIED_SIZE.toDouble()): Mat {
        val transform = Imgproc.getPerspectiveTransform(
            MatOfPoint2f(*quad.corners.map { Point(it.x, it.y) }.toTypedArray()),
            MatOfPoint2f(
                Point(0.0, 0.0), Point(side, 0.0),
                Point(side, side), Point(0.0, side),
            ),
        )
        val out = Mat()
        Imgproc.warpPerspective(full, out, transform, Size(side, side))
        return out
    }
}
