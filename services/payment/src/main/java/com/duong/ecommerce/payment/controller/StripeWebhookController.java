package com.duong.ecommerce.payment.controller;

import com.duong.ecommerce.payment.service.StripeWebhookInbox;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class StripeWebhookController {
    private final StripeWebhookInbox inbox;
    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Void> receive(@RequestHeader("Stripe-Signature") String signature,@RequestBody String raw) {
        inbox.receive(raw,signature);
        return ResponseEntity.ok().build();
    }
}
