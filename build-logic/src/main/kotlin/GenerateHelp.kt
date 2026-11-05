import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction

/**
 * Writes the in-app help, `help.json`, from the language reference, `docs/language.md`, so the
 * reference is the help's one source and the two cannot drift. Read by its shape, not by a list:
 *
 * - every table of §12's subsections whose first column is `Name` gives an entry for each name in
 *   its first cell (a code span may hold several, `sin cos tan`), its category the subsection's
 *   title (`Functions`, `Commands`, …), with the arity, the alias and the notes of its row (its
 *   description, without a trailing "(§…)");
 * - each row of the precedence table, §4.1, gives an entry for each operator in its `Construct`
 *   cell (`lim[…]` is `lim`, `∫[a, b] … dx` is `∫`), category `Operators`;
 * - the examples come from the example tables only, never the prose, whose arrows may need a
 *   context (a definition, the angle mode) a new cell does not have: every row of a table whose
 *   first column is `Input` (or `Names` then `Input`) and last `Result`, with as many results as
 *   inputs, its code spans paired in order; a row whose input cell says more than its code spans
 *   (`sin(30)` in degree mode), or whose result is cut (`0.333…`), is left out. An input is the example of the name it calls
 *   (`name(…`), of the call its `Parse` cell shows, or of the names its `Names` cell gives that it
 *   or its result writes (all of them when it writes none). An input holding `…` is left out, and
 *   each entry keeps its first four examples;
 * - §12.0's table of inputs and results is the first entry, `Getting started`, its category the
 *   subsection's title, its description the paragraph above the table, and every row its example,
 *   in order, none of them another entry's.
 *
 * The output is the resource at [resource] under [outputDirectory], which the module adds to its
 * resources. The task is cacheable: its only input is the file and the path.
 */
@CacheableTask
abstract class GenerateHelp : DefaultTask() {

    /** `docs/language.md`. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val language: RegularFileProperty

    /** The resource's path, `mathcanvas/app/help.json`. */
    @get:Input
    abstract val resource: Property<String>

    /** Where the resource is written, a resource directory of the module. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val entries = HelpReader(language.get().asFile.readLines(Charsets.UTF_8)).read()
        val out = outputDirectory.get().file(resource.get()).asFile
        out.parentFile.mkdirs()
        out.writeText(HelpReader.json(entries), Charsets.UTF_8)
    }
}

/**
 * Registers `generateHelp` in the project: [GenerateHelp] from `docs/language.md` to the resource
 * [resource] (`mathcanvas/app/help.json`) in `build/generated/help`, a resource directory of the
 * main source set.
 */
fun Project.generateHelp(resource: String) {
    val task =
        tasks.register("generateHelp", GenerateHelp::class.java) {
            description = "Generates help.json from docs/language.md."
            language.set(rootProject.layout.projectDirectory.file("docs/language.md"))
            this.resource.set(resource)
            outputDirectory.set(layout.buildDirectory.dir("generated/help"))
        }
    extensions.getByType(SourceSetContainer::class.java).named("main") { resources.srcDir(task) }
}

/** One name's help: what the reference says of it, and its examples. */
class HelpEntry(
    val name: String,
    val category: String,
    val section: String,
    val arity: String,
    val alias: String,
    val description: String,
) {
    /** The examples, an input and the result the reference gives. */
    val examples = mutableListOf<Pair<String, String>>()
}

/** Reads the entries out of the reference's lines. */
class HelpReader(private val lines: List<String>) {

    private val entries = mutableListOf<HelpEntry>()

    /** The entries in the reference's order: Getting started, §12's names, then the operators. */
    fun read(): List<HelpEntry> {
        names()
        operators()
        examples()
        gettingStarted()?.let { entries.add(0, it) }
        return entries
    }

    /** §12.0's steps: one entry with every row of its table as an example, in order. */
    private fun gettingStarted(): HelpEntry? {
        val table = tables().firstOrNull { it.section == GETTING_STARTED } ?: return null
        val heading = lines.indexOfFirst { it.startsWith("### $GETTING_STARTED ") }
        val paragraph =
            lines.drop(heading + 1).dropWhile { it.isBlank() }.takeWhile { it.isNotBlank() && !it.startsWith("|") }
        val entry = HelpEntry(table.title, table.title, GETTING_STARTED, "", "", plain(paragraph.joinToString(" ")))
        for (row in table.rows) {
            val input = spans(row.first())
            val result = spans(row.last())
            if (input.size == 1 && result.size == 1) {
                entry.examples += input[0] to result[0]
            }
        }
        return entry
    }

