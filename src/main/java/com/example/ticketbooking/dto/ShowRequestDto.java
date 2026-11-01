package com.example.ticketbooking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowRequestDto {
    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Start time is required")
    private Instant startTime;

    @NotNull(message = "End time is required")
    private Instant endTime;

    @Min(value = 1, message = "Rows must be at least 1")
    private int rows;

    @Min(value = 1, message = "Seats per row must be at least 1")
    private int seatsPerRow;

    @NotNull(message = "Default price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal defaultPrice;
}
