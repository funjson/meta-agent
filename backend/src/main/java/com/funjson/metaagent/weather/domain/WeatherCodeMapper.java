package com.funjson.metaagent.weather.domain;

/**
 * Maps Open-Meteo WMO weather codes into concise Chinese labels.
 */
public final class WeatherCodeMapper {

    /**
     * Utility class; do not instantiate.
     */
    private WeatherCodeMapper() {
    }

    /**
     * Converts a WMO weather code into a human-readable condition.
     *
     * @param code WMO weather code
     * @return Chinese weather condition
     */
    public static String condition(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1, 2 -> "晴间多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61, 63, 65 -> "雨";
            case 66, 67 -> "冻雨";
            case 71, 73, 75 -> "雪";
            case 77 -> "雪粒";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "未知天气";
        };
    }
}
