
import game.Race
import game.Racer
import game.RacerInput
import game.RacerOutputAction
import genetic.EvolveResultStatsType
import genetic.createPopulation
import genetic.evolve
import nn.SimpleNN
import ui.HistoryChartPanel
import ui.RaceTrackPanel
import java.awt.EventQueue
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.*


class GameFrame : JFrame() {

    val populationSize = 100
    val gameLength = 1000

    val generationHistoryData: MutableList<Array<Number>> = mutableListOf()

    val racePanel: RaceTrackPanel
    val generationHistoryPanel: HistoryChartPanel
    val retainSamplesSlider: JSlider
    val mutationChanceSlider: JSlider
    val mutationSizeSlider: JSlider
    val mutationStrengthSlider: JSlider

    private var bestAgents = emptyList<NNCarRacerAgent>()
    var race: Race? = null

    init {
        val img = ImageIO.read(File("track1.png"))

        title = "Track"

        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        setSize(600, (600.0 * img.height/img.width).toInt())
        setLocationRelativeTo(null)

        racePanel = RaceTrackPanel(img)
        racePanel.addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                race = Race(img, racePanel.toImgPoint(e.point))
                val topAgents = bestAgents.take(10)
                race?.let {r ->
                    topAgents.forEach(r::registerRacer)
                    racePanel.cars = topAgents.mapNotNull { r.racerCars[it] }
                }
            }
        })

        val controlsPanel = JPanel()
        controlsPanel.layout = BoxLayout(controlsPanel, BoxLayout.Y_AXIS)

        retainSamplesSlider = JSlider(JSlider.HORIZONTAL, 1, 99, 60)
        controlsPanel.add(JLabel("Retain for next gen %"))
        controlsPanel.add(retainSamplesSlider)

        mutationChanceSlider = JSlider(JSlider.HORIZONTAL, 1, 99, 10)
        controlsPanel.add(JLabel("Mutation chance %"))
        controlsPanel.add(mutationChanceSlider)

        mutationSizeSlider = JSlider(JSlider.HORIZONTAL, 1, 99, 5)
        controlsPanel.add(JLabel("Mutation size %"))
        controlsPanel.add(mutationSizeSlider)

        mutationStrengthSlider = JSlider(JSlider.HORIZONTAL, 1, 100, 20)
        controlsPanel.add(JLabel("Mutation strength"))
        controlsPanel.add(mutationStrengthSlider)

        val controlGameSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, racePanel, controlsPanel)


        generationHistoryPanel = HistoryChartPanel()
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, controlGameSplitPane, generationHistoryPanel)
        splitPane.dividerLocation = 400

        add(splitPane)

        let {
            // schedule clocks
            val repeatedTask = object : TimerTask() {
                override fun run() {
                    race?.let {
                        it.tick()
                        repaint()
                    }
                }
            }
            val timer = java.util.Timer("Timer")
            timer.scheduleAtFixedRate(repeatedTask, 100, 30)
        }
        Thread() { trainNN() }.start()
    }

    private fun trainNN() {
        val inputCount = 6L // speed + 5 distances

        // input and output layers only, no hidden
        val networkShape = longArrayOf(inputCount, RacerOutputAction.values().size.toLong())
        val networkLength = SimpleNN.random(networkShape).serialize().second.size

        var generation = createPopulation(networkLength, populationSize, 0.5f)

        //seed some
//        generation += (0..populationSize).flatMap {
//    //    no hidden layer
//            listOf<DoubleArray>(
//                    doubleArrayOf(1.7370898E-5, 4.2557062E-4, 0.0034165813, 0.045387335, 0.5876973, 0.043038636, 0.0020814163, 0.17653225, 0.27403617, 0.1484007, 2.2714784E-4, 0.01813405, 0.038704015, 0.105946966, 2.0151422E-4, 1.1980557, 0.087518096, 0.73347956, 3.7347367, 0.015086938, 0.028544001, 0.0039473246, 0.06775831, 0.06708261),
//                    doubleArrayOf(1.87054E-5, 0.001672149, 9.303332E-6, 0.07872352, 0.50674695, 0.048982535, 0.02277285, 0.25493857, 0.22062837, 0.1484007, 0.015714832, 0.012890826, 0.038704015, 0.076925255, 0.038581066, 0.686076, 0.10369444, 0.028881637, 3.7347367, 0.007876245, 0.007213634, 0.0028945187, 0.004625181, 0.06708261),
//                    doubleArrayOf(1.87054E-5, 0.001672149, 9.303332E-6, 0.07872352, 0.50674695, 0.048982535, 0.02277285, 0.25493857, 0.22062837, 0.1484007, 0.015714832, 0.0016742615, 0.038704015, 0.076925255, 0.004024485, 0.686076, 0.10369444, 0.028881637, 3.7347367, 0.007876245, 0.007213634, 0.0028945187, 0.004625181, 0.06708261)
//            )
//        }.map{ nn -> nn.map { it.toFloat() }.toFloatArray() }.take(populationSize)

        val evaluateGeneration = { nn:Collection<SimpleNN> ->
            val agents = nn.map { Pair(it, NNCarRacerAgent(it)) }.toMap()
            val nnScore = mutableMapOf<SimpleNN, Double>()


            val racetrackImg = ImageIO.read(File("track1.png"))
            val lidarCache = ConcurrentHashMap<Pair<Point, Double>, List<Double>>()
            val startPoints = listOf(Point(154,427), Point(291,276), Point(326,90), Point(383,405))

            var anyHadPoints = true
            for (point in startPoints) {
                if (anyHadPoints) {
                    val game = Race(racetrackImg, point, lidarCache)
                    agents.values.forEach(game::registerRacer)

                    var gameTime = 0
                    do {
                        game.tick(true)
                    } while (++gameTime < gameLength)

                    agents.forEach{ (nn, racer) ->
                        nnScore[nn] = nnScore.getOrDefault(nn, 0.0) + game.getRacerScore(racer)
                    }
                    anyHadPoints = nnScore.values.any { it > 0 }
                }
            }

            nnScore.toList()
        }


        val startTime = System.currentTimeMillis()
        var lastStatsTime = 0L
        var generationCount = 0
        while(true) {
            val evResult = evolve(networkShape, generation, evaluateGeneration, retainSamplesSlider.value/100.0, mutationChanceSlider.value/100.0, mutationSizeSlider.value/100.0, mutationStrengthSlider.value/10.0)
            generation = evResult.generation
            generationCount++

            if (lastStatsTime + 30000 < System.currentTimeMillis()) {
                println("-------------------")
                println("Generation $generationCount")
                println("Time elapsed ${(System.currentTimeMillis()-startTime)/1000}")
                evResult.stats.forEach(::println)
                println()
                lastStatsTime = System.currentTimeMillis()
            }

            val currentGenMetrics = arrayOf(EvolveResultStatsType.MIN, EvolveResultStatsType.MAX, EvolveResultStatsType.AVG).mapNotNull { evResult.stats[it] }.filterIsInstance<Number>().toTypedArray()

            // ignore first idiot generations
            if (generationHistoryPanel.data.isNotEmpty() || currentGenMetrics.toSet().size > 1) {
                generationHistoryData += currentGenMetrics
                generationHistoryPanel.data = generationHistoryData.take(generationHistoryPanel.width * 5).toTypedArray()
            }

            bestAgents = generation.take(5).map { weights -> NNCarRacerAgent(SimpleNN(Pair(networkShape, weights))) }
        }
    }

}


class NNCarRacerAgent(private val nn : SimpleNN) : Racer {

    constructor(shape: LongArray, weights: FloatArray) :  this(SimpleNN(Pair(shape, weights)))

    override fun run(input: RacerInput): Set<RacerOutputAction> {

        val nnInput = floatArrayOf(input.speed.toFloat()) + input.lidarDistances.map { it.toFloat() }.toFloatArray()
        val nnOutput = nn.compute(nnInput).map { it > 0.7 }.toBooleanArray() //arbitrary threshold

        val res = mutableSetOf<RacerOutputAction>()
        nnOutput.forEachIndexed { index, b ->
            if (b) res.add(RacerOutputAction.values()[index % RacerOutputAction.values().size])
        }
        return res
    }

    override fun toString(): String {
        return nn.toString()
    }
}

fun main() {
    EventQueue.invokeLater {
        val frame = GameFrame()
        frame.isVisible = true
    }
}
