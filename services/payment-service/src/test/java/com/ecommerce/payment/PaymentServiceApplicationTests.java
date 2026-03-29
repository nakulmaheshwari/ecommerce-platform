package com.ecommerce.payment;

import com.ecommerce.payment.api.dto.InitiatePaymentRequest;
import com.ecommerce.payment.api.dto.InitiatePaymentResponse;
import com.ecommerce.payment.domain.PaymentStatus;
import com.ecommerce.payment.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PaymentServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.razorpay.RazorpayClient razorpayClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @WithMockUser(roles = "USER")
    void shouldInitiatePayment() throws Exception {
        // Mock Razorpay order creation
        com.razorpay.Order mockOrder = org.mockito.Mockito.mock(com.razorpay.Order.class);
        org.mockito.Mockito.when(mockOrder.get("id")).thenReturn("rzp_order_123");
        org.mockito.Mockito.when(mockOrder.get("status")).thenReturn("created");
        
        com.razorpay.OrderClient mockOrderClient = org.mockito.Mockito.mock(com.razorpay.OrderClient.class);
        org.mockito.Mockito.when(mockOrderClient.create(org.mockito.ArgumentMatchers.any())).thenReturn(mockOrder);
        
        // Use reflection or field access if needed, but RazorpayClient has public fields usually
        // Actually the SDK is a bit annoying with public fields. Let's see.
        razorpayClient.orders = mockOrderClient;

        UUID orderId = UUID.randomUUID();
        InitiatePaymentRequest request = new InitiatePaymentRequest(
            orderId,
            UUID.randomUUID(),
            10000L,
            UUID.randomUUID(),
            "INR"
        );

        String responseJson = mockMvc.perform(post("/api/v1/payments/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        InitiatePaymentResponse response = objectMapper.readValue(
            responseJson, InitiatePaymentResponse.class);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.amountPaise()).isEqualTo(10000L);
        
        var txn = transactionRepository.findByOrderId(orderId).orElseThrow();
        assertThat(txn.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
