plugins {
    id("java")
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.ezzenix.mcverify"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
}

gradlePlugin {
    plugins {
        create("mcverify") {
            id = "com.ezzenix.mcverify"
            implementationClass = "com.ezzenix.mcverify.plugin.McVerify"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "pages"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
