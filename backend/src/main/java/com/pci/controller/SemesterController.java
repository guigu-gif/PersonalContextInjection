package com.pci.controller;

import com.pci.dto.Result;
import com.pci.entity.Semester;
import com.pci.service.ISemesterService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/semester")
public class SemesterController {

    @Resource
    private ISemesterService semesterService;

    @GetMapping("/list")
    public Result list() { return semesterService.listAll(); }

    @PostMapping
    public Result create(@RequestBody Semester semester) { return semesterService.create(semester); }

    @PutMapping("/{id}/current")
    public Result setCurrent(@PathVariable Long id) { return semesterService.setCurrent(id); }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) { return semesterService.deleteSemester(id); }

    @GetMapping("/current-week")
    public Result currentWeek() { return semesterService.currentWeek(); }
}
