package com.ecommerce.seller.service;

import com.ecommerce.seller.domain.Commission;
import com.ecommerce.seller.domain.enums.CommissionType;
import com.ecommerce.seller.repository.CommissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionService {

    private final CommissionRepository commissionRepository;

    @Transactional(readOnly = true)
    public long calculateCommission(UUID categoryId, long amountPaise) {
        log.debug("Calculating commission for category: {} and amount: {}", categoryId, amountPaise);
        
        Optional<Commission> commissionOpt = commissionRepository.findByCategoryId(categoryId);
        if (commissionOpt.isEmpty()) {
            // Default 10% if category not found
            return (long) (amountPaise * 0.10);
        }

        Commission commission = commissionOpt.get();
        if (!commission.isActive()) {
            return (long) (amountPaise * 0.10);
        }

        long fee = 0;
        if (commission.getCommissionType() == CommissionType.PERCENTAGE) {
            BigDecimal rate = commission.getRatePercent().divide(new BigDecimal(100));
            fee = new BigDecimal(amountPaise).multiply(rate).longValue();
        } else if (commission.getCommissionType() == CommissionType.FIXED_AMOUNT) {
            fee = commission.getFixedFeePaise();
        }

        // Apply caps
        if (commission.getMinCommissionPaise() != null && fee < commission.getMinCommissionPaise()) {
            fee = commission.getMinCommissionPaise();
        }
        if (commission.getMaxCommissionPaise() != null && fee > commission.getMaxCommissionPaise()) {
            fee = commission.getMaxCommissionPaise();
        }

        return fee;
    }

    @Transactional
    public Commission saveCommissionRule(Commission commission) {
        log.info("Saving commission rule for category: {}", commission.getCategoryName());
        return commissionRepository.save(commission);
    }
}
