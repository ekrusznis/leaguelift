NOTE: gradlew / gradlew.bat wrapper scripts and gradle-wrapper.jar are not generated
in this commit because this sandboxed environment has no Gradle installation and no
network access to download one. Run this once, locally, before first use:

    gradle wrapper --gradle-version 9.6.1

That regenerates `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`
(the properties file is already committed). After running it, delete this file.
