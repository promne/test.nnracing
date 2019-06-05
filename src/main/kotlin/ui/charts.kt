package ui

import java.awt.*
import javax.swing.JFrame
import javax.swing.JPanel
import java.awt.Color



class HistoryChartPanel : JPanel() {

    var data: Array<Array<Number>> = emptyArray()
        set(value) {
            field = value
            repaint()
        }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        if (data.size < 2) {
            return
        }

        val colorPalette = (0 until data[0].size).map {
            Color.getHSBColor((1.0 * it / data[0].size).toFloat(), 1.0f, 1.0f)
        }


        val maxValue = data.mapNotNull { it.maxBy { values -> values.toDouble() }?.toDouble() }.maxBy { it.toDouble() } ?: 0.0
        val minValue = data.mapNotNull { it.minBy { values -> values.toDouble() }?.toDouble() }.minBy { it.toDouble() } ?: 0.0
        val valueScale = this.height / (maxValue - minValue)

        val dataToY: (Number) -> Int = {
            this.height - ((it.toDouble() - minValue) * valueScale).toInt()
        }

        val step = 1.0 * this.width / data.size

        (g as Graphics2D).stroke = BasicStroke(2f)
        for (timeIdx in 1 until data.size) {
            for ((index, value) in data[timeIdx].withIndex()) {
                g.color = colorPalette[index]

                val previousY = dataToY(data[timeIdx-1][index])
                val currentY = dataToY(value)

                g.drawLine(((timeIdx-1)*step).toInt(), previousY, (timeIdx*step).toInt(), currentY)
            }
        }
    }

}


fun main() {
    EventQueue.invokeLater {
        val frame = object : JFrame() {
            init {
                title = "Data chart"
                defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                setSize(600, 300)
                setLocationRelativeTo(null)

                val panel = HistoryChartPanel()
                add(panel)

                val d = (1..10).map {step -> (0..5).map { (it % step) }.filterIsInstance<Number>().toTypedArray() }.toTypedArray()
                panel.data = d
            }
        }
        frame.isVisible = true
    }
}
