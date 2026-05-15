package com.rangwaz.imagesocial.message.dto;

public record MessageSummaryResponse(
        long unreadDirect,
        long unreadNotifications,
        long unreadTotal
) {
}
