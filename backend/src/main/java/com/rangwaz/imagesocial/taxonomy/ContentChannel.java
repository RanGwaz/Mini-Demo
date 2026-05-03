package com.rangwaz.imagesocial.taxonomy;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ContentChannel {
    GENERAL("general", "综合", "综合"),
    CAMPUS_LIFE("campus_life", "大学生校园生活", "大学生校园生活"),
    PHOTOGRAPHY("photography", "摄影爱好者", "摄影爱好者"),
    ANIME_OUTFIT("anime_outfit", "二次元穿搭", "二次元穿搭"),
    PETS("pets", "宠物日常", "宠物日常"),
    OVERSEAS("overseas", "留学生生活", "留学生生活");

    public static final String TAXONOMY_VERSION = "content-channel-v1";

    private final String key;
    private final String label;
    private final String topicPath;

    ContentChannel(String key, String label, String topicPath) {
        this.key = key;
        this.label = label;
        this.topicPath = topicPath;
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

    public static ContentChannel defaultChannel() {
        return GENERAL;
    }

    public static Optional<ContentChannel> fromKey(String raw) {
        String normalized = normalizeKey(raw);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if ("all".equals(normalized)) {
            return Optional.of(GENERAL);
        }
        return Arrays.stream(values())
                .filter(channel -> channel.key.equals(normalized))
                .findFirst();
    }

    public static Optional<ContentChannel> fromTopicPath(String raw) {
        String normalized = normalizeText(raw);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(channel -> normalizeText(channel.topicPath).equals(normalized)
                        || normalizeText(channel.label).equals(normalized))
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
                        || normalizeText(channel.topicPath).equals(normalized));
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
