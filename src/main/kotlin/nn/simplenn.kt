package nn

import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.ops.transforms.Transforms


class SimpleNN {

    private val layers: List<INDArray>

    constructor(model: Array<Array<FloatArray>>) {
        layers = model.map { Nd4j.create(it) }
    }

    constructor(model: List<INDArray>) {
        layers = model
    }

    companion object {
        fun random(layersSize: LongArray, weightRange: Float = 100f): SimpleNN  {
            val layerConfig : List<INDArray> = (1 until layersSize.size).map {
                val input = layersSize[it-1] + 1 //add bias
                val output = layersSize[it]
                Nd4j.rand(intArrayOf(input.toInt(), output.toInt())).mul(2*weightRange).sub(weightRange)
            }
            return SimpleNN(layerConfig)
        }
    }

    constructor(data: Pair<LongArray, FloatArray>) {
        var idx = 0L

        layers = (1 until data.first.size).map {i ->
            val input = data.first[i-1] + 1
            val output = data.first[i]

            val sliceRange = IntRange(idx.toInt(), (idx+(input*output)-1).toInt())
            val layerWeights = data.second.sliceArray(sliceRange)
            idx += input*output
            Nd4j.create(layerWeights, intArrayOf(input.toInt(), output.toInt()))
        }
    }


    fun compute(input: FloatArray): DoubleArray {
        var res = Nd4j.create(input).reshape(input.size.toLong(),1)
        for (layer in layers) {
            res = layer.mulColumnVector(Nd4j.appendBias(res))
            res = res.sum(0)
            res = Transforms.sigmoid(res)
        }
        return res.data().asDouble()
    }

    fun serialize() : Pair<LongArray, FloatArray> {
        val layerSize = layers.map { it.size(0)-1 }.toLongArray() + longArrayOf(layers.last().size(1))
        val data : FloatArray = layers.flatMap { it.data().asFloat().asIterable() }.toFloatArray()
        return Pair(layerSize, data)
    }

    override fun toString(): String {
        return super.toString() + '\n' + layers
    }
}
