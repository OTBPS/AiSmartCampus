package com.smartcampus.navigation.poi;

import com.smartcampus.navigation.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pois")
public class PoiController {
    private final PoiService poiService;

    public PoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    @GetMapping
    public ApiResponse<List<PoiEntity>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "true") Boolean enabledOnly,
            @RequestParam(defaultValue = "false") Boolean mapOnly,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(poiService.list(keyword, category, tag, enabledOnly, mapOnly, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<PoiEntity> get(@PathVariable Long id) {
        return ApiResponse.ok(poiService.get(id));
    }

    @PostMapping("/admin")
    public ApiResponse<PoiEntity> create(@Valid @RequestBody PoiRequest request) {
        return ApiResponse.ok(poiService.create(request));
    }

    @PutMapping("/admin/{id}")
    public ApiResponse<PoiEntity> update(@PathVariable Long id, @Valid @RequestBody PoiRequest request) {
        return ApiResponse.ok(poiService.update(id, request));
    }

    @DeleteMapping("/admin/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        poiService.delete(id);
        return ApiResponse.ok(true);
    }

    @PutMapping("/admin/{id}/status")
    public ApiResponse<PoiEntity> updateStatus(@PathVariable Long id, @RequestBody PoiStatusRequest request) {
        return ApiResponse.ok(poiService.updateStatus(id, request.openStatus, request.enabled));
    }

    @PostMapping(value = "/admin/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PoiEntity> updateImage(@PathVariable Long id, @RequestParam("image") MultipartFile image) {
        return ApiResponse.ok(poiService.updateImage(id, image));
    }

    @DeleteMapping("/admin/{id}/image")
    public ApiResponse<PoiEntity> deleteImage(@PathVariable Long id) {
        return ApiResponse.ok(poiService.deleteImage(id));
    }

    public static class PoiStatusRequest {
        public String openStatus;
        public Boolean enabled;
    }
}
