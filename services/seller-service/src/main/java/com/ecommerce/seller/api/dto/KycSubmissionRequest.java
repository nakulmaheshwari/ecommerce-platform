package com.ecommerce.seller.api.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycSubmissionRequest {
    @NotEmpty
    private List<DocumentDto> documents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentDto {
        private String documentType; // PAN_CARD, GST_CERTIFICATE, etc.
        private String documentUrl;
    }
}
