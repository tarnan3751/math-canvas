import org.gradle.api.provider.ListProperty

/** What `mathcanvas.javafx-free` checks in a module: the whole of it, or the packages listed. */
interface JavafxFreeExtension {
    /**
     * The packages (`mathcanvas.render.font`) whose main and test sources must not reference
     * JavaFX, for a module that itself depends on it; empty, the default, checks the whole module:
     * its sources and its classpaths.
     */
    val packages: ListProperty<String>
}
