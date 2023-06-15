@file:Suppress("unused")

package co.infinum.goldeneye

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.view.TextureView
import androidx.annotation.RequiresPermission
import co.infinum.goldeneye.config.CameraConfig
import co.infinum.goldeneye.config.CameraInfo
import co.infinum.goldeneye.models.CameraApi
import co.infinum.goldeneye.utils.IncompatibleDevicesUtils
import java.io.File

@Suppress("MemberVisibilityCanBePrivate")
interface GoldenEye {
    companion object {
        /**
         * 默认情况下将使用返回的相机API。使用[GoldenEye.Builder.setCameraApi]方法更改相机API。
         *
         * @return 默认情况下使用的首选相机API。
         */
        fun preferredCameraApi(context: Context) = if (shouldUseCamera2Api(context)) CameraApi.CAMERA2 else CameraApi.CAMERA1

        private fun shouldUseCamera2Api(context: Context) =
            isLegacyCamera(context).not() && IncompatibleDevicesUtils.isIncompatibleDevice(Build.MODEL).not()

        /**
         * 将 Legacy 相机与 Camera2 API 结合使用时，问题多于好处。 我发现它在使用已弃用的 Camera1 API 时效果更好。
         *
         * @see CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
         */
        private fun isLegacyCamera(context: Context): Boolean {
            return try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val characteristics = cameraManager?.cameraIdList?.map { cameraManager.getCameraCharacteristics(it) }
                val characteristic = characteristics?.firstOrNull {
                    it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: characteristics?.get(0)
                val hardwareLevel = characteristic?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
            } catch (t: Throwable) {
                false
            }
        }
    }

    /**
     * 可用摄像机列表。一旦GoldenEye实例初始化，列表就可用。
     *
     * @see CameraInfo
     */
    val availableCameras: List<CameraInfo>

    /**
     * 当前打开的摄像头配置。请确保只有在收到[InitCallback.onReady]之后才能访问它。
     *
     * 如果相机处于CLOSED（关闭）或INITIALIZING（初始化）状态，则返回null。
     */
    val config: CameraConfig?

    /**
     * 异步打开相机。
     *
     * @param textureView 将显示相机预览
     * @param cameraInfo  应打开的相机信息
     * @param callback 用于通知相机是否成功初始化
     *
     * @see InitCallback
     *
     * @throws MissingCameraPermissionException 如果缺少摄像头权限
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun open(textureView: TextureView, cameraInfo: CameraInfo, callback: InitCallback)

    /**
     * @see open
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun open(textureView: TextureView, cameraInfo: CameraInfo,
        onReady: ((CameraConfig) -> Unit)? = null, onActive: (() -> Unit)? = null, onError: (Throwable) -> Unit
    )

    /**
     * 不再使用相机时释放资源。它会停止相机，所有回调都将被取消。
     */
    fun release()

    fun setZoomInOrZoomOut(value:Int)

    /**
     * 异步尝试拍照。如果发生任何错误，将调用[PictureCallback.onError]。
     *
     * @see PictureCallback
     */
    fun takePicture(callback: PictureCallback)

    /**
     * @see takePicture
     */
    fun takePicture(onPictureTaken: (Bitmap) -> Unit, onError: (Throwable) -> Unit, onShutter: (() -> Unit)? = null)

    /**
     * 异步尝试录制视频。如果发生任何错误，将调用[VideoCallback.onError]。
     *
     * 注意：外部源当前不支持录制视频！
     *
     * @param file 将用于存储记录
     *
     * @see VideoCallback
     */
    fun startRecording(file: File, callback: VideoCallback)

    /**
     * @see startRecording
     */
    fun startRecording(file: File, onVideoRecorded: (File) -> Unit, onError: (Throwable) -> Unit)

    /**
     * 停止视频录制。
     */
    fun stopRecording()

    class Builder(private val activity: Activity) {

