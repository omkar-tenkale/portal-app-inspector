package io.github.portalappinspector.app.features.files

internal const val FileViewMaxBytes = 512L * 1024L

internal data class FilePathSegment(
    val label: String,
    val path: String?,
)

internal fun filePathSegments(path: String?): List<FilePathSegment> {
    if (path.isNullOrBlank()) return listOf(FilePathSegment(label = "All", path = null))
    val trimmed = path.trim()
    val root = trimmed.substringBefore("/", missingDelimiterValue = trimmed)
    val rootSegment = if (trimmed.contains("/")) "$root/" else root
    val children = trimmed
        .substringAfter("/", missingDelimiterValue = "")
        .split("/")
        .filter { it.isNotBlank() }
    return buildList {
        add(FilePathSegment(label = rootSegment, path = rootSegment))
        children.fold(rootSegment.trimEnd('/')) { currentPath, child ->
            val nextPath = "$currentPath/$child"
            add(FilePathSegment(label = child, path = nextPath))
            nextPath
        }
    }
}

internal fun String.isRootFilePath(): Boolean =
    endsWith(":/")

internal fun PortalFileItem.iconKind(): PortalFileIconKind =
    when {
        directory -> PortalFileIconKind.Folder
        name.isImageFileName() -> PortalFileIconKind.ImageFile
        name.isTextReadableFileName() -> PortalFileIconKind.TextFile
        else -> PortalFileIconKind.DefaultFile
    }

internal fun PortalFileItem.isViewableFile(): Boolean =
    !directory && (sizeBytes ?: Long.MAX_VALUE) <= FileViewMaxBytes

internal fun PortalFileItem.isSharedPrefsFile(): Boolean =
    !directory && path.startsWith("shared-prefs:/") && name.fileExtension() == "xml"

internal fun String.fileExtension(): String =
    substringAfterLast('.', missingDelimiterValue = "").lowercase()

internal fun String.isImageFileName(): Boolean =
    fileExtension() in setOf(
        "apng", "avif", "bmp", "gif", "heic", "heif", "ico",
        "jpeg", "jpg", "png", "svg", "tif", "tiff", "webp",
    )

internal fun String.isTextReadableFileName(): Boolean {
    val lowerName = lowercase()
    if (lowerName in setOf(
            ".env", ".gitignore", ".gitattributes", ".editorconfig",
            "license", "readme", "todo",
        )
    ) {
        return true
    }

    return fileExtension() in setOf(
        "c", "cc", "conf", "cpp", "cs", "css", "csv", "dart", "gradle",
        "graphql", "h", "hpp", "htm", "html", "java", "js", "json", "jsx",
        "kt", "kts", "log", "md", "mjs", "properties", "py", "rb", "rs",
        "sh", "sql", "swift", "toml", "ts", "tsx", "txt", "xml", "yaml", "yml",
    )
}
