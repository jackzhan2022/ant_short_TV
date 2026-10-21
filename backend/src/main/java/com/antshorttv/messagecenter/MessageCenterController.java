package com.antshorttv.messagecenter;

import com.antshorttv.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
public class MessageCenterController {
    private final MessageCenterService service;
    public MessageCenterController(MessageCenterService service) { this.service = service; }

    @GetMapping
    public ApiResponse<MessageCenterService.Page> list(@RequestParam(required = false) String category,
        @RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.list(category, status, keyword, page, pageSize));
    }
    @GetMapping("/summary")
    public ApiResponse<MessageCenterService.Summary> summary() { return ApiResponse.success(service.summary()); }

    @GetMapping("/sent")
    public ApiResponse<MessageCenterService.SentPage> sent(@RequestParam(required = false) String category,
        @RequestParam(required = false) String source, @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.sentMessages(category, source, keyword, page, pageSize));
    }
    @GetMapping("/sent/{id}")
    public ApiResponse<MessageCenterService.SentMessage> sentDetail(@PathVariable String id) { return ApiResponse.success(service.sentDetail(id)); }
    @GetMapping("/sent/{id}/recipients")
    public ApiResponse<MessageCenterService.DeliveryPage> deliveries(@PathVariable String id,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(service.deliveries(id, page, pageSize));
    }

    @GetMapping("/recipients")
    public ApiResponse<List<MessageCenterService.Recipient>> recipients(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.recipients(keyword));
    }
    @GetMapping("/{id}")
    public ApiResponse<MessageCenterService.Message> detail(@PathVariable String id) { return ApiResponse.success(service.detail(id)); }

    @PostMapping("/read")
    public ApiResponse<Void> read(@RequestBody MessageCenterService.ActionRequest request) { service.update(false, request); return ApiResponse.ok(); }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody MessageCenterService.ActionRequest request) { service.update(true, request); return ApiResponse.ok(); }

    @PostMapping("/send")
    public ApiResponse<MessageCenterService.Sent> send(@RequestBody MessageCenterService.SendRequest request) {
        return ApiResponse.success(service.send(request));
    }
}
