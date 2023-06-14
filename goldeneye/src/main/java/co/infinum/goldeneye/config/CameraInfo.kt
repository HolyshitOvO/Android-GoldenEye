package co.infinum.goldeneye.config

import co.infinum.goldeneye.models.Facing

/**
 * 相机 API 用于打开相机的常规相机信息。
 */
interface CameraInfo {
    /**
     * Camera ID.
     */
    val id: String

    /**
     * 相机方向。相机有自己的方向，与设备方向不同步。
     */
    val orientation: Int

    /**
     * 相机朝向可以是正面、背面或外部。外部摄像头的处理方式大多与后置摄像头相同。
     */
    val facing: Facing
}
