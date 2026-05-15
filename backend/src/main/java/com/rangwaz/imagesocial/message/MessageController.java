package com.rangwaz.imagesocial.message;

import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.message.dto.MessageConversationView;
import com.rangwaz.imagesocial.message.dto.MessageItemView;
import com.rangwaz.imagesocial.message.dto.MessageSummaryResponse;
import com.rangwaz.imagesocial.message.dto.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/summary")
    public ApiResponse<MessageSummaryResponse> summary() {
        return ApiResponse.success(messageService.summary(SecurityUtils.currentUserIdOrThrow()));
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResponse<MessageConversationView>> conversations(@RequestParam(defaultValue = "") String keyword,
                                                                            @RequestParam(defaultValue = "1") int page,
                                                                            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(messageService.conversations(SecurityUtils.currentUserIdOrThrow(), keyword, page, size));
    }

    @GetMapping("/conversations/{peerId}/thread")
    public ApiResponse<PageResponse<MessageItemView>> thread(@PathVariable Long peerId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "80") int size) {
        return ApiResponse.success(messageService.thread(SecurityUtils.currentUserIdOrThrow(), peerId, page, size));
    }

    @PostMapping("/conversations/{peerId}")
    public ApiResponse<MessageItemView> sendDirect(@PathVariable Long peerId,
                                                   @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(messageService.sendDirect(SecurityUtils.currentUserIdOrThrow(), peerId, request.content()), "发送成功");
    }

    @GetMapping("/notifications")
    public ApiResponse<PageResponse<MessageItemView>> notifications(@RequestParam(defaultValue = "all") String type,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(messageService.notifications(SecurityUtils.currentUserIdOrThrow(), type, page, size));
    }

    @PostMapping("/{messageId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long messageId) {
        messageService.markMessageRead(SecurityUtils.currentUserIdOrThrow(), messageId);
        return ApiResponse.success(null, "已读");
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(@RequestParam(defaultValue = "all") String box) {
        messageService.markAllRead(SecurityUtils.currentUserIdOrThrow(), box);
        return ApiResponse.success(null, "已全部标记已读");
    }
}
