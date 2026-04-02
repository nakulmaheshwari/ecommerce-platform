package com.ecommerce.seller.service;

import com.ecommerce.common.event.SellerRegisteredEvent;
import com.ecommerce.seller.domain.Seller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SellerEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void emitSellerRegistered(Seller seller) {
        log.info("Emitting seller-registered event for: {}", seller.getId());
        
        SellerRegisteredEvent event = SellerRegisteredEvent.builder()
                .sellerId(seller.getId())
                .email(seller.getEmail())
                .businessName(seller.getBusinessName())
                .status(seller.getStatus().name())
                .timestamp(OffsetDateTime.now())
                .build();

        kafkaTemplate.send("seller-registered", seller.getId().toString(), event);
    }
}
