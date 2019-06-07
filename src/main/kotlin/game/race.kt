package game

import java.awt.Point
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random

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
    private val startingAngle: Double
    private var tickCount = 0

    val racerCars = mutableMapOf<Racer, RacerCar>()
    val lidarCache: MutableMap<Pair<Point, Double>, List<Double>>

    companion object {
        fun getRandomStart(trackProfile: BufferedImage) : Pair<Point, Double> {
            var angle = Double.NaN
            var startPoint = Point(0, 0)

            while (angle.isNaN()) {
                while (trackProfile.getRGB(startPoint.x, startPoint.y)==0) {
                    startPoint = Point(Random.nextInt(trackProfile.width), Random.nextInt(trackProfile.height))
                }

                val (startLine, _) = findMinMaxRay(trackProfile, startPoint)

                angle = atan(1.0 * abs(startLine.first.x - startLine.second.x) / abs(startLine.first.y - startLine.second.y))
            }

            return Pair(startPoint, angle)
        }

        private fun findMinMaxRay(trackProfile: BufferedImage, imgPoint: Point) : Pair<Pair<Point, Point>, Pair<Point, Point>> {
            val raysCount = 100

            val color = trackProfile.getRGB(imgPoint.x, imgPoint.y)
            val widthRange = IntRange(0, trackProfile.width-1)
            val heightRange = IntRange(0, trackProfile.height-1)


            return (0..raysCount).map { ray ->
                val angle = (PI / raysCount) * ray

                val calcEndpoint: (Double) -> Point = {rayAngle ->
                    val xg = cos(rayAngle)
                    val yg = sin(rayAngle)
                    (0..Math.max(trackProfile.width, trackProfile.height)).map { Point((imgPoint.x + it * xg).toInt(), (imgPoint.y + it * yg).toInt()) }.takeWhile {
                        heightRange.contains(it.y) && widthRange.contains(it.x) && trackProfile.getRGB(it.x, it.y) == (color)
                    }.lastOrNull()?:imgPoint
                }

                Pair(calcEndpoint(angle), calcEndpoint(angle + PI))
            }.fold(Pair(Pair(Point(0, 0), Point(trackProfile.width, trackProfile.height)), Pair(Point(0,0), Point(0,0)))) { curRes, ray ->
                val rayLength = ray.first.distance(ray.second)
                if (curRes.first.first.distance(curRes.first.second) > rayLength) {
                    curRes.copy(first = ray)
                } else if (curRes.second.first.distance(curRes.second.second) < rayLength) {
                    curRes.copy(second = ray)
                } else {
                    curRes
                }
            }
        }

    }

    constructor(trackProfile: BufferedImage, startingPoint: Point, startingAngle: Double, lidarCache: MutableMap<Pair<Point, Double>, List<Double>> = ConcurrentHashMap()) {
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
        this.startingAngle = startingAngle
        this.lidarCache = lidarCache
    }

    fun registerRacer(racer: Racer) {
        val car = RacerCar()
        car.position = startingPoint
        car.direction = startingAngle
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
        }.lastOrNull()?:startPoint
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

