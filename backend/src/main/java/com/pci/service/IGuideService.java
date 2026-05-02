package com.pci.service;

import com.pci.dto.GuideDTO;
import com.pci.dto.Result;

import java.util.List;

public interface IGuideService {
    Result createGuide(GuideDTO.CreateRequest request);

    Result listGuides(String city, String keyword, Integer page, Integer size);

    Result getGuideDetail(Long id);

    Result interact(Long guideId, GuideDTO.ActionRequest request);

    Result recommend(String city, String origin, String destination, Integer topK);

    List<GuideDTO.AiEvidence> searchForAi(String city, String query, Integer topK);
}
