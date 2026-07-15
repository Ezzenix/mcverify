# mcverify

Gradle plugin that automates testing different Minecraft versions for mod development by making sure it can start with every version and loader.

- Download and run Minecraft instances automatically.
- Starts client instances and monitors the logs to verify that it reaches the main menu.
- Automatically install other mods for testing compatibility.
- Run instances concurrently to accelerate testing.
- Run your mod in different versions of Minecraft than the one you have your project set up in.

---

## Install

```kotlin
pluginManagement {
    repositories {
        maven("https://ezzenix.github.io/mcverify")
    }
}

plugins {
    id("com.ezzenix.mcverify") version "0.1.0"
}
```

---

## Config
You can configure by adding `mcverify { }` in your `build.gradle.kts`.
<br>
You can add multiple configurations for your subprojects.

```kotlin
mcverify {
    // The mod loader you want to use.
    // "fabric", "neoforge", or "forge"
    loader = "fabric"

    // The minecraft version to use.
    version = "1.21"

    // You can use multiple versions like this:
    versionRange {
        start = "1.19.4"
        end = "1.20.2"
    }
    
    // Add a mod to be automatically installed. Finds matching version and downloads from Modrinth.
    // For Fabric 'modmenu' is already added by default.
    mod("projectId1")
    mod("projectId2")

    // (Optional) The mod file to copy. Defaults to output of task 'remapJar' or 'jar'.
    file = file()

    // (Optional) The delay before the client automatically closes after reaching main menu while testing, in milliseconds.
    // If less than 0 the client will not close automatically.
    // Defaults to 0.
    closeDelay = 0
    
    // (Optional) How many clients to run simultaneously when running testAllVersions.
    // Do not set too high or your computer will be sad.
    // If your have multiple configurations the highest value will be used.
    // Defaults to 1.
    workers = 1

    // (Optional) Set username to use.
    username = "Player"

    // (Optional) Server address to join automatically.
    serverAddress = "localhost"
}
```

---

All downloaded instances and files are stored in `%USER_HOME%\.mcverify`