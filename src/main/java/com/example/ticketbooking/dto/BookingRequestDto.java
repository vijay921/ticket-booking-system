package com.example.ticketbooking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequestDto {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Show ID is required")
    private Long showId;

    @NotEmpty(message = "Seat IDs list must not be empty")
    private List<Long> seatIds;

    @NotNull(message = "Payment method is required")
    private String paymentMethod;
}
