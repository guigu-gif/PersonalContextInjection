package com.pci.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.Semester;
import com.pci.mapper.SemesterMapper;
import com.pci.service.ISemesterService;
import com.pci.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SemesterServiceImpl extends ServiceImpl<SemesterMapper, Semester> implements ISemesterService {

    @Override
    public Result listAll() {
        UserDTO user = UserHolder.getUser();
        List<Semester> list = lambdaQuery().eq(Semester::getUserId, user.getId())
                .orderByDesc(Semester::getIsCurrent)
                .orderByDesc(Semester::getId)
                .list();
        return Result.ok(list);
    }

    @Override
    public Result create(Semester semester) {
        UserDTO user = UserHolder.getUser();
        semester.setId(null);
        semester.setUserId(user.getId());
        if (semester.getTotalWeeks() == null) semester.setTotalWeeks(20);
        semester.setIsCurrent(0);
        save(semester);
        return Result.ok(semester.getId());
    }

    @Override
    @Transactional
    public Result setCurrent(Long id) {
        UserDTO user = UserHolder.getUser();
        // 先清除该用户所有 is_current
        lambdaUpdate().eq(Semester::getUserId, user.getId()).set(Semester::getIsCurrent, 0).update();
        // 设置目标学期
        boolean ok = lambdaUpdate()
                .eq(Semester::getId, id)
                .eq(Semester::getUserId, user.getId())
                .set(Semester::getIsCurrent, 1)
                .update();
        return ok ? Result.ok() : Result.fail("学期不存在");
    }

    @Override
    public Result deleteSemester(Long id) {
        UserDTO user = UserHolder.getUser();
        boolean ok = lambdaUpdate()
                .eq(Semester::getId, id)
                .eq(Semester::getUserId, user.getId())
                .remove();
        return ok ? Result.ok() : Result.fail("删除失败");
    }

    @Override
    public Result currentWeek() {
        UserDTO user = UserHolder.getUser();
        Semester current = lambdaQuery()
                .eq(Semester::getUserId, user.getId())
                .eq(Semester::getIsCurrent, 1)
                .one();
        if (current == null) return Result.ok(null);

        long days = ChronoUnit.DAYS.between(current.getStartDate(), LocalDate.now());
        int week = (int) Math.max(1, Math.min(current.getTotalWeeks(), days / 7 + 1));

        Map<String, Object> data = new HashMap<>();
        data.put("week", week);
        data.put("totalWeeks", current.getTotalWeeks());
        data.put("semesterId", current.getId());
        data.put("semesterName", current.getName());
        return Result.ok(data);
    }
}
