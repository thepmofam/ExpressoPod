# Project Rules

## Automatic APK and AAB Builds
- Whenever the user reports an issue/bug or requests a feature change in the codebase, always build the **debug** APK (`assembleDebug`), **release** APK (`assembleRelease`), and **release** AAB (`bundleRelease`) automatically after implementing the changes.
- Note for building on this machine: If `JAVA_HOME` is not set in the environment, invoke Gradle using `/opt/android-studio/jbr/bin/java -Xmx64m -Xms64m -classpath /home/pm/AndroidStudioProjects/ExpressivePodcasts/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug assembleRelease bundleRelease -Dorg.gradle.java.home=/opt/android-studio/jbr`.
