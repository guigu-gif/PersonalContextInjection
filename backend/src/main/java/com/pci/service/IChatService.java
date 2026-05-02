package com.pci.service;

import com.pci.dto.ChatImageDTO;
import com.pci.dto.Result;

import java.util.List;
import java.util.Map;

public interface IChatService {
    Result chat(String message, List<Map<String, String>> history);
    Result buildContextSummary();
    Result imageRoute(ChatImageDTO.ImageRouteRequest request);
}
