package com.ecommerce.seller.domain.enums;

public enum SellerStatus {
    PENDING_KYC,     // registered but KYC not submitted
    KYC_SUBMITTED,   // documents uploaded, awaiting review
    KYC_REJECTED,    // documents rejected, seller must resubmit
    ACTIVE,          // KYC approved, can list products and sell
    SUSPENDED,       // suspended by admin (policy violation)
    DEACTIVATED      // seller chose to stop selling
}
