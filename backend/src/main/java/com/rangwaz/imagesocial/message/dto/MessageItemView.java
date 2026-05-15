package com.rangwaz.imagesocial.message.dto;

import com.rangwaz.imagesocial.auth.dto.UserSummary;
import java.time.LocalDateTime;

public record MessageItemView(
        Long id,
        String kind,
        String title,
        String content,
        String actionUrl,
        UserSummary sender,
        UserSummary recipient,
        boolean fromMe,
        boolean read,
        LocalDateTime createdAt
) {
}
