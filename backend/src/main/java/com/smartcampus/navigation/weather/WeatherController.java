package com.smartcampus.navigation.weather;

import com.smartcampus.navigation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {
    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/campus")
    public ApiResponse<WeatherResponse> campus(
            @RequestParam(required = false) Long poiId,
            @RequestHeader(name = "Accept-Language", required = false, defaultValue = "zh-CN") String locale
    ) {
        return ApiResponse.ok(weatherService.campus(poiId, locale));
    }
}
