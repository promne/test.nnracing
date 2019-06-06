package nn

import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.ops.transforms.Transforms
import org.nd4j.linalg.util.ArrayUtil


class SimpleNN {

    private val layers: List<INDArray>
    private val bias = Nd4j.create(doubleArrayOf(1.0))

    constructor(model: Array<Array<DoubleArray>>) {
        layers = model.map { Nd4j.create(it) }
    }

    constructor(model: List<INDArray>) {
        layers = model
    }

    companion object {
        fun random(layersSize: LongArray, weightRange: Double = 100.0): SimpleNN  {
            val layerConfig : List<INDArray> = (1 until layersSize.size).map {
                val input = layersSize[it-1] + 1 //add bias
                val output = layersSize[it]
                Nd4j.rand(DataType.DOUBLE, output, input).mul(2 * weightRange).sub(weightRange)
            }
            return SimpleNN(layerConfig)
        }
    }

    constructor(data: Pair<LongArray, DoubleArray>) {
        var idx = 0L

        layers = (1 until data.first.size).map {i ->
            val input = data.first[i-1] + 1
            val output = data.first[i]

            val sliceRange = IntRange(idx.toInt(), (idx+(input*output)-1).toInt())
            val layerWeights = data.second.sliceArray(sliceRange)
            idx += input*output
            Nd4j.create(layerWeights, intArrayOf(output.toInt(), input.toInt()))
        }
    }


    fun compute(input: DoubleArray): DoubleArray {
        var res = Nd4j.create(input)
        for (layer in layers) {
            res = Nd4j.concat(0, res, bias)
            res = layer.mmul(res)
            res = Transforms.sigmoid(res)
        }
        return res.data().asDouble()
    }

    fun serialize() : Pair<LongArray, DoubleArray> {
        val layersSize = layers.map { it.size(1) - 1 }.toLongArray() + longArrayOf(layers.last().size(0))
        val data = Nd4j.toFlattened('c', layers).data().asDouble()
        return Pair(layersSize, data)
    }

    override fun toString(): String {
        return super.toString() + '\n' + layers
    }
}
