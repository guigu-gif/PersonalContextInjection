package com.pci.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pci.dto.Result;
import com.pci.entity.Semester;

public interface ISemesterService extends IService<Semester> {
    Result listAll();
    Result create(Semester semester);
    Result setCurrent(Long id);
    Result deleteSemester(Long id);
    Result currentWeek();
}