    private fun byName(name: String): List<HelpEntry> = entries.filter { it.name == name }

    /** §12's tables whose first column is Name. */
    private fun names() {
        for (table in tables()) {
            val section = table.section
            if (!section.startsWith("12.") || table.header.firstOrNull() != "Name") {
                continue
            }
            val arity = table.header.indexOf("Arity")
            val alias = table.header.indexOf("Alias")
            val notes = table.header.indexOf("Notes")
            for (row in table.rows) {
                for (name in identifiers(row[0])) {
                    if (byName(name).none { it.section.startsWith("12.") }) {
                        entries +=
                            HelpEntry(
                                name,
                                table.title,
                                section,
                                cell(row, arity),
                                cell(row, alias),
                                description(cell(row, notes)))
                    }
                }
            }
        }
    }

    /** The precedence table's operators, with the examples before ⇒. */
    private fun operators() {
        val table = tables().firstOrNull { it.section == "4.1" } ?: return
        val construct = table.header.indexOf("Construct")
        val operand = table.header.indexOf("Operand")
        val associativity = table.header.indexOf("Associativity")
        for (row in table.rows) {
            val level = plain(row[0]).replace(Regex("^[0-9]+[a-z]? "), "")
            val described =
                "$level. Operand: ${cell(row, operand)}. Associativity: ${cell(row, associativity)}."
            for (name in operatorNames(row.getOrElse(construct) { "" })) {
                if (byName(name).none { it.category == OPERATORS }) {
                    entries += HelpEntry(name, OPERATORS, "4.1", "", "", described)
                }
            }
        }
    }

    /** The examples of the text and of the Input … Result tables. */
    private fun examples() {
        // the example tables only: the prose's arrows may need their context (a definition, the
        // angle mode) that a new cell does not have
        for (table in tables()) {
            val header = table.header
            val input = header.indexOf("Input")
            if (input < 0 || input > 1 || header.last() != "Result") {
                continue
            }
            if (input == 1 && header[0] != "Names" || table.section == GETTING_STARTED) {
                continue
            }
            val parse = header.indexOf("Parse")
            for (row in table.rows) {
                val inputs = spans(row.getOrElse(input) { "" })
                val results = spans(row.last())
                val parses = if (parse >= 0) spans(row.getOrElse(parse) { "" }) else listOf()
                val named = if (input == 1) identifiersOrOperators(row[0]) else listOf()
                val qualified =
                    row.getOrElse(input) { "" }.replace(Regex("`[^`]+`"), "").replace(",", "").isNotBlank()
                if (results.size != inputs.size || qualified || results.any { it.contains("…") }) {
                    // an example is shown with its whole result, and needs no setting a cell lacks
                    // (`sin(30)` in degree mode)
                    continue
                }
                for ((k, text) in inputs.withIndex()) {
                    attach(text, results[k], if (parses.size == inputs.size) parses[k] else null, named)
                }
            }
        }
    }

    private fun attach(input: String, result: String, parse: String?, named: List<String>) {
        if (input.contains("…")) {
            return
        }
        // a row of several names gives each the inputs or results that write it, when any does
        val mentioned = named.filter { input.contains(it) || result.contains(it) }
        val targets =
            mentioned.ifEmpty { named }.ifEmpty {
                val head = call(input) ?: parse?.let { call(it) }
                if (head == null) listOf() else listOf(head)
            }
        for (name in targets) {
            // a call is a function's or a command's example; a named input that calls nothing
            // (`17 mod 5`) is the operator's, when the name is one
            val all = byName(name)
            val operator = named.isNotEmpty() && call(input) != name && all.any { it.category == OPERATORS }
            val entry = all.firstOrNull { (it.category == OPERATORS) == operator } ?: continue
            add(entry, input, result)
        }
    }

    private fun add(entry: HelpEntry, input: String, result: String) {
        if (entry.examples.size < MAX_EXAMPLES && entry.examples.none { it.first == input }) {
            entry.examples += input to result
        }
    }

    /** A table: the section and title of the heading above it, its header and its rows. */
    private class Table(
        val section: String,
        val title: String,
        val header: List<String>,
        val rows: List<List<String>>,
    )

