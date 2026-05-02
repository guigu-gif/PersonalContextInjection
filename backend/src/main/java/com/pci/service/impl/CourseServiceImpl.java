package com.pci.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pci.dto.CourseOpDTO;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.Course;
import com.pci.mapper.CourseMapper;
import com.pci.service.ICourseService;
import com.pci.utils.RedisConstants;
import com.pci.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements ICourseService {

    @Resource
    private CourseRemindHelper remindHelper;

    @Override
    public Result listByUser(Long semesterId, Integer week) {
        UserDTO user = UserHolder.getUser();
        List<Course> list = lambdaQuery()
                .eq(Course::getUserId, user.getId())
                .eq(semesterId != null, Course::getSemesterId, semesterId)
                .le(week != null, Course::getWeekStart, week)
                .ge(week != null, Course::getWeekEnd, week)
                .list();
        return Result.ok(list);
    }

    @Override
    public Result addCourse(Course course) {
        UserDTO user = UserHolder.getUser();
        course.setUserId(user.getId());
        save(course);
        remindHelper.scheduleNext(course);
        return Result.ok(course.getId());
    }

    @Override
    public Result remove(Long id) {
        UserDTO user = UserHolder.getUser();
        boolean ok = lambdaUpdate()
                .eq(Course::getId, id)
                .eq(Course::getUserId, user.getId())
                .remove();
        if (ok) remindHelper.cancel(id);
        return ok ? Result.ok() : Result.fail("删除失败");
    }

    @Override
    public Result updateCourse(Long id, Course course) {
        UserDTO user = UserHolder.getUser();
        Course existing = lambdaQuery().eq(Course::getId, id).eq(Course::getUserId, user.getId()).one();
        if (existing == null) return Result.fail("课程不存在");
        course.setId(id);
        course.setUserId(user.getId());
        updateById(course);
        remindHelper.scheduleNext(course);
        return Result.ok();
    }

    @Override
    public Result aiParse(CourseOpDTO.ParseRequest req) {
        // TODO: 接入千问视觉/对话API后在此处调用，解析 req.getInstruction()
        // 当前返回空结果，前端会显示"AI暂未接入"提示
        CourseOpDTO.ParseResult result = new CourseOpDTO.ParseResult();
        result.setOps(new ArrayList<>());
        List<String> unresolved = new ArrayList<>();
        unresolved.add("AI接口暂未接入，请手动添加课程");
        result.setUnresolved(unresolved);
        return Result.ok(result);
    }

    @Override
    @Transactional
    public Result aiConfirm(CourseOpDTO.ConfirmRequest req) {
        UserDTO user = UserHolder.getUser();
        List<String> failed = new ArrayList<>();
        int executed = 0;

        for (CourseOpDTO.ConfirmOp op : req.getOps()) {
            try {
                switch (op.getAction()) {
                    case "CREATE":
                        Course c = op.getCourse();
                        c.setId(null);
                        c.setUserId(user.getId());
                        save(c);
                        remindHelper.scheduleNext(c);
                        executed++;
                        break;
                    case "DELETE":
                        boolean ok = lambdaUpdate()
                                .eq(Course::getId, op.getMatchedId())
                                .eq(Course::getUserId, user.getId())
                                .remove();
                        if (ok) {
                            remindHelper.cancel(op.getMatchedId());
                            executed++;
                        } else failed.add("课程id=" + op.getMatchedId() + " 不存在或无权操作");
                        break;
                    case "UPDATE":
                        Course existing = getById(op.getMatchedId());
                        if (existing == null || !existing.getUserId().equals(user.getId())) {
                            failed.add("课程id=" + op.getMatchedId() + " 不存在或无权操作");
                            break;
                        }
                        Course updated = op.getCourse();
                        updated.setId(op.getMatchedId());
                        updated.setUserId(user.getId());
                        updateById(updated);
                        remindHelper.scheduleNext(updated);
                        executed++;
                        break;
                    default:
                        failed.add("未知操作类型: " + op.getAction());
                }
            } catch (Exception e) {
                failed.add("操作失败: " + e.getMessage());
            }
        }

        CourseOpDTO.ConfirmResult result = new CourseOpDTO.ConfirmResult();
        result.setExecuted(executed);
        result.setFailed(failed);
        return Result.ok(result);
    }
}

