package com.pci.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pci.dto.CourseOpDTO;
import com.pci.dto.Result;
import com.pci.entity.Course;

public interface ICourseService extends IService<Course> {
    Result listByUser(Long semesterId, Integer week);
    Result addCourse(Course course);
    Result remove(Long id);
    Result updateCourse(Long id, Course course);
    Result aiParse(CourseOpDTO.ParseRequest req);
    Result aiConfirm(CourseOpDTO.ConfirmRequest req);
}
