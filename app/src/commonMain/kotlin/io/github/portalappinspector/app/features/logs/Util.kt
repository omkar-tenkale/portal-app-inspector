package io.github.portalappinspector.app.features.logs

internal fun PortalLogEntry.logLine(): String {
    val line = message.takeIf { it.isNotBlank() }
        ?: throwable?.takeIf { it.isNotBlank() }
        ?: "(empty)"
    return line.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "(empty)" }
}

internal fun PortalLogCallSite.shortLabel(): String {
    val file = fileName?.takeIf { it.isNotBlank() }
    return when {
        file != null && lineNumber != null -> "$file:$lineNumber"
        file != null -> file
        methodName.isNotBlank() -> "${className.substringAfterLast('.')}.$methodName"
        else -> className.substringAfterLast('.')
    }
}

internal fun PortalLogCallSite.detailLabel(): String {
    val location = shortLabel()
    val method = methodName.takeIf { it.isNotBlank() } ?: return location
    val owner = className.substringAfterLast('.').takeIf { it.isNotBlank() }
    return if (owner == null) {
        "$location in $method"
    } else {
        "$location in $owner.$method"
    }
}

internal fun List<PortalLogEntry>.toLogRows(gapThresholdMillis: Long): List<PortalLogRow> {
    if (isEmpty()) return emptyList()
    val rows = mutableListOf<PortalLogRow>()
    forEachIndexed { index, entry ->
        if (index > 0) {
            val previous = this[index - 1]
            val gap = previous.timestampEpochMillis - entry.timestampEpochMillis
            if (gap >= gapThresholdMillis) {
                rows += PortalLogRow.Gap(
                    beforeId = previous.id,
                    afterId = entry.id,
                    durationMillis = gap,
                )
            }
        }
        rows += PortalLogRow.Entry(entry)
    }
    return rows
}
