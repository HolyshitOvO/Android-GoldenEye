@file:Suppress("DEPRECATION")

package co.infinum.goldeneye.utils

import android.app.Activity
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.view.Surface
import android.view.TextureView
import co.infinum.goldeneye.BaseGoldenEye
import co.infinum.goldeneye.config.CameraConfig
import co.infinum.goldeneye.config.CameraInfo
import co.infinum.goldeneye.config.camera2.Camera2ConfigImpl
import co.infinum.goldeneye.extensions.isNotMeasured
import co.infinum.goldeneye.models.CameraApi
import co.infinum.goldeneye.models.Facing
import co.infinum.goldeneye.models.PreviewScale
import co.infinum.goldeneye.models.Size
import kotlin.math.max
import kotlin.math.min

internal object CameraUtils {
    private const val FOCUS_AREA_SIZE = 200

    /**
     * 相机和设备方向不同步。此方法将计算它们的方向差，以便可用于手动同步它们。
     */
    fun calculateDisplayOrientation(activity: Activity, config: CameraInfo): Int {
        val deviceOrientation = getDeviceOrientation(activity)
        val cameraOrientation = config.orientation

        return if (config.facing == Facing.FRONT) {
            (360 - (cameraOrientation + deviceOrientation) % 360) % 360
        } else {
            // (cameraOrientation - deviceOrientation + 360 + 90) % 360
            deviceOrientation
        }
    }

    /**
     * 这是转换预览以使其不失真的所必需的。
     *
     * 查看图片目录
     * 1） 默认行为是相机预览将缩放以填充给定的视图相机预览 100x100 将缩放到 100x200，这将导致图像失真
     * 2）我们必须否定默认比例行为才能获得不失真的真实预览大小
     *
     * 3） 应用黄金眼量表
     *
     * 在我们逆转该过程后，我们可以根据需要扩展预览，以当前配置的预览规模。
     */
    fun calculateTextureMatrix(activity: Activity, textureView: TextureView, config: CameraConfig): Matrix {
        val matrix = Matrix()
        val previewSize = config.previewSize
        if (textureView.isNotMeasured() || previewSize == Size.UNKNOWN) {
            return matrix.apply { postScale(0f, 0f) }
        }

        /* scaleX 和 scaleY 用于反转过程，比例用于根据预览比例缩放图像*/
        val (scaleX, scaleY, scale) = calculateScale(activity, textureView, config)

        if (BaseGoldenEye.version == CameraApi.CAMERA2 && getDeviceOrientation(activity) % 180 != 0) {
            matrix.postScale(
                textureView.height / textureView.width.toFloat() / scaleY * scale,
                textureView.width / textureView.height.toFloat() / scaleX * scale,
                textureView.width / 2f,
                textureView.height / 2f
            )
            val rotation = calculateDisplayOrientation(activity, config).toFloat() - config.orientation
            matrix.postRotate(
                if (config.facing == Facing.FRONT) -rotation else rotation,
                textureView.width / 2f,
                textureView.height / 2f
            )
        } else {
            matrix.postScale(1 / scaleX * scale, 1 / scaleY * scale, textureView.width / 2f, textureView.height / 2f)
        }

        return matrix
    }

    fun calculateCamera2FocusArea(
        activity: Activity,
        textureView: TextureView,
        config: Camera2ConfigImpl,
        x: Float,
        y: Float
    ): Rect? {
        val rect = calculateFocusRect(activity, textureView, config, x, y) ?: return null
        /* 获取活动的矩形大小。这对应于 Camera2 API 看到的实际相机大小*/
        val activeRect = config.characteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
        val scaleX = activeRect.width().toFloat() / config.previewSize.width
        val scaleY = activeRect.height().toFloat() / config.previewSize.height
        return Rect(
            (scaleX * rect.left).toInt(),
            (scaleY * rect.top).toInt(),
            (scaleX * rect.right).toInt(),
            (scaleY * rect.bottom).toInt()
        )
    }

    /**
     * This... oh...
     *
     * There are 5 different values in here that we must sync. Preview size, Texture view size,
     * Real preview size that is actually displayed inside texture view after applying scale matrix,
     * actual touch point which we want to focus on and finally, the f****** genius, focus area
     * that must use coordinates [-1000, 1000] and doesn't give a crap about your preview size
     * or orientation.
     *
     * What we must accomplish is scale x,y coordinates that user pressed to [-1000, 1000] coordinates
     * and manually do all the scaling and potential rotation because camera and device orientation
     * is not in sync.
     */
    fun calculateCamera1FocusArea(
        activity: Activity,
        textureView: TextureView,
        config: CameraConfig,
        x: Float,
        y: Float
    ): List<Camera.Area>? {

        val rect = calculateFocusRect(activity, textureView, config, x, y) ?: return null

        val previewSize = config.previewSize
        /* 天才 [-1000，1000] 坐标与缩放预览大小的比率*/
        val cameraWidthRatio = 2000f / previewSize.width
        val cameraHeightRatio = 2000f / previewSize.height

        /* 测量左矩形和顶部矩形点*/
        val left = (cameraWidthRatio * rect.left - 1000).coerceIn(-1000f, 1000f - FOCUS_AREA_SIZE).toInt()
        val top = (cameraHeightRatio * rect.height() - 1000).coerceIn(-1000f, 1000f - FOCUS_AREA_SIZE).toInt()
        val right = min(left + FOCUS_AREA_SIZE, 1000)
        val bottom = min(top + FOCUS_AREA_SIZE, 1000)

        return listOf(Camera.Area(Rect(left, top, right, bottom), 1000))
    }