        private var logger: Logger? = null
        private var onZoomChangedCallback: OnZoomChangedCallback? = null
        private var onFocusChangedCallback: OnFocusChangedCallback? = null
        private var pictureTransformation: PictureTransformation? = PictureTransformation.Default
        private var advancedFeaturesEnabled = false
        private var cameraApi: CameraApi? = null

        /**
         * @see Logger
         */
        fun setLogger(onMessage: (String) -> Unit, onThrowable: (Throwable) -> Unit) = apply {
            this.logger = object : Logger {
                override fun log(message: String) {
                    onMessage(message)
                }

                override fun log(t: Throwable) {
                    onThrowable(t)
                }
            }
        }

        /**
         * @see OnZoomChangedCallback
         */
        fun setOnZoomChangedCallback(onZoomChanged: (Int) -> Unit) = apply {
            this.onZoomChangedCallback = object : OnZoomChangedCallback {
                override fun onZoomChanged(zoom: Int) {
                    onZoomChanged(zoom)
                }
            }
        }

        /**
         * @see OnFocusChangedCallback
         */
        fun setOnFocusChangedCallback(onFocusChanged: (Point) -> Unit) = apply {
            this.onFocusChangedCallback = object : OnFocusChangedCallback {
                override fun onFocusChanged(point: Point) {
                    onFocusChanged(point)
                }
            }
        }

        /**
         * @see PictureTransformation
         */
        fun setPictureTransformation(transform: (Bitmap, CameraConfig, Float) -> Bitmap) = apply {
            this.pictureTransformation = object : PictureTransformation {
                override fun transform(picture: Bitmap, config: CameraConfig, orientationDifference: Float) =
                    transform(picture, config, orientationDifference)
            }
        }

        /**
         * @see Logger
         */
        fun setLogger(logger: Logger) = apply { this.logger = logger }

        /**
         * @see OnZoomChangedCallback
         */
        fun setOnZoomChangedCallback(callback: OnZoomChangedCallback) = apply { this.onZoomChangedCallback = callback }

        /**
         * @see OnFocusChangedCallback
         */
        fun setOnFocusChangedCallback(callback: OnFocusChangedCallback) = apply { this.onFocusChangedCallback = callback }

        /**
         * @see PictureTransformation
         */
        fun setPictureTransformation(transformation: PictureTransformation) = apply { this.pictureTransformation = transformation }

        /**
         * 启用[co.infinum.goldeneye.config.AdvancedFeatureConfig]功能的使用。这些功能是实验性的，默认情况下是禁用的。如果您试图更改任何高级功能的值，除非您启用它们，否则它将被忽略。
         */
        fun withAdvancedFeatures() = apply { this.advancedFeaturesEnabled = true }

        /**
         * 手动选择相机API版本。
         *
         * 如果应用程序不使用视频录制功能，强制使用Camera1 API可能会很有用，因为使用[co.infinum.goldeneye.models.FlashMode.ON]拍摄照片时，它会更加一致。
         *
         * 注意：使用Camera1 API时，较新Android设备上的视频录制可能会崩溃！
         *
         * @throws IllegalArgumentException when trying to force Camera2 API on devices older than Lollipop.
         */
        @Throws(IllegalArgumentException::class)
        fun setCameraApi(cameraApi: CameraApi): Builder {
            return apply { this.cameraApi = cameraApi }
        }

        /**
         * 构建GoldenEye实现。为早于LOLLIPOP的设备和使用LEGACY相机的设备构建Camera1 API包装，否则将构建Camera2 API包装。
         */
        @SuppressLint("NewApi")
        fun build(): GoldenEye {
            val selectedCameraApi = this.cameraApi ?: preferredCameraApi(activity)

            return when (selectedCameraApi) {
                CameraApi.CAMERA1 -> GoldenEye1Impl(
                    activity, advancedFeaturesEnabled, onZoomChangedCallback,
                    onFocusChangedCallback, pictureTransformation, logger
                )
                CameraApi.CAMERA2 -> GoldenEye2Impl(
                    activity, advancedFeaturesEnabled, onZoomChangedCallback,
                    onFocusChangedCallback, pictureTransformation, logger
                )
            }
        }

    }
}
