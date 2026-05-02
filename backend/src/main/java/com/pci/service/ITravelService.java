package com.pci.service;

import com.pci.dto.Result;
import com.pci.dto.TravelDTO;

public interface ITravelService {
    Result planRoute(TravelDTO.RouteRequest request);
    Result aiParse(TravelDTO.ParseRequest request);
    Result aiConfirm(TravelDTO.ConfirmRequest request);
    Result locate(TravelDTO.LocateRequest request);
}
