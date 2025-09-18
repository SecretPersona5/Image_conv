# Image_conv – Kotlin Image Convolution

## Project Overview
**Image_conv** is a Kotlin-based image convolution project that demonstrates and benchmarks multiple convolution strategies using OpenCV.  
It applies various 3×3 and 5×5 filters (e.g., blur, Gaussian blur, sharpen, edge detection, etc.) to images using both sequential and parallel approaches.

## Quick Start

### Build the Project
Make sure you have **Java (JDK 8 or higher)** and Gradle installed.

```bash
git clone https://github.com/SecretPersona5/Image_conv.git
cd Image_conv
./gradlew build

```
I would recommend simply shift + f10 to launch the program

This will compile the Kotlin source and download the required OpenCV native libraries.

---

### Run Interactively
Launch the program in **interactive mode** (with a console menu):

```bash
./gradlew run
# or:
./gradlew run --args="-i"
```

In this mode, you will be prompted to:
- Select an input image
- Choose a convolution mode (`seq`, `row`, `col`, `grid`, `pix`)
- Pick a filter (e.g., `blur_3x3`, `sharpen`, etc.)
- Optionally process a whole directory (pipeline mode)

---

### Process a Single Image via CLI
Run a single image with a specific mode:

```bash
./gradlew run --args="<mode> <inputImagePath> [gridBlockSize]"
```

Example (row-parallel convolution):

```bash
./gradlew run --args="row path/to/image.jpg"
```

For **grid mode**, add a block size:

```bash
./gradlew run --args="grid path/to/image.jpg 64"
```

---

### Pipeline Mode (Batch Processing)
Process **all images in a directory** concurrently:

```bash
./gradlew run --args="pipe <inputDir> <outputDir> <mode> [blockSize] [convWorkers] [saveWorkers] [capacity]"
```

Example:

```bash
./gradlew run --args="pipe ./photos ./photos_out grid 128 8 1 8"
```

Output images will be saved in `<outputDir>`.

---

## Benchmarks

### Microbenchmark (JMH)
Run quick JMH benchmarks:

```bash
./gradlew jmhQuick
```

Results are saved to:

```
build/bench/jmh_quick.csv
```

It measures performance for different:
- Modes (`seq`, `row`, `col`, `grid`, `pix`)
- Filters (e.g., `gaussian_blur_3x3`)
- Image sizes
- Thread counts

---

### Macrobenchmark (Pipeline)
Run full pipeline benchmarks:

```bash
./gradlew macroBench
```

Results are saved to:

```
out/benchmarks/
```

Each CSV contains throughput and timing results for all strategies across multiple runs.

---

## Project Structure
```
src/main/kotlin/
  Main.kt              # Entry point
  Sequential.kt        # Sequential convolution
  RowParallel.kt       # Row-parallel convolution
  ColParallel.kt       # Column-parallel convolution
  GridParallel.kt      # Grid-based parallel convolution
  PixelParallel.kt     # Pixel-level parallel convolution
  Pipeline.kt          # Batch processing pipeline
  Filters.kt           # Filter definitions
  Logger.kt            # Timing logger
```

---

## Requirements
- **JDK 8+**
- **Gradle** (wrapper included)
- **OpenCV** (downloaded automatically via `nu.pattern.OpenCV`)

---

## License
MIT
