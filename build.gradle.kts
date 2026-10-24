// The root project builds nothing itself; it formats the files that are not Java.

plugins { alias(libs.plugins.spotless) }

spotless {
    format("misc") {
        // Explicit roots, never a recursive glob from the project root: walking .gradle/ and
        // build/ would make every build miss the configuration cache.
        target(
            "*.kts",
            "*/*.kts",
            "build-logic/src/**/*.kt",
            "build-logic/src/**/*.kts",
            "gradle/*.toml",
            ".github/**/*.yml",
            "tools/*.sh",
            ".editorconfig",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
    // Markdown keeps trailing whitespace: two trailing spaces are a hard line break.
    format("markdown") {
        target("*.md", "docs/**/*.md")
        endWithNewline()
    }
}
