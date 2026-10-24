plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-conventions")
    application
}

dependencies { implementation(project(":canvas")) }

application {
    mainModule = "mathcanvas.app"
    mainClass = "mathcanvas.app.Main"
    // JavaFX loads its native libraries through System.load, a restricted method in JDK 25.
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}
