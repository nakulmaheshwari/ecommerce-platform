package com.ecommerce.seller.service;

import com.ecommerce.seller.domain.Seller;
import com.ecommerce.seller.domain.Settlement;
import com.ecommerce.seller.domain.enums.SellerStatus;
import com.ecommerce.seller.domain.enums.SettlementStatus;
import com.ecommerce.seller.repository.SellerRepository;
import com.ecommerce.seller.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SellerRepository sellerRepository;

    @Value("${seller.settlement.minimum-payout-paise:50000}")
    private long minimumPayoutPaise;

    @Scheduled(cron = "${seller.settlement.cron:0 0 2 * * SUN}")
    @Transactional
    public void runWeeklySettlement() {
        log.info("Starting weekly settlement process...");
        
        List<Seller> activeSellers = sellerRepository.findByStatus(SellerStatus.ACTIVE);
        OffsetDateTime periodEnd = OffsetDateTime.now();
        OffsetDateTime periodStart = periodEnd.minusWeeks(1);

        for (Seller seller : activeSellers) {
            try {
                processSellerSettlement(seller, periodStart, periodEnd);
            } catch (Exception e) {
                log.error("Failed to process settlement for seller {}: {}", seller.getId(), e.getMessage());
            }
        }
    }

    private void processSellerSettlement(Seller seller, OffsetDateTime start, OffsetDateTime end) {
        log.info("Processing settlement for seller: {} (period: {} to {})", 
                seller.getId(), start, end);
        
        // This is a simplified version. Phase 4 will include gathering order items.
        long pendingAmount = seller.getPendingSettlementPaise();
        
        if (pendingAmount < minimumPayoutPaise) {
            log.info("Seller {} pending amount ({}) is below minimum payout ({}). Skipping.", 
                    seller.getId(), pendingAmount, minimumPayoutPaise);
            return;
        }

        // Create settlement record
        Settlement settlement = Settlement.builder()
                .seller(seller)
                .bankAccount(seller.getBankAccounts().stream().filter(ba -> ba.isPrimary()).findFirst().orElse(null))
                .grossSalesPaise(pendingAmount)
                .netPayoutPaise(pendingAmount) // For now, net = gross. Commission logic in Phase 4.
                .status(SettlementStatus.PENDING)
                .periodStart(start)
                .periodEnd(end)
                .build();

        settlementRepository.save(settlement);
        
        // Update seller's pending amount
        seller.setPendingSettlementPaise(0L);
        seller.setTotalSettledPaise(seller.getTotalSettledPaise() + pendingAmount);
        sellerRepository.save(seller);

        log.info("Created settlement record {} for seller {}", settlement.getId(), seller.getId());
    }

    @Transactional(readOnly = true)
    public List<Settlement> getSettlementsForSeller(UUID sellerId) {
        return settlementRepository.findBySellerId(sellerId);
    }
}
