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

final class EvolveResult(val generation: List<FloatArray>, val stats:Map<EvolveResultStatsType,Any>)

fun evolve(nnShape: LongArray, generation: List<FloatArray>, fitnessEvaluation: (Collection<SimpleNN>) -> List<Pair<SimpleNN, Double>>, retainRatio: Double = 0.6, mutationChance: Double = 0.2, mutationSize: Double = 0.3, mutationStrength: Double = 2.0) : EvolveResult {
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
    var parents = graded.take(retainLength) + graded.drop(retainLength).filter { Random.nextDouble()<(1-retainRatio) }

    // mutate some
    parents = parents.map { if (Random.nextDouble()<mutationChance) mutate(it, mutationSize, mutationStrength) else it }


    // fill empty spots by breeding
    val children = mutableSetOf<FloatArray>()
    while (children.size+parents.size<generation.size) {
        val p1 = parents.random()
        val p2 = parents.random()
        if (p1 != p2) {
            children.add(breed(Pair(p1, p2)))
        }
    }

    return EvolveResult(parents+children, statsResult)
}

private fun mutate(network: FloatArray, mutationSize: Double, mutationStrength: Double): FloatArray {
    val m = (network.size*mutationSize).toInt() % network.size

    val indexes = mutableSetOf<Int>()
    while (indexes.size < m) {
        indexes.add(Random.nextInt(network.size-1))
    }

    val res = network.clone()
    indexes.forEach { res[it] *= Random.nextDouble(mutationStrength).toFloat() }
    return res
}

fun createPopulation(weightsSize: Int, count: Int, weightRange: Float = 20f) = (0 until count).map { FloatArray(weightsSize) { Random.nextFloat() * weightRange}}

private fun breed(parents: Pair<FloatArray, FloatArray>): FloatArray {
    val pivot = Random.nextInt(Math.min(parents.first.size, parents.second.size))
    return (parents.first.take(pivot) + parents.second.drop(pivot)).toFloatArray()
}
