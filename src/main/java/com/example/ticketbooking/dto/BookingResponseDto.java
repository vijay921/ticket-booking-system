package com.example.ticketbooking.dto;

import com.example.ticketbooking.entity.BookingStatus;
import com.example.ticketbooking.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponseDto {
    private Long bookingId;
    private Long userId;
    private Long showId;
    private List<String> seatNumbers;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private PaymentStatus paymentStatus;
    private String transactionRef;
    private Instant bookingTime;
}
