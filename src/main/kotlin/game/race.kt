package game

import java.awt.Point
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class RacerInput(val speed: Int, val lidarDistances: List<Double>)
enum class RacerOutputAction {
    TURN_RIGHT,
    TURN_LEFT,
    ACCELERATE,
    DECELERATE;
}

interface Racer {
    fun run(input: RacerInput): Set<RacerOutputAction>
}

class RacerCar() {
    var speed: Int = 0
    var direction: Double = 0.0
    var position: Point = Point(0,0)
    var dead = false
    var inactive = false

    var distanceTraveled = 0
    var timeTraveled = 0
}


class Race {

    private val trackMap: Array<BooleanArray>
    private val startingPoint: Point
    private var tickCount = 0

    val racerCars = mutableMapOf<Racer, RacerCar>()
    val lidarCache: MutableMap<Pair<Point, Double>, List<Double>>

    constructor(trackProfile: BufferedImage, startingPoint: Point, lidarCache: MutableMap<Pair<Point, Double>, List<Double>> = ConcurrentHashMap()) {
        trackMap = Array(trackProfile.height) { BooleanArray(trackProfile.width) }
        for (x in 0 until trackProfile.width) {
            for (y in 0 until trackProfile.height) {
                trackMap[y][x] = trackProfile.getRGB(x,y)!=0
            }
        }

        if (!trackMap[startingPoint.y][startingPoint.x]) {
            throw IllegalArgumentException("Starting point has to be on the track")
        }
        this.startingPoint = startingPoint
        this.lidarCache = lidarCache
    }

    fun registerRacer(racer: Racer) {
        val car = RacerCar()
        car.position = startingPoint
        racerCars[racer] = car
    }

    private fun findWall(startPoint: Point, angle: Double) : Point {
        val widthRange = (0 until trackMap[0].size)
        val heightRange = (0 until trackMap.size)

        if (!widthRange.contains(startPoint.x) || !heightRange.contains(startPoint.y) || !trackMap[startPoint.y][startPoint.x]) {
            throw IllegalArgumentException("Point has to be on the race track")
        }

        val xg = cos(angle)
        val yg = sin(angle)
        return (0..Math.max(trackMap.size, trackMap[0].size)).map { Point((startPoint.x + it * xg).toInt(), (startPoint.y + it * yg).toInt()) }.takeWhile {
            heightRange.contains(it.y) && widthRange.contains(it.x) && trackMap[it.y][it.x]
        }.last()
    }

    fun getRacerScore(racer: Racer): Double {
        return racerCars[racer]?.let {
            4 * it.distanceTraveled.toDouble() - it.timeTraveled - 2 * (tickCount - it.timeTraveled)
        } ?: 0.0
    }


    fun tick(ignoreInactive: Boolean = false): Boolean {
        val carsToEvaluate: () -> Map<Racer, RacerCar> = {
            racerCars.filterValues { !it.dead && (!ignoreInactive || !it.inactive) }
        }

        carsToEvaluate().entries.parallelStream().forEach { (racer, car) ->
            val lidarDistances = lidarCache.computeIfAbsent(Pair(car.position, car.direction)) {
                doubleArrayOf(-PI /2 , -PI /4, 0.0, PI /4 , PI /2).map { it + car.direction }.map { angle ->
                    findWall(car.position, angle).distance(car.position)
                }
            }

            val input = RacerInput(car.speed, lidarDistances)
            val actions = racer.run(input)

            for (action in actions) {
                val turnStep = 0.1
                val speedStep = 2
                when (action) {
                    RacerOutputAction.ACCELERATE -> car.speed = Math.min(200, car.speed + speedStep)
                    RacerOutputAction.DECELERATE -> car.speed = Math.max(0, car.speed - speedStep)
                    RacerOutputAction.TURN_LEFT -> car.direction -= turnStep
                    RacerOutputAction.TURN_RIGHT -> car.direction += turnStep
                }
            }

            car.timeTraveled++
            car.inactive = false

            if (car.speed > 0) {
                val nextCarPos = car.position.let {
                    Point(it.x + (car.speed * cos(car.direction)).roundToInt(), it.y + (car.speed * sin(car.direction)).roundToInt())
                }

                car.distanceTraveled += nextCarPos.distance(car.position).toInt()

                val widthRange = (0 until trackMap[0].size)
                val heightRange = (0 until trackMap.size)

                if (!widthRange.contains(nextCarPos.x) || !heightRange.contains(nextCarPos.y) || !trackMap[nextCarPos.y][nextCarPos.x]) {
                    car.dead = true
                }
                car.position = nextCarPos

            } else {
                car.inactive = actions.isEmpty() || actions.containsAll(listOf(RacerOutputAction.TURN_LEFT, RacerOutputAction.TURN_RIGHT)) || actions.containsAll(listOf(RacerOutputAction.ACCELERATE, RacerOutputAction.DECELERATE))
            }

        }

        tickCount++
        return carsToEvaluate().isNotEmpty()
    }

}

