package com.pci.controller;

import com.pci.dto.Result;
import com.pci.dto.TravelDTO;
import com.pci.service.ITravelService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/travel")
public class TravelController {

    @Resource
    private ITravelService travelService;

    @PostMapping("/route")
    public Result planRoute(@RequestBody TravelDTO.RouteRequest request) {
        return travelService.planRoute(request);
    }

    /** AI解析：先预览不执行 */
    @PostMapping("/ai-parse")
    public Result aiParse(@RequestBody TravelDTO.ParseRequest request) {
        return travelService.aiParse(request);
    }

    /** AI确认：用户确认后执行 */
    @PostMapping("/ai-confirm")
    public Result aiConfirm(@RequestBody TravelDTO.ConfirmRequest request) {
        return travelService.aiConfirm(request);
    }

    /** GPS定位：经纬度 -> 地址名称/城市 */
    @PostMapping("/locate")
    public Result locate(@RequestBody TravelDTO.LocateRequest request) {
        return travelService.locate(request);
    }
}
