package com.rangwaz.imagesocial.taxonomy;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum ContentChannel {
    GENERAL("general", "综合", "综合", "general_post", Set.of("all")),
    CAMPUS("campus", "校园生活", "校园生活", "campus_post", Set.of("campus_life", "大学生校园生活")),
    ANIME_OUTFIT("anime_outfit", "二次元穿搭", "二次元穿搭", "anime_outfit_post", Set.of()),
    PET("pet", "宠物日常", "宠物日常", "pet_post", Set.of("pets")),
    PHOTOGRAPHY("photography", "摄影分享", "摄影分享", "photography_post", Set.of("摄影爱好者")),
    TECH_MOMENT("tech_moment", "程序员摸鱼", "程序员摸鱼", "tech_moment_post", Set.of("tool_post", "ai_tool", "overseas", "留学生生活"));

    public static final String TAXONOMY_VERSION = "content-channel-v2";
    private static final List<ContentChannel> DISPLAY_CHANNELS = List.of(
            CAMPUS,
            ANIME_OUTFIT,
            PET,
            PHOTOGRAPHY,
            TECH_MOMENT
    );

    private final String key;
    private final String label;
    private final String topicPath;
    private final String postType;
    private final Set<String> aliases;

    ContentChannel(String key, String label, String topicPath, String postType, Set<String> aliases) {
        this.key = key;
        this.label = label;
        this.topicPath = topicPath;
        this.postType = postType;
        this.aliases = aliases;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String topicPath() {
        return topicPath;
    }

    public String postType() {
        return postType;
    }

    public Set<String> aliases() {
        return aliases;
    }

    public static List<ContentChannel> displayChannels() {
        return DISPLAY_CHANNELS;
    }

    public static ContentChannel defaultChannel() {
        return GENERAL;
    }

    public static Optional<ContentChannel> fromKey(String raw) {
        String normalized = normalizeKey(raw);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(channel -> channel.matchesKey(normalized))
                .findFirst();
    }

    public static Optional<ContentChannel> fromTopicPath(String raw) {
        String normalized = normalizeText(raw);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(channel -> normalizeText(channel.topicPath).equals(normalized)
                        || normalizeText(channel.label).equals(normalized)
                        || channel.aliases.stream().anyMatch(alias -> normalizeText(alias).equals(normalized)))
                .findFirst();
    }

    public static ContentChannel fromPostTaxonomy(String channelKey, String topicPath) {
        return fromKey(channelKey)
                .or(() -> fromTopicPath(topicPath))
                .orElse(defaultChannel());
    }

    public static boolean isChannelLabel(String raw) {
        String normalized = normalizeText(raw);
        if (normalized.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(channel -> normalizeText(channel.label).equals(normalized)
                        || normalizeText(channel.topicPath).equals(normalized)
                        || channel.aliases.stream().anyMatch(alias -> normalizeText(alias).equals(normalized)));
    }

    private boolean matchesKey(String normalized) {
        if (key.equals(normalized)) {
            return true;
        }
        return aliases.stream()
                .map(ContentChannel::normalizeKey)
                .anyMatch(normalized::equals);
    }

    private static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String normalizeText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }
}
