package com.pci.controller;

import com.pci.dto.GuideDTO;
import com.pci.dto.Result;
import com.pci.service.IGuideService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/guide")
public class GuideController {

    @Resource
    private IGuideService guideService;

    @PostMapping
    public Result create(@RequestBody GuideDTO.CreateRequest request) {
        return guideService.createGuide(request);
    }

    @GetMapping
    public Result list(@RequestParam(required = false) String city,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size) {
        return guideService.listGuides(city, keyword, page, size);
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id) {
        return guideService.getGuideDetail(id);
    }

    @PostMapping("/{id}/action")
    public Result action(@PathVariable Long id, @RequestBody GuideDTO.ActionRequest request) {
        return guideService.interact(id, request);
    }

    @GetMapping("/recommend")
    public Result recommend(@RequestParam String city,
                            @RequestParam(required = false) String origin,
                            @RequestParam(required = false) String destination,
                            @RequestParam(defaultValue = "5") Integer topK) {
        return guideService.recommend(city, origin, destination, topK);
    }
}
