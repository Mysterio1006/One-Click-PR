package me.app.oneclickpr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 极致优化的轻量级本地图标库。
 * 完全替代庞大的 material-icons 库，无任何外部图标依赖。
 */
object AppIcons {
    val History: ImageVector
        get() = ImageVector.Builder(name = "History", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 3f); curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f); lineTo(1f, 12f); lineToRelative(3.89f, 3.89f); lineToRelative(0.07f, 0.14f); lineTo(9f, 12f); lineTo(6f, 12f); curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f); reflectiveCurveToRelative(7f, 3.13f, 7f, 7f); reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f); curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f); lineToRelative(-1.42f, 1.42f); curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f); curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f); reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f); close()
                moveTo(12f, 8f); verticalLineToRelative(5f); lineToRelative(4.28f, 2.54f); lineToRelative(0.72f, -1.21f); lineToRelative(-3.5f, -2.08f); lineTo(13.5f, 8f); lineTo(12f, 8f); close()
            }
        }.build()

    val AttachFile: ImageVector
        get() = ImageVector.Builder(name = "AttachFile", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16.5f, 6f); verticalLineToRelative(11.5f); curveToRelative(0f, 2.21f, -1.79f, 4f, -4f, 4f); reflectiveCurveToRelative(-4f, -1.79f, -4f, -4f); lineTo(8.5f, 5f); curveToRelative(0f, -1.38f, 1.12f, -2.5f, 2.5f, -2.5f); reflectiveCurveToRelative(2.5f, 1.12f, 2.5f, 2.5f); verticalLineToRelative(10.5f); curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f); reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f); lineTo(11.5f, 6f); horizontalLineToRelative(-1.5f); verticalLineToRelative(9.5f); curveToRelative(0f, 1.38f, 1.12f, 2.5f, 2.5f, 2.5f); reflectiveCurveToRelative(2.5f, -1.12f, 2.5f, -2.5f); lineTo(15f, 5f); curveToRelative(0f, -2.21f, -1.79f, -4f, -4f, -4f); reflectiveCurveTo(7f, 2.79f, 7f, 5f); verticalLineToRelative(12.5f); curveToRelative(0f, 3.04f, 2.46f, 5.5f, 5.5f, 5.5f); reflectiveCurveToRelative(5.5f, -2.46f, 5.5f, -5.5f); lineTo(18f, 6f); horizontalLineToRelative(-1.5f); close()
            }
        }.build()

    val CreateNewFolder: ImageVector
        get() = ImageVector.Builder(name = "CreateNewFolder", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 6f); horizontalLineToRelative(-8f); lineToRelative(-2f, -2f); lineTo(4f, 4f); curveToRelative(-1.11f, 0f, -1.99f, 0.89f, -1.99f, 2f); lineTo(2f, 18f); curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f); horizontalLineToRelative(16f); curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f); lineTo(22f, 8f); curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f); close()
                moveTo(19f, 14f); horizontalLineToRelative(-3f); verticalLineToRelative(3f); horizontalLineToRelative(-2f); verticalLineToRelative(-3f); horizontalLineToRelative(-3f); verticalLineToRelative(-2f); horizontalLineToRelative(3f); lineTo(14f, 9f); horizontalLineToRelative(2f); verticalLineToRelative(3f); horizontalLineToRelative(3f); verticalLineToRelative(2f); close()
            }
        }.build()

    val Folder: ImageVector
        get() = ImageVector.Builder(name = "Folder", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 4f); lineTo(4f, 4f); curveToRelative(-1.1f, 0f, -1.99f, 0.89f, -1.99f, 2f); lineTo(2f, 18f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(16f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); lineTo(22f, 8f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); horizontalLineToRelative(-8f); lineToRelative(-2f, -2f); close()
            }
        }.build()

    val InsertDriveFile: ImageVector
        get() = ImageVector.Builder(name = "InsertDriveFile", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 2f); curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f); lineTo(4f, 20f); curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f); horizontalLineToRelative(14f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); lineTo(20f, 8f); lineToRelative(-6f, -6f); lineTo(6f, 2f); close()
                moveTo(13f, 9f); lineTo(13f, 3.5f); lineTo(18.5f, 9f); lineTo(13f, 9f); close()
            }
        }.build()

    val KeyboardArrowDown: ImageVector
        get() = ImageVector.Builder(name = "KeyboardArrowDown", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7.41f, 8.59f); lineTo(12f, 13.17f); lineToRelative(4.59f, -4.58f); lineTo(18f, 10f); lineToRelative(-6f, 6f); lineToRelative(-6f, -6f); lineToRelative(1.41f, -1.41f); close()
            }
        }.build()

    val KeyboardArrowRight: ImageVector
        get() = ImageVector.Builder(name = "KeyboardArrowRight", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.59f, 16.59f); lineTo(13.17f, 12f); lineTo(8.59f, 7.41f); lineTo(10f, 6f); lineToRelative(6f, 6f); lineToRelative(-6f, 6f); lineToRelative(-1.41f, -1.41f); close()
            }
        }.build()

    val Add: ImageVector
        get() = ImageVector.Builder(name = "Add", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 13f); horizontalLineToRelative(-6f); verticalLineToRelative(6f); horizontalLineToRelative(-2f); verticalLineToRelative(-6f); horizontalLineTo(5f); verticalLineToRelative(-2f); horizontalLineToRelative(6f); verticalLineTo(5f); horizontalLineToRelative(2f); verticalLineToRelative(6f); horizontalLineToRelative(6f); verticalLineToRelative(2f); close()
            }
        }.build()

    val Close: ImageVector
        get() = ImageVector.Builder(name = "Close", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 6.41f); lineTo(17.59f, 5f); lineTo(12f, 10.59f); lineTo(6.41f, 5f); lineTo(5f, 6.41f); lineTo(10.59f, 12f); lineTo(5f, 17.59f); lineTo(6.41f, 19f); lineTo(12f, 13.41f); lineTo(17.59f, 19f); lineTo(19f, 17.59f); lineTo(13.41f, 12f); close()
            }
        }.build()

    val ArrowBack: ImageVector
        get() = ImageVector.Builder(name = "ArrowBack", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 11f); horizontalLineTo(7.83f); lineToRelative(5.59f, -5.59f); lineTo(12f, 4f); lineToRelative(-8f, 8f); lineToRelative(8f, 8f); lineToRelative(1.41f, -1.41f); lineTo(7.83f, 13f); horizontalLineTo(20f); verticalLineToRelative(-2f); close()
            }
        }.build()

    val Delete: ImageVector
        get() = ImageVector.Builder(name = "Delete", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(8f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(7f); horizontalLineTo(6f); verticalLineToRelative(12f); close()
                moveTo(19f, 4f); horizontalLineToRelative(-3.5f); lineToRelative(-1f, -1f); horizontalLineToRelative(-5f); lineToRelative(-1f, 1f); horizontalLineTo(5f); verticalLineToRelative(2f); horizontalLineToRelative(14f); verticalLineTo(4f); close()
            }
        }.build()

    val AccountCircle: ImageVector
        get() = ImageVector.Builder(name = "AccountCircle", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); reflectiveCurveToRelative(4.48f, 10f, 10f, 10f); reflectiveCurveToRelative(10f, -4.48f, 10f, -10f); reflectiveCurveTo(17.52f, 2f, 12f, 2f); close()
                moveTo(12f, 5f); curveToRelative(1.66f, 0f, 3f, 1.34f, 3f, 3f); reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f); reflectiveCurveToRelative(-3f, -1.34f, -3f, -3f); reflectiveCurveToRelative(1.34f, -3f, 3f, -3f); close()
                moveTo(12f, 19.2f); curveToRelative(-2.5f, 0f, -4.71f, -1.28f, -6f, -3.22f); curveToRelative(0.03f, -1.99f, 4f, -3.08f, 6f, -3.08f); curveToRelative(1.99f, 0f, 5.97f, 1.09f, 6f, 3.08f); curveToRelative(-1.29f, 1.94f, -3.5f, 3.22f, -6f, 3.22f); close()
            }
        }.build()

    val Visibility: ImageVector
        get() = ImageVector.Builder(name = "Visibility", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 4.5f); curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f); curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f); reflectiveCurveToRelative(9.27f, -3.11f, 11f, -7.5f); curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f); close()
                moveTo(12f, 17f); curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f); reflectiveCurveToRelative(2.24f, -5f, 5f, -5f); reflectiveCurveToRelative(5f, 2.24f, 5f, 5f); reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f); close()
                moveTo(12f, 9f); curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f); reflectiveCurveToRelative(1.34f, 3f, 3f, 3f); reflectiveCurveToRelative(3f, -1.34f, 3f, -3f); reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f); close()
            }
        }.build()

    val VisibilityOff: ImageVector
        get() = ImageVector.Builder(name = "VisibilityOff", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7f); curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f); curveToRelative(0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f); lineToRelative(2.92f, 2.92f); curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f); curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f); curveToRelative(-1.4f, 0f, -2.74f, 0.25f, -3.98f, 0.7f); lineToRelative(2.16f, 2.16f); curveToRelative(0.58f, -0.23f, 1.2f, -0.36f, 1.83f, -0.36f); close()
                moveTo(2f, 4.27f); lineToRelative(2.28f, 2.28f); lineToRelative(0.46f, 0.46f); curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f); curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f); curveToRelative(1.55f, 0f, 3.03f, -0.3f, 4.38f, -0.84f); lineToRelative(0.42f, 0.42f); lineTo(19.73f, 22f); lineTo(21f, 20.73f); lineTo(3.27f, 3f); lineTo(2f, 4.27f); close()
                moveTo(7.53f, 9.8f); lineToRelative(1.55f, 1.55f); curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f); curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f); curveToRelative(0.22f, 0f, 0.44f, -0.03f, 0.65f, -0.08f); lineToRelative(1.55f, 1.55f); curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f); curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f); curveToRelative(0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f); close()
                moveTo(11.84f, 9.02f); lineToRelative(3.15f, 3.15f); lineToRelative(0.02f, -0.16f); curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f); lineToRelative(-0.17f, 0.01f); close()
            }
        }.build()

    val Info: ImageVector
        get() = ImageVector.Builder(name = "Info", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); reflectiveCurveToRelative(4.48f, 10f, 10f, 10f); reflectiveCurveToRelative(10f, -4.48f, 10f, -10f); reflectiveCurveTo(17.52f, 2f, 12f, 2f); close()
                moveTo(13f, 17f); horizontalLineToRelative(-2f); verticalLineToRelative(-6f); horizontalLineToRelative(2f); verticalLineToRelative(6f); close()
                moveTo(13f, 9f); horizontalLineToRelative(-2f); verticalLineTo(7f); horizontalLineToRelative(2f); verticalLineToRelative(2f); close()
            }
        }.build()
}