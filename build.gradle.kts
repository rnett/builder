import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL
import org.gradle.internal.impldep.com.google.gson.JsonParser
import org.jetbrains.kotlin.gradle.dsl.Coroutines


plugins {
    java
    kotlin("jvm") version "1.2.70"
    application
}

apply {
    plugin("war")
}



(tasks["war"] as War).archiveName = "ROOT.war"
(tasks["war"] as War).webXml = File("web.xml")

application {
    mainClassName = "main.SiteKt"
    applicationDefaultJvmArgs = listOf("‑Djava.library.path=or-tools_VisualStudio2017-64bit_v6.5.4527/lib")
}

configure<ApplicationPluginConvention> {
    mainClassName = "site.SiteKt"
}

group = "com.rnett.ligraph.eve"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    jcenter()
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://dl.bintray.com/kotlin/exposed")

    flatDir {
        dir("or-tools/lib")
    }

}

fun getNewestCommit(gitURL: String, default: String = ""): String {
    return try {
        URL("https://api.github.com/repos/$gitURL/commits").readText()
                .substringAfter("\"sha\":\"").substringBefore("\",").substring(0, 10)
    } catch (e: Exception) {
        default
    }
}

val kframe_version = getNewestCommit("rnett/kframe", "a1ce030345")
val kframemateralize_version = getNewestCommit("rnett/kframe-materalize", "52a431af5a")

val sde_version = getNewestCommit("rnett/sde", "b3bf831b59")
val dotlansde_version = getNewestCommit("rnett/dotlanmaps-sde", "4c7bb7c530")

val fleeteye_version = getNewestCommit("rnett/fleeteye", "ad1f3112b7")
val contracts_version = getNewestCommit("rnett/contracts", "cab574e930")


dependencies {
    compile(kotlin("stdlib-jdk8"))

    implementation("io.ktor:ktor-client-core:0.9.3")
    implementation("io.ktor:ktor-client-apache:0.9.3")

    implementation("io.ktor:ktor-server-servlet:0.9.3")

    implementation("", "com.google.ortools")

    testCompile("junit", "junit", "4.12")

    implementation("com.github.rnett:sde:$sde_version") { isForce = true }
    implementation("com.github.rnett:kframe:$kframe_version") { isForce = true }
    implementation("com.github.rnett:kframe-materalize:$kframemateralize_version")
    implementation("com.github.rnett:fleeteye:$fleeteye_version")
    implementation("com.github.rnett:dotlanmaps-kframe:9088cd58dc")
    implementation("com.github.rnett:dotlanmaps-sde:$dotlansde_version")
    implementation("com.github.rnett:contracts:$contracts_version")
    //compile("com.github.rnett:core:1a5bfbcab6") { isForce = true }

}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
kotlin {
    experimental.coroutines = Coroutines.ENABLE
}