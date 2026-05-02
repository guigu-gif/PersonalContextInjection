package com.pci.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pci.dto.GuideDTO;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.GuideInteraction;
import com.pci.entity.TravelGuide;
import com.pci.mapper.GuideInteractionMapper;
import com.pci.mapper.TravelGuideMapper;
import com.pci.service.IGuideService;
import com.pci.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GuideServiceImpl extends ServiceImpl<TravelGuideMapper, TravelGuide> implements IGuideService {

    private static final Set<String> ACTION_TYPES = new HashSet<>(Arrays.asList("LIKE", "FAV", "COIN", "CHARGE"));

    @Resource
    private GuideInteractionMapper interactionMapper;

    @Override
    public Result createGuide(GuideDTO.CreateRequest request) {
        if (request == null || isBlank(request.getCity()) || isBlank(request.getTitle()) || isBlank(request.getContent())) {
            return Result.fail("城市、标题、内容不能为空");
        }
        UserDTO user = UserHolder.getUser();
        TravelGuide guide = new TravelGuide();
        guide.setAuthorId(user.getId());
        guide.setCity(request.getCity().trim());
        guide.setTitle(request.getTitle().trim());
        guide.setContent(request.getContent().trim());
        guide.setTags(trimOrEmpty(request.getTags()));
        guide.setIsOfficial(request.getIsOfficial() != null && request.getIsOfficial() == 1 ? 1 : 0);
        guide.setStatus(1);
        guide.setCreatedTime(LocalDateTime.now());
        guide.setUpdatedTime(LocalDateTime.now());
        save(guide);
        return Result.ok(guide);
    }

    @Override
    public Result listGuides(String city, String keyword, Integer page, Integer size) {
        UserDTO user = UserHolder.getUser();
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<TravelGuide> wrapper = baseListQuery(city, keyword)
                .last("limit " + ((pageNo - 1) * pageSize) + "," + pageSize);
        List<TravelGuide> guides = list(wrapper);
        long total = count(baseListQuery(city, keyword));
        List<GuideDTO.GuideCard> cards = buildCards(guides, user.getId());
        cards.sort(Comparator.comparing(GuideDTO.GuideCard::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        return Result.ok(cards, total);
    }

    @Override
    public Result getGuideDetail(Long id) {
        UserDTO user = UserHolder.getUser();
        TravelGuide guide = lambdaQuery().eq(TravelGuide::getId, id).eq(TravelGuide::getStatus, 1).one();
        if (guide == null) return Result.fail("攻略不存在");
        List<GuideDTO.GuideCard> cards = buildCards(Collections.singletonList(guide), user.getId());
        return Result.ok(cards.isEmpty() ? null : cards.get(0));
    }

    @Override
    public Result interact(Long guideId, GuideDTO.ActionRequest request) {
        UserDTO user = UserHolder.getUser();
        if (request == null || isBlank(request.getActionType())) return Result.fail("actionType不能为空");
        String actionType = request.getActionType().trim().toUpperCase(Locale.ROOT);
        if (!ACTION_TYPES.contains(actionType)) return Result.fail("不支持的操作类型");

        TravelGuide guide = lambdaQuery().eq(TravelGuide::getId, guideId).eq(TravelGuide::getStatus, 1).one();
        if (guide == null) return Result.fail("攻略不存在");

        GuideInteraction existing = interactionMapper.selectOne(
                new LambdaQueryWrapper<GuideInteraction>()
                        .eq(GuideInteraction::getGuideId, guideId)
                        .eq(GuideInteraction::getUserId, user.getId())
                        .eq(GuideInteraction::getActionType, actionType)
        );

        if ("LIKE".equals(actionType) || "FAV".equals(actionType)) {
            // 点赞/收藏：开关型
            if (existing == null) {
                GuideInteraction item = new GuideInteraction();
                item.setGuideId(guideId);
                item.setUserId(user.getId());
                item.setActionType(actionType);
                item.setValue(1);
                item.setCreatedTime(LocalDateTime.now());
                item.setUpdatedTime(LocalDateTime.now());
                interactionMapper.insert(item);
            } else {
                interactionMapper.deleteById(existing.getId());
            }
        } else {
            // 投币/充电：累计型
            if (existing == null) {
                GuideInteraction item = new GuideInteraction();
                item.setGuideId(guideId);
                item.setUserId(user.getId());
                item.setActionType(actionType);
                item.setValue(1);
                item.setCreatedTime(LocalDateTime.now());
                item.setUpdatedTime(LocalDateTime.now());
                interactionMapper.insert(item);
            } else {
                existing.setValue((existing.getValue() == null ? 0 : existing.getValue()) + 1);
                existing.setUpdatedTime(LocalDateTime.now());
                interactionMapper.updateById(existing);
            }
        }

        return getGuideDetail(guideId);
    }

    @Override
    public Result recommend(String city, String origin, String destination, Integer topK) {
        UserDTO user = UserHolder.getUser();
        int limit = topK == null || topK < 1 ? 5 : Math.min(topK, 10);
        String query = (trimOrEmpty(origin) + " " + trimOrEmpty(destination)).trim();
        List<GuideDTO.AiEvidence> evidenceList = searchForAi(city, query, limit);

        // 把 ai evidence 映射成前端卡片结构，便于出行页直接展示
        List<Long> ids = evidenceList.stream().map(GuideDTO.AiEvidence::getGuideId).collect(Collectors.toList());
        if (ids.isEmpty()) return Result.ok(Collections.emptyList(), 0L);
        List<TravelGuide> guides = listByIds(ids);
        List<GuideDTO.GuideCard> cards = buildCards(guides, user.getId());
        Map<Long, GuideDTO.GuideCard> cardMap = cards.stream().collect(Collectors.toMap(GuideDTO.GuideCard::getId, c -> c));
        List<GuideDTO.GuideCard> ordered = new ArrayList<>();
        for (Long id : ids) {
            GuideDTO.GuideCard card = cardMap.get(id);
            if (card != null) ordered.add(card);
        }
        return Result.ok(ordered, (long) ordered.size());
    }

    @Override
    public List<GuideDTO.AiEvidence> searchForAi(String city, String query, Integer topK) {
        int limit = topK == null || topK < 1 ? 5 : Math.min(topK, 10);
        LambdaQueryWrapper<TravelGuide> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelGuide::getStatus, 1);
        if (!isBlank(city)) wrapper.eq(TravelGuide::getCity, city.trim());
        if (!isBlank(query)) {
            String q = query.trim();
            wrapper.and(w -> w.like(TravelGuide::getTitle, q).or().like(TravelGuide::getContent, q).or().like(TravelGuide::getTags, q));
        }
        wrapper.orderByDesc(TravelGuide::getIsOfficial).orderByDesc(TravelGuide::getCreatedTime).last("limit 100");
        List<TravelGuide> candidates = list(wrapper);
        if (candidates.isEmpty()) return Collections.emptyList();

        List<GuideDTO.GuideCard> cards = buildCards(candidates, null);
        cards.sort(Comparator.comparing(GuideDTO.GuideCard::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        if (cards.size() > limit) cards = cards.subList(0, limit);

        List<GuideDTO.AiEvidence> evidence = new ArrayList<>();
        for (GuideDTO.GuideCard card : cards) {
            GuideDTO.AiEvidence item = new GuideDTO.AiEvidence();
            item.setGuideId(card.getId());
            item.setSourceType(card.getIsOfficial() != null && card.getIsOfficial() == 1 ? "官方" : "用户");
            item.setScore(card.getScore());
            item.setTitle(card.getTitle());
            item.setSnippet(buildSnippet(card.getContent()));
            evidence.add(item);
        }
        return evidence;
    }

    private LambdaQueryWrapper<TravelGuide> baseListQuery(String city, String keyword) {
        LambdaQueryWrapper<TravelGuide> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelGuide::getStatus, 1);
        if (!isBlank(city)) wrapper.eq(TravelGuide::getCity, city.trim());
        if (!isBlank(keyword)) {
            String q = keyword.trim();
            wrapper.and(w -> w.like(TravelGuide::getTitle, q).or().like(TravelGuide::getContent, q).or().like(TravelGuide::getTags, q));
        }
        wrapper.orderByDesc(TravelGuide::getCreatedTime);
        return wrapper;
    }

    private List<GuideDTO.GuideCard> buildCards(List<TravelGuide> guides, Long currentUserId) {
        if (guides == null || guides.isEmpty()) return Collections.emptyList();
        List<Long> ids = guides.stream().map(TravelGuide::getId).collect(Collectors.toList());
        List<GuideInteraction> interactions = interactionMapper.selectList(
                new LambdaQueryWrapper<GuideInteraction>().in(GuideInteraction::getGuideId, ids)
        );

        Map<Long, List<GuideInteraction>> byGuide = interactions.stream().collect(Collectors.groupingBy(GuideInteraction::getGuideId));
        Set<String> myLike = new HashSet<>();
        Set<String> myFav = new HashSet<>();
        if (currentUserId != null) {
            for (GuideInteraction item : interactions) {
                if (!Objects.equals(item.getUserId(), currentUserId)) continue;
                if ("LIKE".equals(item.getActionType())) myLike.add(item.getGuideId() + "");
                if ("FAV".equals(item.getActionType())) myFav.add(item.getGuideId() + "");
            }
        }

        List<GuideDTO.GuideCard> cards = new ArrayList<>();
        for (TravelGuide guide : guides) {
            List<GuideInteraction> list = byGuide.getOrDefault(guide.getId(), Collections.emptyList());
            int likeCount = 0, favCount = 0, coinCount = 0, chargeCount = 0;
            for (GuideInteraction item : list) {
                String type = item.getActionType();
                int v = item.getValue() == null ? 0 : item.getValue();
                if ("LIKE".equals(type)) likeCount += 1;
                else if ("FAV".equals(type)) favCount += 1;
                else if ("COIN".equals(type)) coinCount += v;
                else if ("CHARGE".equals(type)) chargeCount += v;
            }

            double score = calcScore(guide, likeCount, favCount, coinCount, chargeCount);

            GuideDTO.GuideCard card = new GuideDTO.GuideCard();
            card.setId(guide.getId());
            card.setCity(guide.getCity());
            card.setTitle(guide.getTitle());
            card.setContent(guide.getContent());
            card.setTags(guide.getTags());
            card.setIsOfficial(guide.getIsOfficial());
            card.setScore(score);
            card.setLikeCount(likeCount);
            card.setFavCount(favCount);
            card.setCoinCount(coinCount);
            card.setChargeCount(chargeCount);
            card.setLiked(myLike.contains(guide.getId() + ""));
            card.setFavored(myFav.contains(guide.getId() + ""));
            card.setCreatedTime(guide.getCreatedTime());
            cards.add(card);
        }
        return cards;
    }

    private double calcScore(TravelGuide guide, int like, int fav, int coin, int charge) {
        double base = 10.0;
        double officialBoost = guide.getIsOfficial() != null && guide.getIsOfficial() == 1 ? 8.0 : 0.0;
        double interaction = like * 1.0 + fav * 2.0 + coin * 3.0 + charge * 4.0;

        LocalDateTime created = guide.getCreatedTime() == null ? LocalDateTime.now() : guide.getCreatedTime();
        long days = Math.max(0, Duration.between(created, LocalDateTime.now()).toDays());
        double freshness = Math.max(0.2, 1.0 - days * 0.01); // 每天衰减1%，最低保留20%

        return Math.round((base + officialBoost + interaction) * freshness * 100.0) / 100.0;
    }

    private String buildSnippet(String content) {
        if (isBlank(content)) return "";
        String c = content.trim();
        return c.length() > 80 ? c.substring(0, 80) + "..." : c;
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
