package net.ashwork.gradle.multiloader

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra

fun Project.resolveProperty(name: String): String {
    if (project.extra.has(name)) return project.extra[name].toString()
    return rootProject.extra[name].toString()
}
