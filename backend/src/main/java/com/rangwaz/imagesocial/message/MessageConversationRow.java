package com.rangwaz.imagesocial.message;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MessageConversationRow {
    private Long peerId;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Long unreadCount;
    private Long messageCount;
}
