package com.rangwaz.imagesocial.message.dto;

import com.rangwaz.imagesocial.auth.dto.UserSummary;
import java.time.LocalDateTime;

public record MessageConversationView(
        Long peerId,
        UserSummary peer,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount,
        long messageCount
) {
}
