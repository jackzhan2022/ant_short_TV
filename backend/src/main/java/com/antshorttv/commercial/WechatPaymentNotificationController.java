package com.antshorttv.commercial;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commercial/payments/wechat")
public class WechatPaymentNotificationController {
    private final WechatPaymentNotificationService service;

    public WechatPaymentNotificationController(WechatPaymentNotificationService service) { this.service = service; }

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> notify(
        @RequestHeader("Wechatpay-Timestamp") String timestamp,
        @RequestHeader("Wechatpay-Nonce") String nonce,
        @RequestHeader("Wechatpay-Signature") String signature,
        @RequestHeader("Wechatpay-Serial") String serial,
        @RequestBody String body
    ) {
        service.process(timestamp, nonce, signature, serial, body);
        return Map.of("code", "SUCCESS", "message", "成功");
    }
}
