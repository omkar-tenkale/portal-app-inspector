package io.github.portalappinspector.app.features.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview

@Preview(heightDp = 400)
@Composable
internal fun PreviewFilesPanel_Standard() {
    val previewState = remember {
        FilesPanelState().apply {
            currentPath = "files:/internal/data"
            pinnedFiles = emptyList()
            fileItems.addAll(
                listOf(
                    PortalFileItem(
                        name = "config.json",
                        path = "files:/internal/data/config.json",
                        absolutePath = "files:/internal/data/config.json",
                        directory = false,
                        sizeBytes = 1024L
                    ),
                    PortalFileItem(
                        name = "logs_dir",
                        path = "files:/internal/data/logs_dir",
                        absolutePath = "files:/internal/data/logs_dir",
                        directory = true,
                        sizeBytes = 0L
                    ),
                    PortalFileItem(
                        name = "profile.png",
                        path = "files:/internal/data/profile.png",
                        absolutePath = "files:/internal/data/profile.png",
                        directory = false,
                        sizeBytes = 204800L
                    )
                )
            )
        }
    }
    FilesPanelContent(
        state = previewState,
        enabled = true,
        onLoadPath = {},
        onOpenDirectory = {},
        onGoToParentDirectory = {},
        onOpenBreadcrumb = {},
        onCopyPath = {},
        onTogglePinned = {},
        onDownloadFile = {},
        onViewFile = {},
        onDeletePath = {},
    )
}

@Preview(heightDp = 400)
@Composable
internal fun PreviewFilesPanel_Loading() {
    val previewState = remember {
        FilesPanelState().apply {
            currentPath = "files:/internal/data"
            loading = true
            pinnedFiles = emptyList()
            fileItems.addAll(
                listOf(
                    PortalFileItem(
                        name = "config.json",
                        path = "files:/internal/data/config.json",
                        absolutePath = "files:/internal/data/config.json",
                        directory = false,
                        sizeBytes = 1024L
                    )
                )
            )
        }
    }
    FilesPanelContent(
        state = previewState,
        enabled = true,
        onLoadPath = {},
        onOpenDirectory = {},
        onGoToParentDirectory = {},
        onOpenBreadcrumb = {},
        onCopyPath = {},
        onTogglePinned = {},
        onDownloadFile = {},
        onViewFile = {},
        onDeletePath = {},
    )
}

@Preview(heightDp = 400)
@Composable
internal fun PreviewFilesPanel_Error() {
    val previewState = remember {
        FilesPanelState().apply {
            currentPath = "files:/internal/data"
            error = "Failed to list children. Permission denied or device offline."
            pinnedFiles = emptyList()
        }
    }
    FilesPanelContent(
        state = previewState,
        enabled = true,
        onLoadPath = {},
        onOpenDirectory = {},
        onGoToParentDirectory = {},
        onOpenBreadcrumb = {},
        onCopyPath = {},
        onTogglePinned = {},
        onDownloadFile = {},
        onViewFile = {},
        onDeletePath = {},
    )
}

@Preview(heightDp = 400)
@Composable
internal fun PreviewFilesPanel_Empty() {
    val previewState = remember {
        FilesPanelState().apply {
            currentPath = "files:/internal/empty_dir"
            pinnedFiles = emptyList()
        }
    }
    FilesPanelContent(
        state = previewState,
        enabled = true,
        onLoadPath = {},
        onOpenDirectory = {},
        onGoToParentDirectory = {},
        onOpenBreadcrumb = {},
        onCopyPath = {},
        onTogglePinned = {},
        onDownloadFile = {},
        onViewFile = {},
        onDeletePath = {},
    )
}

@Preview(heightDp = 400)
@Composable
internal fun PreviewFilesPanel_ViewingFile() {
    val previewState = remember {
        FilesPanelState().apply {
            currentPath = "files:/internal/data/config.json"
            backStack.add("files:/internal/data")
            pinnedFiles = emptyList()
            viewedFile = PortalViewedFile(
                item = PortalFileItem(
                    name = "config.json",
                    path = "files:/internal/data/config.json",
                    absolutePath = "files:/internal/data/config.json",
                    directory = false,
                    sizeBytes = 1024L
                ),
                text = "{\n  \"theme\": \"dark\",\n  \"version\": 1\n}"
            )
        }
    }
    FilesPanelContent(
        state = previewState,
        enabled = true,
        onLoadPath = {},
        onOpenDirectory = {},
        onGoToParentDirectory = {},
        onOpenBreadcrumb = {},
        onCopyPath = {},
        onTogglePinned = {},
        onDownloadFile = {},
        onViewFile = {},
        onDeletePath = {},
    )
}
