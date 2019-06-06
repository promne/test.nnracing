package genetic

import nn.SimpleNN
import kotlin.random.Random

enum class EvolveResultStatsType {
    MAX,
    MIN,
    AVG,
    MEDIAN,
    BEST_AGENT
}

final class EvolveResult(val generation: List<DoubleArray>, val stats:Map<EvolveResultStatsType,Any>)

fun evolve(nnShape: LongArray, generation: List<DoubleArray>, fitnessEvaluation: (Collection<SimpleNN>) -> List<Pair<SimpleNN, Double>>, retainRatio: Double = 0.6, mutationChance: Double = 0.2, mutationSize: Double = 0.3, mutationStrength: Double = 2.0) : EvolveResult {
    val statsResult = mutableMapOf<EvolveResultStatsType, Any>()

    // get score and sort by it
    val gradedAgents = fitnessEvaluation(generation.map { SimpleNN(Pair(nnShape, it)) }).map { Pair(it.first.serialize().second, it.second) }.sortedByDescending { it.second }
    statsResult[EvolveResultStatsType.MAX] = gradedAgents.first().second
    statsResult[EvolveResultStatsType.MIN] = gradedAgents.last().second
    statsResult[EvolveResultStatsType.AVG] = gradedAgents.map { it.second }.average()
    statsResult[EvolveResultStatsType.MEDIAN] = gradedAgents.map { it.second }[gradedAgents.size/2]

    statsResult[EvolveResultStatsType.BEST_AGENT] = gradedAgents[0].first.asList()


    val graded = gradedAgents.map { it.first }


    val retainLength = (retainRatio * generation.size).toInt()

    // transfer the breeding material plus add some random ones from the less skilled
    var parents = graded.take(retainLength - 1) + graded.drop(retainLength).filter { Random.nextDouble()<(1-retainRatio) }

    // mutate some + keep the best one
    parents = arrayListOf(graded[0]) + parents.map { if (Random.nextDouble()<mutationChance) mutate(it, mutationSize, mutationStrength) else it }


    // fill empty spots by breeding
    val children = mutableSetOf<DoubleArray>()
    while (children.size+parents.size<generation.size) {
        val p1 = parents.random()
        val p2 = parents.random()
        if (p1 != p2) {
            children.add(breed(Pair(p1, p2)))
        }
    }

    return EvolveResult(parents+children, statsResult)
}

private fun mutate(network: DoubleArray, mutationSize: Double, mutationStrength: Double): DoubleArray {
    val m = (network.size*mutationSize).toInt() % network.size

    val indexes = mutableSetOf<Int>()
    while (indexes.size < m) {
        indexes.add(Random.nextInt(network.size-1))
    }

    val res = network.clone()
    indexes.forEach {
        val mutationAmount = Random.nextDouble(mutationStrength * 2) - mutationStrength
        res[it] += mutationAmount
    }
    return res
}

fun createPopulation(weightsSize: Int, count: Int, weightRange: Double = 20.0) = (0 until count).map { DoubleArray(weightsSize) { Random.nextDouble(weightRange) } }

private fun breed(parents: Pair<DoubleArray, DoubleArray>): DoubleArray {
    val pivot = Random.nextInt(Math.min(parents.first.size, parents.second.size))
    return (parents.first.take(pivot) + parents.second.drop(pivot)).toDoubleArray()
}
