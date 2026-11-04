/** The command-line REPL and script runner over the engine. */
module mathcanvas.cli {
  requires mathcanvas.engine;
  // help(name) reads the generated help.json
  requires com.fasterxml.jackson.core;
}
