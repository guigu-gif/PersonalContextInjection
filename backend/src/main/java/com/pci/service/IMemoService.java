package com.pci.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pci.dto.MemoDTO;
import com.pci.dto.Result;
import com.pci.entity.Memo;

public interface IMemoService extends IService<Memo> {
    Result listMemos(Integer page, Integer size, String status, String keyword, Integer hasRemind);

    Result addMemo(Memo memo);

    Result updateMemo(Long id, Memo memo);

    Result deleteMemo(Long id);

    Result completeMemo(Long id);

    Result uncompleteMemo(Long id);

    Result aiParse(MemoDTO.ParseRequest request);

    Result aiConfirm(MemoDTO.ConfirmRequest request);

    Result searchMemos(String query);
}
