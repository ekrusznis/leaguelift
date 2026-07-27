// Lets Gradle auto-download a matching JDK when the requested toolchain (Java 21,
// build.gradle.kts) isn't already installed/discoverable on this machine. Without
// this plugin, a missing local JDK 21 fails with:
// "Cannot find a Java installation ... Toolchain download repositories have not
// been configured."
plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "leaguelift-backend"
