package co.infinum.goldeneye.config

import co.infinum.goldeneye.IllegalCharacteristicsException

/**
 * 主摄像头配置界面。它包装所有较小的接口只是为了拆分实现逻辑。有关详细文档，请查看扩展接口。
 */
interface CameraConfig :
    CameraInfo,
    VideoConfig,
    BasicFeatureConfig,
    AdvancedFeatureConfig,
    SizeConfig,
    ZoomConfig

internal abstract class CameraConfigImpl<T : Any>(
    var cameraInfo: CameraInfo,
    var videoConfig: BaseVideoConfig<T>,
    var basicFeatureConfig: BaseBasicFeatureConfig<T>,
    var advancedFeatureConfig: BaseAdvancedFeatureConfig<T>,
    var sizeConfig: BaseSizeConfig<T>,
    var zoomConfig: BaseZoomConfig<T>
) : CameraConfig,
    CameraInfo by cameraInfo,
    VideoConfig by videoConfig,
    BasicFeatureConfig by basicFeatureConfig,
    AdvancedFeatureConfig by advancedFeatureConfig,
    SizeConfig by sizeConfig,
    ZoomConfig by zoomConfig {

    var characteristics: T? = null
        set(value) {
            field = value
            if (value != null) {
                sizeConfig.characteristics = value
                videoConfig.characteristics = value
                basicFeatureConfig.characteristics = value
                advancedFeatureConfig.characteristics = value
                zoomConfig.characteristics = value
            } else {
                throw IllegalCharacteristicsException
            }
        }
}
