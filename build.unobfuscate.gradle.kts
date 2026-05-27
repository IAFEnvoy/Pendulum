import java.util.LinkedList

plugins {
    id("net.neoforged.moddev")
    id("dev.kikugie.postprocess.jsonlang")
    id("me.modmuss50.mod-publish-plugin")
}

version = "${property("mod.version")}-${property("deps.minecraft")}-universal"
base.archivesName = property("mod.id") as String

jsonlang {
    languageDirectories = listOf("assets/${property("mod.id")}/lang")
    prettyPrint = true
}

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.terraformersmc.com/") { name = "ModMenu" }
}

dependencies {
    compileOnly("net.fabricmc:fabric-loader:${property("deps.fabric-loader")}")
    compileOnly("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric-api")}")
    compileOnly("com.terraformersmc:modmenu:${property("deps.mod_menu")}")
}

neoForge {
    version = property("deps.neoforge") as String
    validateAccessTransformers = true

    runs {
        register("client") {
            gameDirectory = file("run/")
            client()
        }
        register("server") {
            gameDirectory = file("run/")
            server()
        }
    }

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets["main"])
        }
    }
    sourceSets["main"].resources.srcDir("src/main/generated")
}

tasks {
    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(jar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

val supportedMinecraftVersions = LinkedList<String>()
supportedMinecraftVersions.addAll(
    (property("publish.additionalVersions") as String?)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList())
supportedMinecraftVersions.add(stonecutter.current.version)

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
        this["minecraft_version_range_neoforge"] = project.property("deps.minecraft_version_range_neoforge") as String
        this["minecraft_version_range_fabric"] = project.property("deps.minecraft_version_range_fabric") as String
    }

    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
        expand(props)
    }
}

publishMods {
    file = tasks.jar.map { it.archiveFile.get() }
    additionalFiles.from(tasks.named<org.gradle.jvm.tasks.Jar>("sourcesJar").map { it.archiveFile.get() })

    val modVersion = property("mod.version") as String
    type = if (modVersion.contains("alpha")) ALPHA
    else if (modVersion.contains("beta")) BETA
    else STABLE
    displayName = "${property("mod.name")} ${property("mod.version")} for ${stonecutter.current.version} Universal"
    version = "${property("mod.version")}-${property("deps.minecraft")}"
    changelog = provider { rootProject.file("CHANGELOG.md").readText() }
    modLoaders.addAll("neoforge", "fabric")

    modrinth {
        projectId = property("publish.modrinth") as String
        accessToken = env.MODRINTH_API_KEY.orNull()
        minecraftVersions.addAll(supportedMinecraftVersions)
        optional("cloth-config")
        optional("forge-config-api-port")
        optional("fabric-api")
    }

    curseforge {
        projectId = property("publish.curseforge") as String
        accessToken = env.CURSEFORGE_API_KEY.orNull()
        minecraftVersions.addAll(supportedMinecraftVersions)
        optional("cloth-config")
        optional("forge-config-api-port")
        optional("fabric-api")
    }
}
