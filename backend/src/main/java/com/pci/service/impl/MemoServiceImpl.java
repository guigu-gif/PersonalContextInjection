package com.pci.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pci.dto.MemoDTO;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.Memo;
import com.pci.mapper.MemoMapper;
import com.pci.service.IMemoService;
import com.pci.utils.RedisConstants;
import com.pci.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.annotation.PreDestroy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MemoServiceImpl extends ServiceImpl<MemoMapper, Memo> implements IMemoService {

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private QdrantService qdrantService;

    private static final int STATUS_TODO = 0;
    private static final int STATUS_DONE = 1;
    private static final int MAX_INSTRUCTION_LENGTH = 200;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final java.util.regex.Pattern UNSAFE_TAG_PATTERN =
            java.util.regex.Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");
    private static final java.util.regex.Pattern UNSAFE_PROTOCOL_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\b(javascript|data|vbscript)\\s*:");

    private static final ExecutorService INDEX_EXECUTOR = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200),
            r -> {
                Thread t = new Thread(r);
                t.setName("memo-index-pool");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private static final Pattern CLOCK_PATTERN = Pattern.compile(
            "(\\u4e0a\\u5348|\\u65e9\\u4e0a|\\u4e2d\\u5348|\\u4e0b\\u5348|\\u665a\\u4e0a|\\u4eca\\u665a)?\\s*(\\d{1,2})(?:[:\\uFF1A\\u70B9](\\d{1,2}))?(\\u534A)?"
    );
    private static final Pattern WEEKDAY_PATTERN = Pattern.compile("\\u5468([\\u4e00\\u4e8c\\u4e09\\u56db\\u4e94\\u516d\\u65e5\\u5929])");

    // 中文数字 → 阿拉伯数字，长串优先，仅在"点/时"前生效
    private static final Map<String, String> CN_TIME_MAP;
    static {
        CN_TIME_MAP = new LinkedHashMap<>();
        CN_TIME_MAP.put("二十三", "23"); CN_TIME_MAP.put("二十二", "22"); CN_TIME_MAP.put("二十一", "21");
        CN_TIME_MAP.put("二十", "20"); CN_TIME_MAP.put("十九", "19"); CN_TIME_MAP.put("十八", "18");
        CN_TIME_MAP.put("十七", "17"); CN_TIME_MAP.put("十六", "16"); CN_TIME_MAP.put("十五", "15");
        CN_TIME_MAP.put("十四", "14"); CN_TIME_MAP.put("十三", "13"); CN_TIME_MAP.put("十二", "12");
        CN_TIME_MAP.put("十一", "11"); CN_TIME_MAP.put("十", "10");
        CN_TIME_MAP.put("两", "2"); CN_TIME_MAP.put("九", "9"); CN_TIME_MAP.put("八", "8");
        CN_TIME_MAP.put("七", "7"); CN_TIME_MAP.put("六", "6"); CN_TIME_MAP.put("五", "5");
        CN_TIME_MAP.put("四", "4"); CN_TIME_MAP.put("三", "3"); CN_TIME_MAP.put("二", "2"); CN_TIME_MAP.put("一", "1");
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result listMemos(Integer page, Integer size, String status, String keyword, Integer hasRemind) {
        UserDTO user = UserHolder.getUser();
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int offset = (pageNo - 1) * pageSize;

        LambdaQueryWrapper<Memo> wrapper = baseQuery(user.getId(), status, keyword, hasRemind)
                .orderByDesc(Memo::getUpdatedTime)
                .last("limit " + offset + "," + pageSize);

        List<Memo> list = list(wrapper);
        long total = count(baseQuery(user.getId(), status, keyword, hasRemind));
        return Result.ok(list, total);
    }

    @Override
    public Result addMemo(Memo memo) {
        UserDTO user = UserHolder.getUser();
        if (memo == null || isBlank(memo.getContent())) {
            return Result.fail("内容不能为空");
        }
        normalizeMemoForSave(memo, user.getId(), null);
        save(memo);
        syncRemindQueue(memo);
        indexAsync(memo, user.getId());
        return Result.ok(memo);
    }

    @Override
    public Result updateMemo(Long id, Memo memo) {
        UserDTO user = UserHolder.getUser();
        Memo existing = ownedMemo(id, user.getId());
        if (existing == null) {
            return Result.fail("备忘录不存在");
        }
        if (memo == null || isBlank(memo.getContent())) {
            return Result.fail("内容不能为空");
        }
        normalizeMemoForSave(memo, user.getId(), existing);
        memo.setId(id);
        memo.setCreatedTime(existing.getCreatedTime());
        updateById(memo);
        syncRemindQueue(memo);
        indexAsync(memo, user.getId());
        return Result.ok(memo);
    }

    @Override
    public Result deleteMemo(Long id) {
        UserDTO user = UserHolder.getUser();
        Memo existing = ownedMemo(id, user.getId());
        if (existing == null) {
            return Result.fail("备忘录不存在");
        }
        boolean removed = removeById(id);
        if (!removed) {
            return Result.fail("删除失败");
        }
        removeFromQueue(id);
        qdrantService.deleteMemo(id);
        return Result.ok();
    }

    @Override
    public Result completeMemo(Long id) {
        return updateStatus(id, STATUS_DONE);
    }

    @Override
    public Result uncompleteMemo(Long id) {
        return updateStatus(id, STATUS_TODO);
    }

    @Override
    public Result aiParse(MemoDTO.ParseRequest request) {
        if (request == null || isBlank(request.getInstruction())) {
            return Result.fail("指令不能为空");
        }

        UserDTO user = UserHolder.getUser();
        String instruction = request.getInstruction().trim();
        Long userId = user != null ? user.getId() : null;
        log.info("[AUDIT][MEMO][PARSE][START] userId={}, instruction={}", userId, instruction);
        if (instruction.length() > MAX_INSTRUCTION_LENGTH) {
            log.warn("[AUDIT][MEMO][PARSE][REJECT] userId={}, reason=instruction_too_long, len={}",
                    userId, instruction.length());
            return Result.fail("指令过长，请控制在" + MAX_INSTRUCTION_LENGTH + "字以内");
        }
        String normalized = instruction.toLowerCase(Locale.ROOT);

        MemoDTO.ParseResult result = new MemoDTO.ParseResult();
        result.setUnresolved(new ArrayList<String>());
        result.setOps(new ArrayList<MemoDTO.MemoOp>());
        result.setQueryResults(new ArrayList<Memo>());

        if (isWeakIntentOnly(instruction)) {
            result.getUnresolved().add("你的意图较模糊，请补充是“查询/修改/删除/完成”哪一种操作");
            result.setSummary("暂不执行，等待你补充更明确指令");
            log.info("[AUDIT][MEMO][PARSE][END] userId={}, mode=weak_intent, unresolved={}",
                    userId, result.getUnresolved().size());
            return Result.ok(result);
        }

        if (containsAny(normalized, "查看", "查询", "搜索", "找")) {
            List<Memo> matched = findMatchingMemos(user.getId(), extractTargetKeyword(instruction));
            result.setSummary("准备查询匹配的备忘录");
            result.setQueryResults(matched);
            if (matched.isEmpty()) {
                result.getUnresolved().add("没有找到匹配的备忘录");
            }
            log.info("[AUDIT][MEMO][PARSE][END] userId={}, mode=query, matched={}",
                    userId, matched.size());
            return Result.ok(result);
        }

        if (containsAny(normalized, "删除", "删掉", "移除")) {
            List<Memo> matched = findMatchingMemos(user.getId(), extractTargetKeyword(instruction));
            if (matched.isEmpty()) {
                result.getUnresolved().add("没有找到要删除的备忘录");
            } else {
                for (Memo memo : matched) {
                    result.getOps().add(buildOp("DELETE", memo, "删除备忘录"));
                }
                result.setSummary("准备删除 " + matched.size() + " 条备忘录");
            }
            log.info("[AUDIT][MEMO][PARSE][END] userId={}, mode=delete, ops={}, unresolved={}",
                    userId, result.getOps().size(), result.getUnresolved().size());
            return Result.ok(result);
        }

        if (containsAny(normalized, "完成", "搞定")) {
            List<Memo> matched = findMatchingMemos(user.getId(), extractTargetKeyword(instruction));
            if (matched.isEmpty()) {
                result.getUnresolved().add("没有找到要完成的备忘录");
            } else {
                for (Memo memo : matched) {
                    result.getOps().add(buildOp("COMPLETE", memo, "标记完成"));
                }
                result.setSummary("准备完成 " + matched.size() + " 条备忘录");
            }
            log.info("[AUDIT][MEMO][PARSE][END] userId={}, mode=complete, ops={}, unresolved={}",
                    userId, result.getOps().size(), result.getUnresolved().size());
            return Result.ok(result);
        }

        if (containsAny(normalized, "修改", "改成", "改为")) {
            ParseUpdate update = parseUpdateInstruction(instruction, user.getId());
            if (update == null) {
                result.getUnresolved().add("暂时只支持“把旧内容改成新内容”这类修改");
            } else {
                result.getOps().add(update.op);
                result.setSummary(update.summary);
            }
            log.info("[AUDIT][MEMO][PARSE][END] userId={}, mode=update, ops={}, unresolved={}",
                    userId, result.getOps().size(), result.getUnresolved().size());
            return Result.ok(result);
        }

        Memo draft = new Memo();
        draft.setTitle(buildTitle(instruction));
        draft.setContent(instruction);
        draft.setRemindTime(parseRemindTime(instruction));
        draft.setSource(1);
        draft.setAiExtracted(1);
        draft.setStatus(STATUS_TODO);

        MemoDTO.MemoOp op = new MemoDTO.MemoOp();
        op.setAction("CREATE");
        op.setMemo(draft);
        op.setMessage("创建新备忘录");
        result.getOps().add(op);
        result.setSummary("准备创建 1 条备忘录");
        log.info("[AUDIT][MEMO][PARSE][END] userId={}, mode=create, ops={}", userId, result.getOps().size());
        return Result.ok(result);
    }

    @Override
    @Transactional
    public Result aiConfirm(MemoDTO.ConfirmRequest request) {
        if (request == null || request.getOps() == null || request.getOps().isEmpty()) {
            return Result.fail("没有可执行的操作");
        }

        UserDTO user = UserHolder.getUser();
        Long userId = user != null ? user.getId() : null;
        log.info("[AUDIT][MEMO][CONFIRM][START] userId={}, ops={}", userId, request.getOps().size());
        int executed = 0;
        List<String> failed = new ArrayList<String>();

        for (MemoDTO.MemoOp op : request.getOps()) {
            try {
                log.info("[AUDIT][MEMO][EXECUTE][TRY] userId={}, action={}, matchedId={}",
                        userId, op.getAction(), op.getMatchedId());
                if ("CREATE".equals(op.getAction())) {
                    Memo memo = op.getMemo();
                    if (memo == null || isBlank(memo.getContent())) {
                        failed.add("创建项内容不能为空");
                        continue;
                    }
                    normalizeMemoForSave(memo, user.getId(), null);
                    save(memo);
                    syncRemindQueue(memo);
                    executed++;
                    log.info("[AUDIT][MEMO][EXECUTE][OK] userId={}, action=CREATE, memoId={}", userId, memo.getId());
                    continue;
                }

                Memo existing = ownedMemo(op.getMatchedId(), user.getId());
                if (existing == null) {
                    failed.add("目标备忘录不存在");
                    continue;
                }

                if ("DELETE".equals(op.getAction())) {
                    removeById(existing.getId());
                    removeFromQueue(existing.getId());
                    executed++;
                    log.info("[AUDIT][MEMO][EXECUTE][OK] userId={}, action=DELETE, memoId={}", userId, existing.getId());
                } else if ("COMPLETE".equals(op.getAction())) {
                    existing.setStatus(STATUS_DONE);
                    updateById(existing);
                    removeFromQueue(existing.getId());
                    executed++;
                    log.info("[AUDIT][MEMO][EXECUTE][OK] userId={}, action=COMPLETE, memoId={}", userId, existing.getId());
                } else if ("UPDATE".equals(op.getAction())) {
                    Memo incoming = op.getMemo();
                    if (incoming == null || isBlank(incoming.getContent())) {
                        failed.add("修改后的内容不能为空");
                        continue;
                    }
                    normalizeMemoForSave(incoming, user.getId(), existing);
                    incoming.setId(existing.getId());
                    incoming.setCreatedTime(existing.getCreatedTime());
                    updateById(incoming);
                    syncRemindQueue(incoming);
                    executed++;
                    log.info("[AUDIT][MEMO][EXECUTE][OK] userId={}, action=UPDATE, memoId={}", userId, incoming.getId());
                } else {
                    failed.add("未知操作类型: " + op.getAction());
                    log.warn("[AUDIT][MEMO][EXECUTE][REJECT] userId={}, action={}", userId, op.getAction());
                }
            } catch (Exception e) {
                failed.add("执行失败: " + e.getMessage());
                log.error("[AUDIT][MEMO][EXECUTE][ERROR] userId={}, action={}, err={}",
                        userId, op.getAction(), e.getMessage(), e);
            }
        }

        MemoDTO.ConfirmResult result = new MemoDTO.ConfirmResult();
        result.setExecuted(executed);
        result.setFailed(failed);
        log.info("[AUDIT][MEMO][CONFIRM][END] userId={}, executed={}, failed={}",
                userId, executed, failed.size());
        return Result.ok(result);
    }

    private LambdaQueryWrapper<Memo> baseQuery(Long userId, String status, String keyword, Integer hasRemind) {
        LambdaQueryWrapper<Memo> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Memo::getUserId, userId);
        if (!isBlank(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(Memo::getTitle, trimmed).or().like(Memo::getContent, trimmed));
        }
        if (hasRemind != null && hasRemind == 1) {
            wrapper.isNotNull(Memo::getRemindTime);
        }
        applyStatusFilter(wrapper, status);
        return wrapper;
    }

    private Result updateStatus(Long id, int status) {
        UserDTO user = UserHolder.getUser();
        Memo existing = ownedMemo(id, user.getId());
        if (existing == null) {
            return Result.fail("备忘录不存在");
        }
        existing.setStatus(status);
        updateById(existing);
        if (status == STATUS_DONE) {
            removeFromQueue(id);
        } else {
            syncRemindQueue(existing);
        }
        return Result.ok();
    }

    private Memo ownedMemo(Long id, Long userId) {
        return lambdaQuery().eq(Memo::getId, id).eq(Memo::getUserId, userId).one();
    }

    private void normalizeMemoForSave(Memo memo, Long userId, Memo existing) {
        memo.setUserId(userId);
        String safeContent = sanitizeText(memo.getContent(), MAX_CONTENT_LENGTH);
        memo.setContent(safeContent);
        String safeTitle = isBlank(memo.getTitle()) ? buildTitle(safeContent) : memo.getTitle().trim();
        memo.setTitle(sanitizeText(safeTitle, MAX_TITLE_LENGTH));
        memo.setSource(memo.getSource() == null ? 0 : memo.getSource());
        memo.setAiExtracted(memo.getAiExtracted() == null ? (memo.getSource() == 1 ? 1 : 0) : memo.getAiExtracted());
        memo.setStatus(memo.getStatus() == null ? (existing == null ? STATUS_TODO : existing.getStatus()) : memo.getStatus());
        memo.setReminded(resolveReminded(memo, existing));
    }

    /**
     * 输入脱敏：去除常见HTML标签 / 不安全协议，并限制最大长度。
     * 这里不强求完整XSS方案，主要做最小防注入与可控长度，避免脏字符串污染存储与索引。
     */
    private String sanitizeText(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return "";
        String stripped = UNSAFE_TAG_PATTERN.matcher(trimmed).replaceAll("");
        stripped = UNSAFE_PROTOCOL_PATTERN.matcher(stripped).replaceAll("");
        stripped = stripped.replace("\u0000", "").replace("\r", " ");
        if (maxLength > 0 && stripped.length() > maxLength) {
            stripped = stripped.substring(0, maxLength);
        }
        return stripped;
    }

    private int resolveReminded(Memo memo, Memo existing) {
        if (existing == null) {
            return 0;
        }
        if (existing.getRemindTime() != null && existing.getRemindTime().equals(memo.getRemindTime())) {
            return existing.getReminded() == null ? 0 : existing.getReminded();
        }
        return 0;
    }

    private void syncRemindQueue(Memo memo) {
        removeFromQueue(memo.getId());
        if (memo.getStatus() != null && memo.getStatus() == STATUS_DONE) {
            return;
        }
        if (memo.getRemindTime() == null || memo.getId() == null) {
            return;
        }
        if (memo.getRemindTime().isBefore(LocalDateTime.now())) {
            return;
        }
        double score = memo.getRemindTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        stringRedisTemplate.opsForZSet().add(RedisConstants.MEMO_REMIND_QUEUE, String.valueOf(memo.getId()), score);
    }

    private void removeFromQueue(Long id) {
        if (id != null) {
            stringRedisTemplate.opsForZSet().remove(RedisConstants.MEMO_REMIND_QUEUE, String.valueOf(id));
        }
    }

    private void applyStatusFilter(LambdaQueryWrapper<Memo> wrapper, String status) {
        if (isBlank(status) || "all".equalsIgnoreCase(status)) {
            return;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if ("todo".equals(normalized)) {
            wrapper.eq(Memo::getStatus, STATUS_TODO);
        } else if ("done".equals(normalized)) {
            wrapper.eq(Memo::getStatus, STATUS_DONE);
        }
    }

    private List<Memo> findMatchingMemos(Long userId, String keyword) {
        if (isBlank(keyword)) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(Memo::getUserId, userId)
                .and(w -> w.like(Memo::getTitle, keyword).or().like(Memo::getContent, keyword))
                .last("limit 5")
                .list();
    }

    private MemoDTO.MemoOp buildOp(String action, Memo memo, String message) {
        MemoDTO.MemoOp op = new MemoDTO.MemoOp();
        op.setAction(action);
        op.setMatchedId(memo.getId());
        op.setMemo(memo);
        op.setMessage(message);
        return op;
    }

    private ParseUpdate parseUpdateInstruction(String instruction, Long userId) {
        String marker = containsAny(instruction, "改成") ? "改成" : (containsAny(instruction, "改为") ? "改为" : "修改");
        if ("修改".equals(marker)) {
            return null;
        }
        String[] parts = instruction.split(marker, 2);
        if (parts.length < 2) {
            return null;
        }
        String left = parts[0]
                .replace("把", "")
                .replace("备忘录", "")
                .replace("提醒", "")
                .trim();
        String right = parts[1].trim();
        List<Memo> matched = findMatchingMemos(userId, left);
        if (matched.isEmpty()) {
            return null;
        }
        Memo existing = matched.get(0);
        Memo updated = new Memo();
        updated.setTitle(buildTitle(right));
        updated.setContent(right);
        updated.setRemindTime(parseRemindTime(right));
        updated.setSource(1);
        updated.setAiExtracted(1);
        updated.setStatus(existing.getStatus());

        MemoDTO.MemoOp op = new MemoDTO.MemoOp();
        op.setAction("UPDATE");
        op.setMatchedId(existing.getId());
        op.setMemo(updated);
        op.setMessage("修改备忘录");

        ParseUpdate update = new ParseUpdate();
        update.op = op;
        update.summary = "准备修改 1 条备忘录";
        return update;
    }

    private String extractTargetKeyword(String instruction) {
        return instruction
                .replace("帮我", "")
                .replace("给我", "")
                .replace("把", "")
                .replace("备忘录", "")
                .replace("提醒", "")
                .replace("删除", "")
                .replace("删掉", "")
                .replace("移除", "")
                .replace("完成", "")
                .replace("搞定", "")
                .replace("查看", "")
                .replace("查询", "")
                .replace("搜索", "")
                .replace("找", "")
                .trim();
    }

    private String buildTitle(String content) {
        String normalized = normalizeCnTime(content).replaceAll("\\s+", " ").trim();
        normalized = normalized.replaceAll("^(\\u4eca\\u5929|\\u660e\\u5929|\\u540e\\u5929|\\u4eca\\u665a|\\u5468[\\u4e00\\u4e8c\\u4e09\\u56db\\u4e94\\u516d\\u65e5\\u5929])", "").trim();
        normalized = normalized.replaceAll("^(\\u4e0a\\u5348|\\u4e2d\\u5348|\\u4e0b\\u5348|\\u665a\\u4e0a)?\\s*\\d{1,2}([:\\uFF1A\\u70B9]\\d{1,2}|\\u70B9)?(\\u534A)?", "").trim();
        if (normalized.isEmpty()) {
            normalized = content.trim();
        }
        return normalized.length() > 16 ? normalized.substring(0, 16) : normalized;
    }

    private LocalDateTime parseRemindTime(String content) {
        LocalDate date = parseDate(content);
        LocalTime time = parseTime(content);
        if (time == null) {
            return null;
        }
        // 有时间无日期 → 默认今天
        if (date == null) {
            date = LocalDate.now();
        }
        LocalDateTime result = LocalDateTime.of(date, time);
        // 今天的时间已过 且 没有明确时段词 → 尝试 +12h（AM→PM）
        if (date.equals(LocalDate.now())
                && result.isBefore(LocalDateTime.now())
                && !hasExplicitPeriod(content)) {
            LocalDateTime pm = result.plusHours(12);
            if (pm.getHour() <= 23 && pm.isAfter(LocalDateTime.now())) {
                return pm;
            }
        }
        return result;
    }

    private boolean hasExplicitPeriod(String content) {
        return content.contains("上午") || content.contains("早上") || content.contains("中午")
                || content.contains("下午") || content.contains("晚上") || content.contains("今晚");
    }

    // X月X日 或 X号
    private static final java.util.regex.Pattern DATE_MD_PATTERN =
        java.util.regex.Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]");
    private static final java.util.regex.Pattern DATE_D_PATTERN =
        java.util.regex.Pattern.compile("(?<!月)(\\d{1,2})[日号]");

    private LocalDate parseDate(String content) {
        LocalDate today = LocalDate.now();
        if (content.contains("今天") || content.contains("今晚")) return today;
        if (content.contains("明天")) return today.plusDays(1);
        if (content.contains("后天")) return today.plusDays(2);

        // X月X日
        Matcher mdMatcher = DATE_MD_PATTERN.matcher(content);
        if (mdMatcher.find()) {
            int month = Integer.parseInt(mdMatcher.group(1));
            int day   = Integer.parseInt(mdMatcher.group(2));
            try {
                LocalDate d = LocalDate.of(today.getYear(), month, day);
                if (d.isBefore(today)) d = d.plusYears(1); // 已过则取明年
                return d;
            } catch (Exception ignored) {}
        }

        // X号（无月份，取本月或下月）
        Matcher dMatcher = DATE_D_PATTERN.matcher(content);
        if (dMatcher.find()) {
            int day = Integer.parseInt(dMatcher.group(1));
            try {
                LocalDate d = LocalDate.of(today.getYear(), today.getMonth(), day);
                if (d.isBefore(today)) d = d.plusMonths(1);
                return d;
            } catch (Exception ignored) {}
        }

        // 周X
        Matcher matcher = WEEKDAY_PATTERN.matcher(content);
        if (!matcher.find()) return null;
        DayOfWeek target = toDayOfWeek(matcher.group(1));
        LocalDate next = today.with(TemporalAdjusters.nextOrSame(target));
        if (next.equals(today) && LocalTime.now().isAfter(LocalTime.of(20, 0))) {
            return next.plusWeeks(1);
        }
        return next;
    }

    private LocalTime parseTime(String content) {
        String normalized = normalizeCnTime(content);
        Matcher matcher = CLOCK_PATTERN.matcher(normalized);
        if (matcher.find()) {
            String period = matcher.group(1);
            int hour = Integer.parseInt(matcher.group(2));
            String minuteText = matcher.group(3);
            boolean half = matcher.group(4) != null;
            int minute = half ? 30 : (minuteText == null ? 0 : Integer.parseInt(minuteText));
            if ("下午".equals(period) || "晚上".equals(period) || "今晚".equals(period)) {
                if (hour < 12) hour += 12;
            }
            if ("中午".equals(period) && hour < 11) {
                hour += 12;
            }
            if (hour <= 23 && minute <= 59) {
                return LocalTime.of(hour, minute);
            }
        }
        if (content.contains("今晚")) {
            return LocalTime.of(20, 0);
        }
        if (content.contains("早上") || content.contains("上午")) {
            return LocalTime.of(9, 0);
        }
        if (content.contains("中午")) {
            return LocalTime.of(12, 0);
        }
        if (content.contains("下午")) {
            return LocalTime.of(15, 0);
        }
        if (content.contains("晚上")) {
            return LocalTime.of(20, 0);
        }
        return null;
    }

    private String normalizeCnTime(String text) {
        for (Map.Entry<String, String> entry : CN_TIME_MAP.entrySet()) {
            text = text.replace(entry.getKey() + "点", entry.getValue() + "点");
            text = text.replace(entry.getKey() + "时", entry.getValue() + "时");
        }
        return text;
    }

    private DayOfWeek toDayOfWeek(String value) {
        switch (value) {
            case "一":
                return DayOfWeek.MONDAY;
            case "二":
                return DayOfWeek.TUESDAY;
            case "三":
                return DayOfWeek.WEDNESDAY;
            case "四":
                return DayOfWeek.THURSDAY;
            case "五":
                return DayOfWeek.FRIDAY;
            case "六":
                return DayOfWeek.SATURDAY;
            default:
                return DayOfWeek.SUNDAY;
        }
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class ParseUpdate {
        private MemoDTO.MemoOp op;
        private String summary;
    }

    // ---- Qdrant 语义搜索 ----

    @Override
    public Result searchMemos(String query) {
        if (isBlank(query)) return Result.fail("搜索词不能为空");
        UserDTO user = UserHolder.getUser();

        float[] vector = embeddingService.embed(query);
        if (vector == null) {
            // embedding 失败，降级为关键词搜索
            List<Memo> fallback = list(baseQuery(user.getId(), null, query, null)
                    .orderByDesc(Memo::getUpdatedTime).last("limit 20"));
            return Result.ok(fallback, (long) fallback.size());
        }

        List<Long> ids = qdrantService.searchMemos(user.getId(), vector, 10);
        if (ids.isEmpty()) return Result.ok(Collections.emptyList(), 0L);

        List<Memo> memos = listByIds(ids);
        // 按 Qdrant 返回顺序排列
        Map<Long, Memo> memoMap = new java.util.HashMap<>();
        for (Memo m : memos) memoMap.put(m.getId(), m);
        List<Memo> ordered = new ArrayList<>();
        for (Long id : ids) {
            if (memoMap.containsKey(id)) ordered.add(memoMap.get(id));
        }
        return Result.ok(ordered, (long) ordered.size());
    }

    /** 异步向量入库，不阻塞主流程 */
    private void indexAsync(Memo memo, Long userId) {
        INDEX_EXECUTOR.execute(() -> {
            String text = (memo.getTitle() != null ? memo.getTitle() : "") + " " + memo.getContent();
            float[] vector = embeddingService.embed(text.trim());
            qdrantService.upsertMemo(memo.getId(), userId, memo.getTitle(), memo.getContent(), vector);
        });
    }

    private boolean isWeakIntentOnly(String instruction) {
        String text = instruction == null ? "" : instruction.trim();
        // “改/查/删”等单字或短词不直接执行，避免误识别
        return text.matches("^(改|查|删|看|改下|查下|看下|改一下|查一下|看一下)$");
    }

    @PreDestroy
    public void shutdownIndexExecutor() {
        INDEX_EXECUTOR.shutdown();
    }
}
