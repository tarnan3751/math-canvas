/** The desktop application: the window, the document model and persistence. */
module mathcanvas.app {
  requires mathcanvas.canvas;
  requires javafx.controls;
  // ImageIO writes the exported PNG; javafx.graphics already brings java.desktop into the image.
  requires java.desktop;
  requires com.fasterxml.jackson.core;

  // The toolkit instantiates the Application subclass reflectively.
  exports mathcanvas.app to
      javafx.graphics;
}
