package conv
import java.io.File

object TimeLogger {
    private val csvFile = File("src/main/resources/img/timing_log.csv")
    private val header = listOf("Image", "Load(ms)", "ToGray(ms)", "Filter(ms)", "Save(ms)", "Total(ms)")

    init {
        if (!csvFile.exists()) {
            csvFile.writeText(header.joinToString(",") + "\n")
        }
    }

    fun log(imageName: String, tLoad: Long, tGray: Long, tFilter: Long, tSave: Long, tTotal: Long) {
        val line = listOf(imageName, tLoad, tGray, tFilter, tSave, tTotal).joinToString(",")
        csvFile.appendText("$line\n")
    }

}