    fun findBestMatchingSize(referenceSize: Size, availableSizes: List<Size>): Size =
        availableSizes.find { it.aspectRatio == referenceSize.aspectRatio } ?: availableSizes.getOrNull(0) ?: Size.UNKNOWN

    private fun touchNotInPreview(
        rotatedTextureViewX: Int,
        rotatedTextureViewY: Int,
        scaledPreviewX: Float,
        scaledPreviewY: Float,
        x: Float,
        y: Float
    ): Boolean {

        val diffX = max(0f, (rotatedTextureViewX - scaledPreviewX) / 2)
        val diffY = max(0f, (rotatedTextureViewY - scaledPreviewY) / 2)
        return x < diffX || x > diffX + scaledPreviewX || y < diffY || y > diffY + scaledPreviewY
    }

    private fun calculateScale(activity: Activity, textureView: TextureView, config: CameraConfig): Triple<Float, Float, Float> {
        val previewSize = config.previewSize
        val displayOrientation = calculateDisplayOrientation(activity, config)
        val scaleX =
            if (displayOrientation % 180 == 0) {
                textureView.width.toFloat() / previewSize.width
            } else {
                textureView.width.toFloat() / previewSize.height
            }

        val scaleY =
            if (displayOrientation % 180 == 0) {
                textureView.height.toFloat() / previewSize.height
            } else {
                textureView.height.toFloat() / previewSize.width
            }

        val scale =
            when (config.previewScale) {
                PreviewScale.MANUAL_FILL,
                PreviewScale.AUTO_FILL -> max(scaleX, scaleY)
                PreviewScale.MANUAL_FIT,
                PreviewScale.AUTO_FIT -> min(scaleX, scaleY)
                PreviewScale.MANUAL -> 1f
            }

        return Triple(scaleX, scaleY, scale)
    }

    private fun getDeviceOrientation(activity: Activity) =
        when (activity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_0 -> 0
            else -> 0
        }

    private fun calculateFocusRect(
        activity: Activity,
        textureView: TextureView,
        config: CameraConfig,
        x: Float,
        y: Float
    ): Rect? {

        val (_, _, scale) = calculateScale(activity, textureView, config)
        val displayOrientation = calculateDisplayOrientation(activity, config)
        val previewSize = config.previewSize
        val textureViewSize = Size(textureView.width, textureView.height)
        /* 计算实际缩放的预览大小*/
        val scaledPreviewX = previewSize.width * scale
        val scaledPreviewY = previewSize.height * scale

        /* 将纹理视图方向与相机方向同步*/
        val rotatedTextureViewX = if (displayOrientation % 180 == 0) textureViewSize.width else textureViewSize.height
        val rotatedTextureViewY = if (displayOrientation % 180 == 0) textureViewSize.height else textureViewSize.width

        /* 将纹理视图 x，y 转换为相机 x，y*/
        val rotatedX = when (displayOrientation) {
            90 -> y
            180 -> textureViewSize.width - x
            270 -> textureViewSize.height - y
            else -> x
        }
        val rotatedY = when (displayOrientation) {
            90 -> textureViewSize.width - x
            180 -> textureViewSize.height - y
            270 -> x
            else -> y
        }

        if (touchNotInPreview(rotatedTextureViewX, rotatedTextureViewY, scaledPreviewX, scaledPreviewY, rotatedX, rotatedY)) {
            return null
        }

        /* 将相机 x，y 转换为预览 x，y，如果预览不是全屏或屏幕外缩放，则转换 x，y*/
        val translatedPreviewX = rotatedX - max(0f, (rotatedTextureViewX - scaledPreviewX) / 2)
        val translatedPreviewY = rotatedY - max(0f, (rotatedTextureViewY - scaledPreviewY) / 2)

        val rectWidth = previewSize.width * 0.1f
        val rectHeight = previewSize.height * 0.1f
        val left = (translatedPreviewX / scale - rectWidth / 2).coerceIn(0f, previewSize.width - rectWidth).toInt()
        val top = (translatedPreviewY / scale - rectHeight / 2).coerceIn(0f, previewSize.height - rectHeight).toInt()
        val right = min(left + rectWidth.toInt(), previewSize.width - 1)
        val bottom = min(top + rectHeight.toInt(), previewSize.height - 1)

        return Rect(left, top, right, bottom)
    }

    /**
     * 通过缩放活动矩形与缩放比率来计算缩放矩形。
     */
    fun calculateCamera2ZoomRect(config: Camera2ConfigImpl): Rect? {
        val activeRect = config.characteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
        val zoomPercentage = config.zoom / 100f

        /* 测量实际缩放宽度和缩放高度*/
        val zoomedWidth = (activeRect.width() / zoomPercentage).toInt()
        val zoomedHeight = (activeRect.height() / zoomPercentage).toInt()
        val halfWidthDiff = (activeRect.width() - zoomedWidth) / 2
        val halfHeightDiff = (activeRect.height() - zoomedHeight) / 2

        /* 创建缩放矩形*/
        return Rect(
            max(0, halfWidthDiff),
            max(0, halfHeightDiff),
            min(halfWidthDiff + zoomedWidth, activeRect.width() - 1),
            min(halfHeightDiff + zoomedHeight, activeRect.height() - 1)
        )
    }
}
