plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    application
    id("me.champeau.jmh") version "0.7.3"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<Test> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.openpnp:opencv:4.7.0-0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    jmh("org.openpnp:opencv:4.7.0-0")
    jmh("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

}

application {
    mainClass.set("Benchmark.BenchMainKt")
}

jmh {
    warmupIterations.set(2)
    iterations.set(5)
    fork.set(1)
    resultFormat.set("CSV")
    resultsFile.set(layout.buildDirectory.file("bench/jmh.csv").get().asFile)
    includes.set(listOf("bench\\.ConvBench"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        doFirst {
            layout.buildDirectory.dir("tmp/test").get().asFile.mkdirs()
        }
    }


    kotlin {
        sourceSets.main {
            kotlin.srcDirs("src/main/kotlin")
        }
        jvmToolchain(21)
    }
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

abstract class JmhQuickTask : DefaultTask() {

    @get:javax.inject.Inject
    abstract val execOps: ExecOperations

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val libsDir = project.layout.buildDirectory.dir("libs").get().asFile
        val jmhJar = libsDir.listFiles { _, name -> name.endsWith("-jmh.jar") }
            ?.firstOrNull() ?: throw GradleException("JMH jar не найден в $libsDir")

        val outCsv = project.layout.buildDirectory.file("bench/jmh_quick.csv").get().asFile
        outCsv.parentFile.mkdirs()

        val javaHome = System.getenv("JAVA_HOME")
        val javaBin = if (javaHome.isNullOrBlank()) "java" else "$javaHome/bin/java"

        execOps.exec {
            commandLine(
                javaBin, "-jar", jmhJar.absolutePath,
                // быстрые окна
                "-wi","1","-i","2","-f","0",
                "-w","200ms","-r","200ms",
                "-tu","ms","-bm","avgt",
                "-p","mode=seq",
                "-p","mode=row",
                "-p","mode=col",
                "-p","mode=grid",
                "-p","mode=pix",
                "-p","size=256",
                "-p","filterName=gaussian_blur_3x3",
                "-p","blockSize=64",
                "-p","blockSize=128",
                "-p","blockSize=256",
                "-p","xWorkers=1",
                "-p","xWorkers=2",
                "-p","xWorkers=4",
                "-p","xWorkers=8",
                "-rf","csv","-rff", outCsv.absolutePath
            )
        }
        println("JMH quick CSV -> ${outCsv.absolutePath}")
    }
}

tasks.register("jmhQuick", JmhQuickTask::class.java) {
    dependsOn("jmhJar")
}