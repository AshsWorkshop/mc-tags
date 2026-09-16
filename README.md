# Tags

A utility for generating tag references from Minecraft mod loaders for use in multi-loader projects.

## Repositories

The transformers can be found in the following repositories:

<details open>

<summary>build.gradle</summary>

```groovy
repositories {
    maven {
        name = 'UUID'
        url = 'https://maven.uuid.gg/snapshots'
    }
    maven {
        name = 'Tags Github'
        url = 'https://maven.pkg.github.com/AshsWorkshop/mc-tags'
        // Credentials are required to pull from Github Packages (requires 'read:packages' scope)
        // See: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry#using-a-published-package
        credentials {
            username = '<GITHUB_USERNAME>'
            password = '<GITHUB_ACCESS_TOKEN>'
        }
    }
}
```

</details>

<details>

<summary>build.gradle.kts</summary>

```kotlin
repositories {
    maven {
        name = "UUID"
        // https://maven.uuid.gg/#/snapshots
        url = uri("https://maven.uuid.gg/snapshots")
    }
    maven {
        name = "Tags Github"
        url = uri("https://maven.pkg.github.com/AshsWorkshop/mc-tags")
        // Credentials are required to pull from Github Packages (requires 'read:packages' scope)
        // See: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry#using-a-published-package
        credentials {
            username = "<GITHUB_USERNAME>"
            password = "<GITHUB_ACCESS_TOKEN>"
        }
    }
}
```

</details>

## Supported Minecraft Versions

The versions are constructed using the version number for Minecraft appended with a build number (e.g. `26.1.2` would be `26.1.2.0`, `26.1.2.1`, etc.). If the version number for Minecraft does not have a third component, it must be set to `0` (e.g. `26.1` would be `26.1.0.0`, `26.1.0.1`, etc.).

Versions are broken into three priorities:

* Latest: Transformers for all patches of the latest Minecraft minor release will be generated once a week.
* Supported: Transformers for the latest patch of previous Minecraft minor releases (up to two years) will be generated once a month.
* End-of-Life: Transformers for the latest patch of previous Minecraft minor releases (longer than two years) will be generated once a year.

### Latest

| Minecraft Version |                                                                                           Transformer Version                                                                                            |
|:-----------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
|       26.3        |  ![26.3 Maven Version](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.uuid.gg%2Fsnapshots%2Fnet%2Fashwork%2Fmc%2Ftags%2Fmaven-metadata.xml&filter=26.3.0.*&cacheSeconds=43200)  |
|       26.2        |  ![26.2 Maven Version](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.uuid.gg%2Fsnapshots%2Fnet%2Fashwork%2Fmc%2Ftags%2Fmaven-metadata.xml&filter=26.2.0.*&cacheSeconds=43200)  |
|      26.1.2       | ![26.1.2 Maven Version](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.uuid.gg%2Fsnapshots%2Fnet%2Fashwork%2Fmc%2Ftags%2Fmaven-metadata.xml&filter=26.1.2.*&cacheSeconds=43200) |

## Using the Tags

<details open>

<summary>build.gradle</summary>

```groovy
dependencies {
    implementation platform('net.ashwork.mc:tags:<MINECRAFT_VERSION>.+')
    implementation 'net.ashwork.mc:tags-all'
}
```

</details>

<details>

<summary>build.gradle.kts</summary>

```kotlin
dependencies {
    implementation(platform("net.ashwork.mc:tags:<MINECRAFT_VERSION>.+"))
    implementation("net.ashwork.mc:tags-all")
}
```

</details>

## Available JARS

Tags publishes two JARs: `tags-all` and `tags-stubs`.

`tags-all` contains both the Java classes and the computed tag JSONs. This can be used during development for the shared
entries between mod loaders.

`tags-stubs` only contains the Java classes. This can be used when publishing your JAR as either a Jar-in-Jar component
or simply shadowing the files.
