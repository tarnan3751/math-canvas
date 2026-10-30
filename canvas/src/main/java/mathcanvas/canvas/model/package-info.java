/**
 * The canvas object model: immutable objects with stable ids, the dependency graph over them, and
 * the geometry recomputed from their definitions. Free of JavaFX, so it tests headless; the model
 * never evaluates an expression itself, it asks a {@link mathcanvas.canvas.model.Sampler}.
 */
package mathcanvas.canvas.model;
