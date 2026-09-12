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
        name = 'Transformers Github'
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
        name = "Transformers Github"
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

TODO: Supported minecraft versions

TODO: Using the tags