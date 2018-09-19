package models

import com.google.ortools.linearsolver.MPSolver
import com.rnett.eve.ligraph.sde.*
import kotlin.math.ceil

class OreOptimizer(val mats: ItemList, val refineRate: Double = 92.39) {
    fun optimize(): OreOptimizerSolution {
        val prices = compressedOres.map { it.type }.getPrices().sell()

        val solver = MPSolver("Ore Solver", MPSolver.OptimizationProblemType.GLOP_LINEAR_PROGRAMMING)
        val infinity = MPSolver.infinity()

        // the amount of each ore
        val oreVars = compressedOres.map { Pair(it, solver.makeIntVar(0.0, infinity, it.type.typeName)) }.toMap()

        val best = solver.objective()
        best.setMinimization()

        // sets the price coefficients
        oreVars.forEach {
            best.setCoefficient(it.value, prices[it.key.type] ?: Double.POSITIVE_INFINITY)
        }
        minerals.forEach {
            // the total amount of each mineral
            val constraint = solver.makeConstraint(mats[it].toDouble(), infinity)
            val type = it
            oreVars.forEach {
                // the amount of mineral for each ore
                constraint.setCoefficient(it.value, it.key[type].toDouble() * refineRate / 100)
            }
        }

        solver.solve()

        return OreOptimizerSolution(oreVars.mapValues { ceil(it.value.solutionValue()).toLong() }.mapKeys { it.key.type }.filter { it.value > 0 }.toItemList(), best.value())
    }

    companion object {
        fun optimize(mats: ItemList, refineRate: Double = 92.39): OreOptimizerSolution {
            return OreOptimizer(mats, refineRate).optimize()
        }
    }

}

data class OreOptimizerSolution internal constructor(val ores: ItemList, val price: Double)