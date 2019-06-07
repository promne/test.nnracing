package ui

import game.Race
import game.RacerCar
import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Point
import java.awt.Transparency
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JPanel

class RaceTrackPanel : JPanel {

    private val carImage: BufferedImage = ImageIO.read(File("car.png")).let {
        val resized = BufferedImage(it.width/2, it.height/2, BufferedImage.TYPE_INT_ARGB)
        resized.graphics.drawImage(it,0,0,it.width/2,it.height/2, null)
        resized
    }

    var racetrackImg: BufferedImage
    set(value) {
        cars = emptySet()
        field = value
    }

    var race: Race? = null
    var cars: Collection<RacerCar> = emptySet()
        set(value) {
            field = value
            repaint()
        }


    constructor(racetrackImg: BufferedImage) : super() {
        this.racetrackImg = racetrackImg
    }

    fun toImgPoint(pos : Point) = Point((pos.x * (1.0 * racetrackImg.width / this.width)).toInt(), (pos.y * (1.0 * racetrackImg.height / this.height)).toInt())

    private fun toPanelPoint(pos : Point) = Point((pos.x * (1.0 * this.width / racetrackImg.width)).toInt(), (pos.y * (1.0 * this.height / racetrackImg.height)).toInt())

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.drawImage(racetrackImg, 0,0, this.width, this.height, null)

        //draw cars
        for (car in cars) {
            val alpha = if (car.dead) .15f else 1f
            val rotatedCarImage = rotateImage(carImage, car.direction, alpha)
            val pos = toPanelPoint(car.position)
            g.drawImage(rotatedCarImage, pos.x - rotatedCarImage.width/2, pos.y - rotatedCarImage.height/2, this)
        }
    }

    private fun rotateImage(img: BufferedImage, angle: Double, alpha: Float = 0f): BufferedImage {
        val sin = Math.abs(Math.sin(angle))
        val cos = Math.abs(Math.cos(angle))
        val w = img.width
        val h = img.height
        val newWidth = Math.floor(w * cos + h * sin).toInt()
        val newHeight = Math.floor(h * cos + w * sin).toInt()

        val rotated = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = rotated.createGraphics()
        val at = AffineTransform()
        at.translate(((newWidth - w) / 2).toDouble(), ((newHeight - h) / 2).toDouble())

        val x = w / 2
        val y = h / 2

        at.rotate(angle, x.toDouble(), y.toDouble())

        g2d.composite = AlphaComposite.getInstance( AlphaComposite.SRC_OVER, alpha)

        g2d.transform = at
        g2d.drawImage(img, 0, 0, this)
        g2d.dispose()

        return rotated
    }

}