    private fun tables(): List<Table> {
        val out = mutableListOf<Table>()
        var section = ""
        var title = ""
        var header: List<String>? = null
        var rows = mutableListOf<List<String>>()
        fun close() {
            header?.let { out += Table(section, title, it, rows) }
            header = null
            rows = mutableListOf()
        }
        for (line in lines) {
            if (!line.startsWith("|")) {
                close()
                val heading = Regex("^#+ ([0-9]+(?:\\.[0-9]+)*) (.*)$").find(line)
                if (heading != null) {
                    section = heading.groupValues[1]
                    title = heading.groupValues[2].trim()
                } else if (line.startsWith("#")) {
                    section = ""
                    title = ""
                }
                continue
            }
            val cells = cells(line)
            if (header == null) {
                header = cells
            } else if (!line.matches(Regex("^\\|[-| :]+\\|$"))) {
                rows += cells
            }
        }
        close()
        return out
    }

    companion object {
        private const val OPERATORS = "Operators"
        private const val MAX_EXAMPLES = 4
        private const val GETTING_STARTED = "12.0"
        private val IDENTIFIER = Regex("^[A-Za-z][A-Za-z0-9]*$")

        /** A table row's cells, split at the bars that are not escaped. */
        private fun cells(line: String): List<String> =
            line.trim().split(Regex("(?<!\\\\)\\|")).drop(1).dropLast(1).map { it.trim() }

        private fun spans(text: String): List<String> =
            Regex("`([^`]+)`").findAll(text).map { it.groupValues[1].replace("\\|", "|") }.toList()

        /** The names in a cell's code spans, several to a span when spaces part them. */
        private fun identifiers(text: String): List<String> =
            spans(text).flatMap { it.split(Regex("\\s+")) }.filter { IDENTIFIER.matches(it) }

        /**
         * The operators a construct cell writes: each of a span's tokens (`√ ∛ ∜`), the head of a
         * span with a placeholder (`∫[a, b] … dx` is `∫`, `lim[…]` is `lim`), and none of a span
         * that is an example (`sin x`).
         */
        private fun operatorNames(text: String): List<String> =
            spans(text).flatMap { span ->
                val tokens = span.split(Regex("\\s+")).filter { it.isNotEmpty() }
                when {
                    span.contains("…") -> listOf(tokens[0].substringBefore('['))
                    tokens.size > 1 && tokens.any { IDENTIFIER.matches(it) } -> listOf()
                    else -> tokens
                }
            }

        private fun identifiersOrOperators(text: String): List<String> =
            spans(text).flatMap { it.split(Regex("\\s+")) }.filter { it.isNotEmpty() }

        /** The name an input calls first, `name(…`, or null. */
        private fun call(text: String): String? =
            Regex("^([A-Za-z][A-Za-z0-9]*)\\(").find(text.trim())?.groupValues?.get(1)

        /** A Notes cell as a description: the reference's trailing "(§…)" left out. */
        private fun description(notes: String): String =
            notes.replace(Regex("\\s*\\(§[^()]*\\)$"), "")

        private fun cell(row: List<String>, index: Int): String =
            if (index < 0) "" else plain(row.getOrElse(index) { "" })

        /** A cell as plain text: no code marks or emphasis, escaped bars unescaped. */
        private fun plain(text: String): String =
            text.replace("\\|", "|").replace("`", "").replace("**", "").trim()

        /** The entries as `help.json`. */
        fun json(entries: List<HelpEntry>): String {
            val out = StringBuilder("{\n  \"version\": 1,\n  \"entries\": [")
            for ((k, e) in entries.withIndex()) {
                out.append(if (k == 0) "\n" else ",\n")
                out.append("    {\n")
                out.append("      \"name\": ${quote(e.name)},\n")
                out.append("      \"category\": ${quote(e.category)},\n")
                out.append("      \"section\": ${quote(e.section)},\n")
                out.append("      \"arity\": ${quote(e.arity)},\n")
                out.append("      \"alias\": ${quote(e.alias)},\n")
                out.append("      \"description\": ${quote(e.description)},\n")
                out.append("      \"examples\": [")
                for ((j, x) in e.examples.withIndex()) {
                    out.append(if (j == 0) "\n" else ",\n")
                    out.append(
                        "        { \"input\": ${quote(x.first)}, \"result\": ${quote(x.second)} }")
                }
                out.append(if (e.examples.isEmpty()) "]\n" else "\n      ]\n")
                out.append("    }")
            }
            out.append("\n  ]\n}\n")
            return out.toString()
        }

        private fun quote(s: String): String {
            val out = StringBuilder("\"")
            for (c in s) {
                when {
                    c == '"' -> out.append("\\\"")
                    c == '\\' -> out.append("\\\\")
                    c == '\n' -> out.append("\\n")
                    c < ' ' -> out.append(String.format("\\u%04x", c.code))
                    else -> out.append(c)
                }
            }
            return out.append('"').toString()
        }
    }
}
