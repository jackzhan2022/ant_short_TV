package com.antshorttv.drama;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wechat/component")
public class WechatComponentCallbackController {
    private final WechatComponentService service;
    public WechatComponentCallbackController(WechatComponentService service) { this.service = service; }
    @GetMapping(value="/callback", produces="text/plain")
    public String callback(@RequestParam String state, @RequestParam("auth_code") String code) {
        service.callback(state, code); return "授权处理完成，请返回管理后台查询状态。授权不代表代码已发布或可以自动播放。";
    }
    @GetMapping(value={"/events", "/messages/{appid}"}, produces="text/plain")
    public String echo(@PathVariable(required=false) String appid,
                       @RequestParam(required=false) String signature,
                       @RequestParam(value="msg_signature", required=false) String messageSignature,
                       @RequestParam String timestamp, @RequestParam String nonce, @RequestParam String echostr) {
        return service.echo(appid, signature, messageSignature, timestamp, nonce, echostr);
    }
    @PostMapping(value="/messages/{appid}", produces="text/plain")
    public String message(@PathVariable String appid, jakarta.servlet.http.HttpServletRequest request,
                          @RequestParam("msg_signature") String signature,
                          @RequestParam String timestamp, @RequestParam String nonce) throws java.io.IOException {
        return service.message(appid, body(request), signature, timestamp, nonce);
    }
    @PostMapping(value="/events", produces="text/plain")
    public String event(jakarta.servlet.http.HttpServletRequest request, @RequestParam("msg_signature") String signature,
                        @RequestParam String timestamp, @RequestParam String nonce) throws java.io.IOException {
        service.event(body(request), signature, timestamp, nonce); return "success";
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<String> rejected(org.springframework.web.server.ResponseStatusException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatusCode()).contentType(org.springframework.http.MediaType.TEXT_PLAIN)
            .body(error.getReason() == null ? "微信回调输入过大。" : error.getReason());
    }
    private String body(jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        byte[] bytes = request.getInputStream().readNBytes(65537);
        if (bytes.length > 65536) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE);
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) { throw WechatComponentCrypto.rejected(); }
    }
}
