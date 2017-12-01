allprojects {
    repositories {
        jcenter()
    }
}

buildscript {
    repositories {
        mavenCentral()
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-dev") }
        mavenLocal()
    }
    dependencies {
        classpath("org.jetbrains.intellij.plugins:inspection-plugin:0.1.1-SNAPSHOT")
    }
}

apply {
    plugin("org.jetbrains.intellij.inspections")
}