package com.rangwaz.imagesocial.interaction.dto;

import com.rangwaz.imagesocial.auth.dto.UserSummary;
import java.time.LocalDateTime;

public record CommentView(
        Long id,
        UserSummary author,
        Long parentCommentId,
        UserSummary replyToUser,
        String content,
        LocalDateTime createdAt
) {
}
