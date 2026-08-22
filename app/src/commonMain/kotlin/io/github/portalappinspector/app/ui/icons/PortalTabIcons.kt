package io.github.portalappinspector.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object PortalTabIcons {
    val Folder: ImageVector by lazy {
        strokeIcon(
            name = "Folder",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M4 9V41L9 21H39.5V15C39.5 13.8954 38.6046 13 37.5 13H24L19 7H6C4.89543 7 4 7.89543 4 9Z",
                "M40 41L44 21H8.8125L4 41H40Z",
            ),
        )
    }

    val Network: ImageVector by lazy {
        strokeIcon(
            name = "Network",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M24 4C12.9543 4 4 12.9543 4 24C4 35.0457 12.9543 44 24 44C35.0457 44 44 35.0457 44 24C44 12.9543 35.0457 4 24 4Z",
                "M6 16H42",
                "M6 32H42",
                "M24 4C18 10 15 16.6667 15 24C15 31.3333 18 38 24 44",
                "M24 4C30 10 33 16.6667 33 24C33 31.3333 30 38 24 44",
            ),
        )
    }

    val Logs: ImageVector by lazy {
        strokeIcon(
            name = "Logs",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M8 9H40",
                "M8 19H40",
                "M8 29H40",
                "M8 39H26",
            ),
        )
    }

    val ScreenMirror: ImageVector by lazy {
        strokeIcon(
            name = "ScreenMirror",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M7 10H41V34H7V10Z",
                "M18 42H30",
                "M24 34V42",
                "M14 17H34",
                "M14 25H26",
            ),
        )
    }

    val Response: ImageVector by lazy {
        strokeIcon(
            name = "Response",
            viewportSize = 24f,
            strokeWidth = 2f,
            pathData = listOf(
                "M5 22H19C19.5523 22 20 21.5523 20 21V7H15V2H5C4.44771 2 4 2.44771 4 3V21C4 21.5523 4.44771 22 5 22Z",
                "M15 2L20 7",
                "M8.5 14H15.5",
                "M8.5 18H15.5",
            ),
        )
    }

    val Unsupported: ImageVector by lazy {
        strokeIcon(
            name = "Unsupported",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M24 44C35.0457 44 44 35.0457 44 24C44 12.9543 35.0457 4 24 4C12.9543 4 4 12.9543 4 24C4 35.0457 12.9543 44 24 44Z",
                "M16 16L32 32",
                "M32 16L16 32",
            ),
        )
    }

    val Edit: ImageVector by lazy {
        strokeIcon(
            name = "Edit",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M7 42H43",
                "M11 26.7199V34H18.3172L39 13.3081L31.6951 6L11 26.7199Z",
            ),
        )
    }

    val Delete: ImageVector by lazy {
        strokeIcon(
            name = "Delete",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M9 10V44H39V10H9Z",
                "M20 20V33",
                "M28 20V33",
                "M4 10H44",
                "M16 10L19.289 4H28.7771L32 10H16Z",
            ),
        )
    }

    val Download: ImageVector by lazy {
        strokeIcon(
            name = "Download",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M6 24.0083V42H42V24",
                "M33 23L24 32L15 23",
                "M23.9917 6V32",
            ),
        )
    }

    val CopyPath: ImageVector by lazy {
        strokeIcon(
            name = "CopyPath",
            viewportSize = 24f,
            strokeWidth = 2f,
            pathData = listOf(
                "M6 4.96352V3.5C6 2.67158 6.67155 2 7.5 2H20.5C21.3285 2 22 2.67158 22 3.5V16.5C22 17.3285 21.3285 18 20.5 18H19.0087",
                "M17.5 5H3.5C2.67157 5 2 5.67157 2 6.5V20.5C2 21.3284 2.67157 22 3.5 22H17.5C18.3284 22 19 21.3284 19 20.5V6.5C19 5.67157 18.3284 5 17.5 5Z",
                "M8 18L10.5 13.5L13 9",
            ),
        )
    }

    val Star: ImageVector by lazy {
        strokeIcon(
            name = "Star",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M23.9986 5L17.8856 17.4776L4 19.4911L14.0589 29.3251L11.6544 43L23.9986 36.4192L36.3454 43L33.9586 29.3251L44 19.4911L30.1913 17.4776L23.9986 5Z",
            ),
        )
    }

    val StarFilled: ImageVector by lazy {
        filledIcon(
            name = "StarFilled",
            viewportSize = 48f,
            pathData = "M23.9986 5L17.8856 17.4776L4 19.4911L14.0589 29.3251L11.6544 43L23.9986 36.4192L36.3454 43L33.9586 29.3251L44 19.4911L30.1913 17.4776L23.9986 5Z",
        )
    }

    val ArrowDown: ImageVector by lazy {
        strokeIcon(
            name = "ArrowDown",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf("M36 18L24 30L12 18"),
        )
    }

    val ArrowUp: ImageVector by lazy {
        strokeIcon(
            name = "ArrowUp",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf("M13 30L25 18L37 30"),
        )
    }

    val ArrowRight: ImageVector by lazy {
        strokeIcon(
            name = "ArrowRight",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf("M19 12L31 24L19 36"),
        )
    }

    val Close: ImageVector by lazy {
        strokeIcon(
            name = "Close",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M8 8L40 40",
                "M8 40L40 8",
            ),
        )
    }

    val OpenInNew: ImageVector by lazy {
        strokeIcon(
            name = "OpenInNew",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M19 6H9C7.34315 6 6 7.34315 6 9V39C6 40.6569 7.34315 42 9 42H39C40.6569 42 42 40.6569 42 39V29",
                "M42 19L38 7L26 11",
                "M38 7C33 22 30 25 20 29",
            ),
        )
    }

    val Mock: ImageVector by lazy {
        strokeIcon(
            name = "Mock",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M16 7H32",
                "M20 7V19L10 36C8.43054 38.6679 10.3546 42 13.4497 42H34.5503C37.6454 42 39.5695 38.6679 38 36L28 19V7",
                "M15 31H33",
            ),
        )
    }

    val Plus: ImageVector by lazy {
        strokeIcon(
            name = "Plus",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M24 6V42",
                "M6 24H42",
            ),
        )
    }

    val Settings: ImageVector by lazy {
        filledIcon(
            name = "Settings",
            viewportSize = 24f,
            pathData = "M7.174 5.619A8.064 8.064 0 0 1 8.809 4.673L9.099 3.515A2 2 0 0 1 11.039 2H12.961A2 2 0 0 1 14.901 3.515L15.191 4.673A8.063 8.063 0 0 1 16.826 5.619L17.976 5.29A2 2 0 0 1 20.258 6.213L21.219 7.878A2 2 0 0 1 20.877 10.315L20.017 11.147A8.151 8.151 0 0 1 20.017 13.035L20.877 13.865A2 2 0 0 1 21.219 16.303L20.259 17.968A2 2 0 0 1 17.976 18.891L16.826 18.562A8.063 8.063 0 0 1 15.191 19.508L14.901 20.666A2 2 0 0 1 12.961 22.181H11.04A2 2 0 0 1 9.1 20.666L8.81 19.508A8.064 8.064 0 0 1 7.175 18.562L6.025 18.891A2 2 0 0 1 3.743 17.968L2.782 16.303A2 2 0 0 1 3.124 13.866L3.984 13.035A8.158 8.158 0 0 1 3.984 11.146L3.124 10.316A2 2 0 0 1 2.782 7.878L3.742 6.213A2 2 0 0 1 6.025 5.29L7.174 5.619ZM12 16A4 4 0 1 0 12 8A4 4 0 0 0 12 16ZM12 14A2 2 0 1 1 12 10A2 2 0 0 1 12 14Z",
        )
    }


    val Wifi: ImageVector by lazy {
        strokeIcon(
            name = "Wifi",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M4 18.9653C4.5888 18.4073 5.19522 17.8786 5.8174 17.3792C17.0371 8.37423 33.3821 8.90292 44 18.9653",
                "M38 25.799C30.268 18.067 17.732 18.067 10 25.799",
                "M32 32.3137C27.5817 27.8954 20.4183 27.8954 16 32.3137",
                "M24 40C25.3807 40 26.5 38.8807 26.5 37.5C26.5 36.1193 25.3807 35 24 35C22.6193 35 21.5 36.1193 21.5 37.5C21.5 38.8807 22.6193 40 24 40Z",
            ),
        )
    }

    val Usb: ImageVector by lazy {
        strokeIcon(
            name = "Usb",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M12 22C14.2091 22 16 20.2091 16 18C16 15.7909 14.2091 14 12 14C9.79086 14 8 15.7909 8 18C8 20.2091 9.79086 22 12 22Z",
                "M36 28C38.2091 28 40 26.2091 40 24C40 21.7909 38.2091 20 36 20C33.7909 20 32 21.7909 32 24C32 26.2091 33.7909 28 36 28Z",
                "M19 9L24 4L29 9",
                "M25 39L12 28.2632V22",
                "M36 28V32.7895L24 41",
                "M24 4V43",
                "M21 44H27",
            ),
        )
    }

    val SharedPrefs: ImageVector by lazy {
        strokeIcon(
            name = "SharedPrefs",
            viewportSize = 24f,
            strokeWidth = 2f,
            pathData = listOf(
                "M8.5 7H14.5",
                "M12.5 12H8.5",
                "M10.0715 21H4C3.44771 21 3 20.5523 3 20V3.5C3 2.94771 3.44771 2.5 4 2.5H20C20.5523 2.5 21 2.94771 21 3.5V8.35835",
                "M13.5 19L18.75 11.75L21 13.5L15.5 21H13.5V19Z",
            ),
        )
    }

    val SharedPrefsEntry: ImageVector by lazy {
        strokeIcon(
            name = "SharedPrefsEntry",
            viewportSize = 48f,
            strokeWidth = 4f,
            pathData = listOf(
                "M22.8682 24.2982C25.4105 26.7935 26.4138 30.4526 25.4971 33.8863C24.5805 37.32 21.8844 40.0019 18.4325 40.9137C14.9806 41.8256 11.3022 40.8276 8.79375 38.2986C5.02208 34.4141 5.07602 28.2394 8.91499 24.4206C12.754 20.6019 18.9613 20.5482 22.8664 24.3L22.8682 24.2982Z",
                "M23 24L40 7",
                "M30.3052 16.9001L35.7337 22.3001L42.0671 16.0001L36.6385 10.6001L30.3052 16.9001Z",
            ),
        )
    }

    internal fun strokeIcon(
        name: String,
        pathData: List<String>,
        viewportSize: Float,
        strokeWidth: Float,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 18.dp,
        defaultHeight = 18.dp,
        viewportWidth = viewportSize,
        viewportHeight = viewportSize,
    ).apply {
        pathData.forEach { path ->
            addPath(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathData = PathParser().parsePathString(path).toNodes(),
            )
        }
    }.build()

    internal fun filledIcon(
        name: String,
        pathData: String,
        viewportSize: Float,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 18.dp,
        defaultHeight = 18.dp,
        viewportWidth = viewportSize,
        viewportHeight = viewportSize,
    ).apply {
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString(pathData).toNodes(),
        )
    }.build()
}
