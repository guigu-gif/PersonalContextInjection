package com.pci.service;

import com.pci.dto.CourseOpDTO;
import org.springframework.web.multipart.MultipartFile;

public interface IVisionService {
    /** 识别课程表图片，返回解析结果（不写库） */
    CourseOpDTO.ParseResult recognizeSchedule(MultipartFile file);
}
