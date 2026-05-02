package com.pci.controller;

import com.pci.dto.MemoDTO;
import com.pci.dto.Result;
import com.pci.entity.Memo;
import com.pci.service.IMemoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/memo")
public class MemoController {

    @Resource
    private IMemoService memoService;

    @GetMapping
    public Result list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer hasRemind) {
        return memoService.listMemos(page, size, status, keyword, hasRemind);
    }

    @PostMapping
    public Result add(@RequestBody Memo memo) {
        return memoService.addMemo(memo);
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody Memo memo) {
        return memoService.updateMemo(id, memo);
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        return memoService.deleteMemo(id);
    }

    @PutMapping("/{id}/complete")
    public Result complete(@PathVariable Long id) {
        return memoService.completeMemo(id);
    }

    @PutMapping("/{id}/uncomplete")
    public Result uncomplete(@PathVariable Long id) {
        return memoService.uncompleteMemo(id);
    }

    @PostMapping("/ai-parse")
    public Result aiParse(@Valid @RequestBody MemoDTO.ParseRequest request) {
        return memoService.aiParse(request);
    }

    @PostMapping("/ai-confirm")
    public Result aiConfirm(@Valid @RequestBody MemoDTO.ConfirmRequest request) {
        return memoService.aiConfirm(request);
    }

    @GetMapping("/search")
    public Result search(@RequestParam String q) {
        return memoService.searchMemos(q);
    }
}
