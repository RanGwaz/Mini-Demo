package com.rangwaz.imagesocial.taxonomy;

import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.taxonomy.dto.PublishSuggestionsResponse;
import com.rangwaz.imagesocial.taxonomy.dto.PublishTagSuggestion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TaxonomyService {
    private static final int RECENT_TAG_DAYS = 30;
    private static final int RECENT_TAG_SAMPLE_LIMIT = 500;
    private static final Pattern TAG_SPLITTER = Pattern.compile("[,，#\\s]+");

    private static final List<String> COMMON_QUICK_TAGS = List.of(
            "日常记录", "经验分享", "好物推荐", "学习笔记", "摄影", "游戏", "穿搭", "旅行"
    );

    private static final Map<String, List<String>> CHANNEL_QUICK_TAGS = Map.of(
            "campus", List.of("宿舍", "课程", "期末周", "社团", "自习室", "食堂", "校园摄影", "学习笔记"),
            "photography", List.of("街拍", "胶片", "构图", "人像", "风景", "后期", "相机", "镜头"),
            "anime_outfit", List.of("二次元", "漫展", "cos", "谷子", "痛包", "穿搭", "手办", "游戏美术"),
            "pet", List.of("猫咪", "狗狗", "萌宠", "养宠经验", "领养", "洗护", "治愈瞬间", "宠物档案"),
            "tech_moment", List.of("AI工具", "效率工具", "开发工具", "摸鱼", "Cursor", "自动化", "代码重构", "工作流")
    );

    private static final List<String> TRENDING_SEEDS = List.of(
            "异次元的狙击手", "游戏原画", "虚拟人", "漫画人物出场定格", "穿越火线", "中画幅", "射击游戏", "校园生活",
            "旅行攻略", "人像摄影", "宿舍改造", "AI效率工具"
    );

    private final PostMapper postMapper;

    public TaxonomyService(PostMapper postMapper) {
        this.postMapper = postMapper;
    }

    public PublishSuggestionsResponse publishSuggestions(String rawChannel, String rawKeyword) {
        String channelKey = ContentChannel.fromKey(rawChannel)
                .map(ContentChannel::key)
                .filter(key -> !"general".equals(key))
                .orElse("campus");
        String keyword = normalizeTag(rawKeyword).toLowerCase(Locale.ROOT);

        LinkedHashMap<String, Integer> scores = new LinkedHashMap<>();
        mergeSeedTags(scores, CHANNEL_QUICK_TAGS.getOrDefault(channelKey, List.of()), 80);
        mergeSeedTags(scores, COMMON_QUICK_TAGS, 48);
        mergeSeedTags(scores, TRENDING_SEEDS, 36);
        mergePostTags(scores);

        List<Map.Entry<String, Integer>> ranked = scores.entrySet().stream()
                .filter(entry -> keyword.isBlank() || entry.getKey().toLowerCase(Locale.ROOT).contains(keyword))
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .toList();

        List<String> quickTags = ranked.stream()
                .map(Map.Entry::getKey)
                .limit(8)
                .toList();
        List<PublishTagSuggestion> trendingTags = ranked.stream()
                .limit(12)
                .map(entry -> new PublishTagSuggestion(
                        entry.getKey(),
                        formatHeat(entry.getValue()),
                        Math.max(0, entry.getValue()),
                        entry.getValue() >= 100 ? "recent" : "seed"
                ))
                .toList();

        return new PublishSuggestionsResponse(quickTags, trendingTags);
    }

    private void mergeSeedTags(Map<String, Integer> scores, List<String> tags, int baseScore) {
        for (int i = 0; i < tags.size(); i++) {
            String tag = normalizeTag(tags.get(i));
            if (tag.isBlank() || ContentChannel.isChannelLabel(tag)) continue;
            scores.merge(tag, Math.max(1, baseScore - i), Integer::sum);
        }
    }

    private void mergePostTags(Map<String, Integer> scores) {
        List<Post> posts = postMapper.selectRecentTagSamples(RECENT_TAG_DAYS, RECENT_TAG_SAMPLE_LIMIT);
        for (Post post : posts) {
            for (String tag : parseTags(post.getTags(), post.getSemanticTags(), post.getStyleTags())) {
                if (ContentChannel.isChannelLabel(tag)) continue;
                scores.merge(tag, 100, Integer::sum);
            }
        }
    }

    private List<String> parseTags(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            for (String item : TAG_SPLITTER.split(value)) {
                String tag = normalizeTag(item);
                if (!tag.isBlank()) result.add(tag);
            }
        }
        return result;
    }

    private String normalizeTag(String raw) {
        return Objects.toString(raw, "")
                .trim()
                .replaceFirst("^#+", "")
                .replaceAll("\\s+", "");
    }

    private String formatHeat(int score) {
        if (score >= 10_000) {
            return String.format(Locale.ROOT, "%.1f万浏览", score / 10_000.0).replace(".0万", "万");
        }
        if (score >= 100) {
            return score + "浏览";
        }
        return "推荐";
    }
}
