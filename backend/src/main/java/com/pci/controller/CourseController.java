package com.pci.controller;

import com.pci.dto.CourseOpDTO;
import com.pci.dto.Result;
import com.pci.entity.Course;
import com.pci.service.ICourseService;
import com.pci.service.IVisionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@RestController
@RequestMapping("/course")
public class CourseController {

    @Resource
    private ICourseService courseService;

    @Resource
    private IVisionService visionService;

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) Long semesterId,
                       @RequestParam(required = false) Integer week) {
        return courseService.listByUser(semesterId, week);
    }

    @PostMapping
    public Result add(@RequestBody Course course) {
        return courseService.addCourse(course);
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        return courseService.remove(id);
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody Course course) {
        return courseService.updateCourse(id, course);
    }

    /** 识图导入：上传课程表图片，返回识别结果（不写库） */
    @PostMapping("/import")
    public Result importSchedule(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return Result.fail("文件不能为空");
        if (file.getSize() > 5 * 1024 * 1024) return Result.fail("图片不能超过5MB");
        CourseOpDTO.ParseResult result = visionService.recognizeSchedule(file);
        return Result.ok(result);
    }

    /** AI解析自然语言指令，返回操作预览（不写库） */
    @PostMapping("/ai-parse")
    public Result aiParse(@RequestBody CourseOpDTO.ParseRequest req) {
        return courseService.aiParse(req);
    }

    /** 用户确认后执行AI操作 */
    @PostMapping("/ai-confirm")
    public Result aiConfirm(@RequestBody CourseOpDTO.ConfirmRequest req) {
        return courseService.aiConfirm(req);
    }
}


