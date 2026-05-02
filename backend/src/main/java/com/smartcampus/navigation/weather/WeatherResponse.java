package com.smartcampus.navigation.weather;

import java.util.ArrayList;
import java.util.List;

public class WeatherResponse {
    public WeatherLocation location;
    public String updateTime;
    public String source = "QWeather";
    public CurrentWeather now;
    public List<HourlyWeather> hourly = new ArrayList<>();
    public List<DailyWeather> daily = new ArrayList<>();
    public List<WeatherRecommendation> recommendations = new ArrayList<>();

    public static class WeatherLocation {
        public Long poiId;
        public String name;
        public String longitude;
        public String latitude;
        public String locationText;
        public String category;
    }

    public static class CurrentWeather {
        public String obsTime;
        public Integer temp;
        public Integer feelsLike;
        public String text;
        public String icon;
        public String windDir;
        public String windScale;
        public Integer humidity;
        public Double precip;
        public Integer pressure;
        public Integer vis;
    }

    public static class HourlyWeather {
        public String fxTime;
        public Integer temp;
        public String text;
        public String icon;
        public String windDir;
        public String windScale;
        public Integer humidity;
        public Integer pop;
        public Double precip;
    }

    public static class DailyWeather {
        public String fxDate;
        public Integer tempMax;
        public Integer tempMin;
        public String textDay;
        public String textNight;
        public String windDirDay;
        public String windScaleDay;
        public Integer uvIndex;
        public Double precip;
    }

    public static class WeatherRecommendation {
        public String type;
        public String title;
        public String detail;
        public Long poiId;
        public String poiName;
        public String tone;
    }
}
