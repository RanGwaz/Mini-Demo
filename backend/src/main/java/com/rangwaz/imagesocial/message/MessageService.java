package com.rangwaz.imagesocial.message;

import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.SiteMessage;
import com.rangwaz.imagesocial.domain.mapper.SiteMessageMapper;
import com.rangwaz.imagesocial.message.dto.MessageConversationView;
import com.rangwaz.imagesocial.message.dto.MessageItemView;
import com.rangwaz.imagesocial.message.dto.MessageSummaryResponse;
import com.rangwaz.imagesocial.user.UserService;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

    private final SiteMessageMapper siteMessageMapper;
    private final UserService userService;

    public MessageService(SiteMessageMapper siteMessageMapper, UserService userService) {
        this.siteMessageMapper = siteMessageMapper;
        this.userService = userService;
    }

    public MessageSummaryResponse summary(Long userId) {
        long unreadDirect = siteMessageMapper.countUnreadDirect(userId);
        long unreadNotifications = siteMessageMapper.countUnreadNotifications(userId);
        return new MessageSummaryResponse(unreadDirect, unreadNotifications, unreadDirect + unreadNotifications);
    }

    public PageResponse<MessageConversationView> conversations(Long userId, String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = clamp(size, 1, 50);
        int offset = (safePage - 1) * safeSize;
        String safeKeyword = normalizeKeyword(keyword);
        List<MessageConversationRow> rows = siteMessageMapper.selectConversationRows(userId, safeKeyword, offset, safeSize);
        Map<Long, UserSummary> users = userService.summaryMapByIds(rows.stream().map(MessageConversationRow::getPeerId).toList());
        List<MessageConversationView> records = rows.stream()
                .map(row -> new MessageConversationView(
                        row.getPeerId(),
                        users.getOrDefault(row.getPeerId(), userService.summaryOrPlaceholder(row.getPeerId())),
                        row.getLastMessage(),
                        row.getLastMessageAt(),
                        row.getUnreadCount() == null ? 0L : row.getUnreadCount(),
                        row.getMessageCount() == null ? 0L : row.getMessageCount()))
                .toList();
        long total = siteMessageMapper.countConversations(userId, safeKeyword);
        return new PageResponse<>(records, total, safePage, safeSize);
    }

    @Transactional
    public PageResponse<MessageItemView> thread(Long userId, Long peerId, int page, int size) {
        userService.requireById(peerId);
        int safePage = Math.max(page, 1);
        int safeSize = clamp(size, 1, 100);
        int offset = (safePage - 1) * safeSize;
        List<SiteMessage> rows = siteMessageMapper.selectThread(userId, peerId, offset, safeSize);
        siteMessageMapper.markThreadRead(userId, peerId);
        long total = siteMessageMapper.countThread(userId, peerId);
        return new PageResponse<>(toViews(rows, userId), total, safePage, safeSize);
    }

    @Transactional
    public MessageItemView sendDirect(Long senderId, Long recipientId, String content) {
        if (senderId.equals(recipientId)) {
            throw new BusinessException("不能给自己发送私信");
        }
        userService.requireById(recipientId);
        String safeContent = normalizeContent(content);

        SiteMessage message = new SiteMessage();
        message.setSenderId(senderId);
        message.setRecipientId(recipientId);
        message.setPeerId(recipientId);
        message.setMessageKind(MessageKind.DIRECT);
        message.setContent(safeContent);
        siteMessageMapper.insert(message);
        return toView(message, senderId, loadUserMap(List.of(message)), userService.summaryOrPlaceholder(senderId));
    }

    public PageResponse<MessageItemView> notifications(Long userId, String type, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = clamp(size, 1, 80);
        int offset = (safePage - 1) * safeSize;
        String safeType = normalizeNotificationType(type);
        List<SiteMessage> rows = siteMessageMapper.selectNotifications(userId, safeType, offset, safeSize);
        long total = siteMessageMapper.countNotifications(userId, safeType);
        return new PageResponse<>(toViews(rows, userId), total, safePage, safeSize);
    }

    @Transactional
    public void markMessageRead(Long userId, Long messageId) {
        siteMessageMapper.markMessageRead(userId, messageId);
    }

    @Transactional
    public void markAllRead(Long userId, String box) {
        String safeBox = box == null ? "all" : box.trim().toLowerCase();
        if ("direct".equals(safeBox)) {
            siteMessageMapper.markAllDirectRead(userId);
            return;
        }
        if ("notifications".equals(safeBox)) {
            siteMessageMapper.markAllNotificationsRead(userId);
            return;
        }
        siteMessageMapper.markAllDirectRead(userId);
        siteMessageMapper.markAllNotificationsRead(userId);
    }

    @Transactional
    public void notifyInteraction(Long recipientId, Long senderId, String title, String content, String actionUrl) {
        notify(recipientId, senderId, MessageKind.INTERACTION, title, content, actionUrl);
    }

    @Transactional
    public void notifySystem(Long recipientId, String title, String content, String actionUrl) {
        notify(recipientId, null, MessageKind.SYSTEM, title, content, actionUrl);
    }

    private void notify(Long recipientId, Long senderId, String kind, String title, String content, String actionUrl) {
        if (recipientId == null || recipientId.equals(senderId)) {
            return;
        }
        SiteMessage message = new SiteMessage();
        message.setSenderId(senderId);
        message.setRecipientId(recipientId);
        message.setPeerId(senderId);
        message.setMessageKind(kind);
        message.setTitle(abbreviate(title, 160));
        message.setContent(abbreviate(normalizeContent(content), 1000));
        message.setActionUrl(abbreviate(actionUrl, 512));
        siteMessageMapper.insert(message);
    }

    private List<MessageItemView> toViews(List<SiteMessage> rows, Long currentUserId) {
        Map<Long, UserSummary> users = loadUserMap(rows);
        UserSummary currentUser = userService.summaryOrPlaceholder(currentUserId);
        return rows.stream()
                .map(row -> toView(row, currentUserId, users, currentUser))
                .toList();
    }

    private MessageItemView toView(SiteMessage row, Long currentUserId, Map<Long, UserSummary> users, UserSummary currentUser) {
        UserSummary sender = row.getSenderId() == null
                ? null
                : users.getOrDefault(row.getSenderId(), userService.summaryOrPlaceholder(row.getSenderId()));
        UserSummary recipient = row.getRecipientId() == null
                ? null
                : users.getOrDefault(row.getRecipientId(), currentUserId.equals(row.getRecipientId())
                        ? currentUser
                        : userService.summaryOrPlaceholder(row.getRecipientId()));
        return new MessageItemView(
                row.getId(),
                row.getMessageKind(),
                row.getTitle(),
                row.getContent(),
                row.getActionUrl(),
                sender,
                recipient,
                row.getSenderId() != null && row.getSenderId().equals(currentUserId),
                row.getReadAt() != null || row.getSenderId() != null && row.getSenderId().equals(currentUserId),
                row.getCreatedAt() == null ? LocalDateTime.now() : row.getCreatedAt());
    }

    private Map<Long, UserSummary> loadUserMap(List<SiteMessage> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (SiteMessage row : rows) {
            if (row.getSenderId() != null) {
                ids.add(row.getSenderId());
            }
            if (row.getRecipientId() != null) {
                ids.add(row.getRecipientId());
            }
        }
        return userService.summaryMapByIds(ids.stream().toList());
    }

    private String normalizeContent(String content) {
        String safe = content == null ? "" : content.trim();
        if (safe.isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }
        return abbreviate(safe, 1000);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String normalizeNotificationType(String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) {
            return "all";
        }
        String value = type.trim().toLowerCase();
        if ("interaction".equals(value) || "system".equals(value)) {
            return value;
        }
        return "all";
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
