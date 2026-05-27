@file:Suppress("UnstableApiUsage")

plugins {
    id("fabric-loom")
    id("dev.kikugie.postprocess.jsonlang")
    id("me.modmuss50.mod-publish-plugin")
}

version = "${property("mod.version")}-${property("deps.minecraft")}-fabric"
base.archivesName = property("mod.id") as String

jsonlang {
    languageDirectories = listOf("assets/${property("mod.id")}/lang")
    prettyPrint = true
}

repositories {
    mavenLocal()
    maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
    maven("https://maven.terraformersmc.com/") { name = "ModMenu" }
    maven("https://maven.nucleoid.xyz/") { name = "Placeholder API" }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("deps.minecraft")}")
    mappings(loom.layered {
        officialMojangMappings()
        if (hasProperty("deps.parchment"))
            parchment("org.parchmentmc.data:parchment-${property("deps.parchment")}@zip")
    })
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric-loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric-api")}")
    modImplementation("com.terraformersmc:modmenu:${property("deps.mod_menu")}")
}

fabricApi {
    configureDataGeneration() {
        outputDirectory = file("$rootDir/src/main/generated")
        client = true
    }
}

tasks {
    processResources {
        exclude("**/neoforge.mods.toml", "**/mods.toml", "**/pack.mcmeta")
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(remapJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}

java {
    withSourcesJar()
    val javaCompat = if (stonecutter.eval(stonecutter.current.version, ">=1.20.5")) JavaVersion.VERSION_21
    else if (stonecutter.eval(stonecutter.current.version, ">=1.18")) JavaVersion.VERSION_17
    else if (stonecutter.eval(stonecutter.current.version, ">=1.17")) JavaVersion.VERSION_16
    else JavaVersion.VERSION_1_8
    sourceCompatibility = javaCompat
    targetCompatibility = javaCompat
}

val supportedMinecraftVersions: List<String> = com.google.common.collect.ImmutableList.builder<String>()
    .addAll(
        (property("publish.additionalVersions") as String?)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList())
    .add(stonecutter.current.version)
    .build()

tasks.named<ProcessResources>("processResources") {
    val props = HashMap<String, String>().apply {
        this["mod_id"] = project.property("mod.id") as String
        this["mod_name"] = project.property("mod.name") as String
        this["mod_description"] = project.property("mod.description") as String
        this["mod_version"] = project.property("mod.version") as String
        this["mod_authors"] = project.property("mod.authors") as String
        this["mod_repo_url"] = project.property("mod.repo_url") as String
        this["mod_license"] = project.property("mod.license") as String
        this["mod_logo"] = project.property("mod.logo") as String
        this["minecraft_version_range_fabric"] = project.property("deps.minecraft_version_range") as String
    }

    filesMatching(listOf("fabric.mod.json")) {
        expand(props)
    }
}

publishMods {
    file = tasks.remapJar.map { it.archiveFile.get() }
    additionalFiles.from(tasks.remapSourcesJar.map { it.archiveFile.get() })

    val modVersion = property("mod.version") as String
    type = if (modVersion.contains("alpha")) ALPHA
    else if (modVersion.contains("beta")) BETA
    else STABLE

    displayName = "${property("mod.name")} $modVersion for ${stonecutter.current.version} Fabric"
    version = "${modVersion}-${property("deps.minecraft")}-fabric"
    changelog = provider { rootProject.file("CHANGELOG.md").readText() }
    modLoaders.add("fabric")

    modrinth {
        projectId = property("publish.modrinth") as String
        accessToken = env.MODRINTH_API_KEY.orNull()
        minecraftVersions.addAll(supportedMinecraftVersions)
    }

    curseforge {
        projectId = property("publish.curseforge") as String
        accessToken = env.CURSEFORGE_API_KEY.orNull()
        minecraftVersions.addAll(supportedMinecraftVersions)
    }
}
