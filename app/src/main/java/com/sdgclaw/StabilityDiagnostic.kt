package com.sdgclaw

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * StabilityDiagnostic - native Kotlin port of the standalone blackbox_diagnostic
 * (engine.py). No external dependencies (no numpy) - pure Kotlin math so it can
 * run directly in the app without needing the Termux bridge.
 *
 * Feed it a sequence of numeric state vectors (one per agent turn) and it reports
 * whether the trajectory is converging, diverging, stuck in a limit-cycle, or
 * chaotic/unsettled.
 */
object StabilityDiagnostic {

    data class Report(
        val steps: Int,
        val dimensions: Int,
        val drift: List<Double>,
        val entropy: List<Double>,
        val finalDrift: Double,
        val meanEntropy: Double,
        val sScore: Double,
        val regime: String,
        val regimeDetail: String
    )

    private fun norm(v: DoubleArray): Double = sqrt(v.sumOf { it * it })

    private fun diam(states: Array<DoubleArray>): Double {
        val dims = states[0].size
        val maxV = DoubleArray(dims) { d -> states.maxOf { it[d] } }
        val minV = DoubleArray(dims) { d -> states.minOf { it[d] } }
        val span = DoubleArray(dims) { maxV[it] - minV[it] }
        val d = norm(span)
        return if (d > 1e-9) d else 1.0
    }

    private fun shannonEntropyNormalized(vec: DoubleArray): Double {
        val maxV = vec.max()
        val expV = vec.map { exp(it - maxV) }
        val sum = expV.sum()
        val p = expV.map { it / sum }
        val h = -p.sumOf { it * ln(it + 1e-12) }
        val hMax = if (vec.size > 1) ln(vec.size.toDouble()) else 1.0
        return if (hMax > 0) h / hMax else 0.0
    }

    private fun classifyRegime(
        states: Array<DoubleArray>,
        drifts: DoubleArray,
        window: Int,
        tol: Double = 1e-3
    ): Pair<String, String> {
        if (drifts.size < 3) return "insufficient-data" to "need at least 3 steps to classify"

        val w = minOf(window, drifts.size)
        val tailDrift = drifts.copyOfRange(drifts.size - w, drifts.size)
        val tailStates = states.copyOfRange(states.size - w, states.size)

        if (tailDrift.last() < tol) {
            return "converging" to "final drift ${"%.2e".format(tailDrift.last())} < tol ${"%.0e".format(tol)}"
        }

        if (tailDrift.size >= 3) {
            val n = tailDrift.size
            val xMean = (0 until n).average()
            val yMean = tailDrift.average()
            var num = 0.0
            var den = 0.0
            for (i in 0 until n) {
                num += (i - xMean) * (tailDrift[i] - yMean)
                den += (i - xMean) * (i - xMean)
            }
            val slope = if (den != 0.0) num / den else 0.0
            if (slope > 0 && tailDrift.last() > tailDrift.first() * 1.5 && tailDrift.last() > 1.0) {
                return "diverging" to "drift grew from ${"%.3f".format(tailDrift.first())} to ${"%.3f".format(tailDrift.last())}"
            }
        }

        val d = diam(states)
        val last = tailStates.last()
        for (period in intArrayOf(1, 2, 3, 4, 5, 6, 8, 10)) {
            if (tailStates.size > period + 2) {
                val candidate = tailStates[tailStates.size - 1 - period]
                val diff = DoubleArray(last.size) { last[it] - candidate[it] }
                val dist = norm(diff) / d
                if (dist < tol * 10) {
                    return "limit-cycle" to "state recurs with period~$period, normalized distance ${"%.2e".format(dist)}"
                }
            }
        }

        return "chaotic-or-unsettled" to
            "drift bounded (${"%.3f".format(tailDrift.min())}-${"%.3f".format(tailDrift.max())}), no state recurrence found"
    }

    /**
     * @param statesIn sequence of state vectors, one per turn (must all be the same length)
     * @param alpha weight on entropy in the stability score
     * @param beta weight on drift in the stability score
     * @param lam decay rate applied to older steps in the stability score
     * @param window how many recent steps to consider when classifying the regime
     */
    fun analyze(
        statesIn: Array<DoubleArray>,
        alpha: Double = 1.0,
        beta: Double = 1.0,
        lam: Double = 0.15,
        window: Int = 8
    ): Report {
        require(statesIn.size >= 2) { "Need at least 2 states to analyze a trajectory." }

        val d = diam(statesIn)
        val drifts = DoubleArray(statesIn.size - 1) { i ->
            val diff = DoubleArray(statesIn[i].size) { statesIn[i + 1][it] - statesIn[i][it] }
            norm(diff) / d
        }

        val dims = statesIn[0].size
        val mean = DoubleArray(dims) { dIdx -> statesIn.map { it[dIdx] }.average() }
        val std = DoubleArray(dims) { dIdx ->
            val m = mean[dIdx]
            val variance = statesIn.map { (it[dIdx] - m) * (it[dIdx] - m) }.average()
            sqrt(variance).let { if (it < 1e-9) 1.0 else it }
        }
        val statesNorm = statesIn.map { s -> DoubleArray(dims) { (s[it] - mean[it]) / std[it] } }
        val entropies = DoubleArray(statesNorm.size - 1) { i -> shannonEntropyNormalized(statesNorm[i + 1]) }

        val k = drifts.size
        var sScore = 0.0
        for (i in 0 until k) {
            val weight = exp(-lam * (i + 1))
            sScore += weight * (alpha * entropies[i] - beta * drifts[i])
        }

        val (regime, detail) = classifyRegime(statesIn, drifts, window)

        return Report(
            steps = k,
            dimensions = dims,
            drift = drifts.toList(),
            entropy = entropies.toList(),
            finalDrift = drifts.last(),
            meanEntropy = entropies.average(),
            sScore = sScore,
            regime = regime,
            regimeDetail = detail
        )
    }
}
