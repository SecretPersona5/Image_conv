package filters

class Filter (
    val kernel: Array<DoubleArray>,
    val factor: Double = 1.0,
    val bias: Double = 0.0
)