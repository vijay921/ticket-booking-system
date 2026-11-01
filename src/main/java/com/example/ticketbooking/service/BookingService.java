package com.example.ticketbooking.service;

import com.example.ticketbooking.dto.BookingRequestDto;
import com.example.ticketbooking.dto.BookingResponseDto;
import com.example.ticketbooking.entity.*;
import com.example.ticketbooking.exception.ResourceNotFoundException;
import com.example.ticketbooking.exception.SeatAlreadyBookedException;
import com.example.ticketbooking.repository.BookingRepository;
import com.example.ticketbooking.repository.PaymentRepository;
import com.example.ticketbooking.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final UserService userService;
    private final ShowService showService;
    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public BookingResponseDto bookSeats(BookingRequestDto requestDto) {
        User user = userService.getUserById(requestDto.getUserId());
        Show show = showService.getShowById(requestDto.getShowId());

        if (requestDto.getSeatIds() == null || requestDto.getSeatIds().isEmpty()) {
            throw new IllegalArgumentException("At least one seat must be selected for booking");
        }

        // Acquire pessimistic row-level locks on the requested seats for this show
        List<ShowSeat> seats = showSeatRepository.findSeatsWithLock(requestDto.getSeatIds(), requestDto.getShowId());

        if (seats.size() != requestDto.getSeatIds().size()) {
            throw new ResourceNotFoundException("One or more requested seats do not exist for this show");
        }

        // Validate availability
        for (ShowSeat seat : seats) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                throw new SeatAlreadyBookedException("Seat " + seat.getSeatNumber() + " is already booked");
            }
        }

        // Calculate total amount & update seat status
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ShowSeat seat : seats) {
            seat.setStatus(SeatStatus.BOOKED);
            totalAmount = totalAmount.add(seat.getPrice());
        }
        showSeatRepository.saveAll(seats);

        // Create Booking
        Booking booking = Booking.builder()
                .user(user)
                .show(show)
                .status(BookingStatus.CONFIRMED)
                .totalAmount(totalAmount)
                .seats(seats)
                .build();
        booking = bookingRepository.save(booking);

        // Process Payment Record (Mock success)
        String transactionRef = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Payment payment = Payment.builder()
                .booking(booking)
                .amount(totalAmount)
                .paymentMethod(requestDto.getPaymentMethod())
                .status(PaymentStatus.SUCCESS)
                .transactionRef(transactionRef)
                .build();
        paymentRepository.save(payment);

        return BookingResponseDto.builder()
                .bookingId(booking.getId())
                .userId(user.getId())
                .showId(show.getId())
                .seatNumbers(seats.stream().map(ShowSeat::getSeatNumber).collect(Collectors.toList()))
                .totalAmount(totalAmount)
                .status(booking.getStatus())
                .paymentStatus(payment.getStatus())
                .transactionRef(transactionRef)
                .bookingTime(booking.getBookingTime())
                .build();
    }
}
