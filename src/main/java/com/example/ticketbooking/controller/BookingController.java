package com.example.ticketbooking.controller;

import com.example.ticketbooking.dto.BookingRequestDto;
import com.example.ticketbooking.dto.BookingResponseDto;
import com.example.ticketbooking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponseDto> bookSeats(@Valid @RequestBody BookingRequestDto bookingRequestDto) {
        BookingResponseDto response = bookingService.bookSeats(bookingRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
