package com.example.ticketbooking.service;

import com.example.ticketbooking.dto.BookingRequestDto;
import com.example.ticketbooking.dto.BookingResponseDto;
import com.example.ticketbooking.dto.ShowRequestDto;
import com.example.ticketbooking.dto.UserRequestDto;
import com.example.ticketbooking.entity.*;
import com.example.ticketbooking.exception.SeatAlreadyBookedException;
import com.example.ticketbooking.repository.BookingRepository;
import com.example.ticketbooking.repository.ShowSeatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
public class BookingServiceConcurrencyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ShowService showService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    @DisplayName("Under concurrent load, exactly 1 thread can book a contested seat and others fail with SeatAlreadyBookedException")
    void testConcurrentSeatBooking() throws InterruptedException {
        // 1. Setup a Show with 1 row and 2 seats
        ShowRequestDto showDto = ShowRequestDto.builder()
                .title("Concurrency Showcase")
                .description("Pessimistic Locking Test")
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .rows(1)
                .seatsPerRow(2)
                .defaultPrice(new BigDecimal("20.00"))
                .build();
        Show show = showService.createShow(showDto);

        List<ShowSeat> seats = showService.getSeatsForShow(show.getId());
        assertThat(seats).hasSize(2);
        ShowSeat targetSeat = seats.get(0);

        // 2. Setup 10 Users
        int threadCount = 10;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UserRequestDto userDto = UserRequestDto.builder()
                    .name("User_" + i)
                    .email("user" + i + "_" + System.currentTimeMillis() + "@test.com")
                    .phone("123456789" + i)
                    .build();
            users.add(userService.createUser(userDto));
        }

        // 3. Concurrently try to book the exact same target seat with 10 threads
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Exception> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final User user = users.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Wait for all threads to align
                    BookingRequestDto request = BookingRequestDto.builder()
                            .userId(user.getId())
                            .showId(show.getId())
                            .seatIds(List.of(targetSeat.getId()))
                            .paymentMethod("CREDIT_CARD")
                            .build();

                    bookingService.bookSeats(request);
                    successCount.incrementAndGet();
                } catch (SeatAlreadyBookedException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedExceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire all threads simultaneously
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 4. Assertions
        assertThat(completed).isTrue();
        assertThat(unexpectedExceptions).isEmpty();
        assertThat(successCount.get())
                .as("Exactly one user should successfully book the contested seat")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("All other 9 users should receive SeatAlreadyBookedException")
                .isEqualTo(threadCount - 1);

        // Verify database state
        ShowSeat updatedSeat = showSeatRepository.findById(targetSeat.getId()).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    @DisplayName("Booking with empty seats list fails validation")
    void testBookingEmptySeatsThrowsException() {
        User user = userService.createUser(UserRequestDto.builder()
                .name("Empty Seats Tester")
                .email("empty_" + System.currentTimeMillis() + "@test.com")
                .build());

        Show show = showService.createShow(ShowRequestDto.builder()
                .title("Empty Seat Show")
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .rows(1)
                .seatsPerRow(1)
                .defaultPrice(new BigDecimal("10.00"))
                .build());

        BookingRequestDto request = BookingRequestDto.builder()
                .userId(user.getId())
                .showId(show.getId())
                .seatIds(List.of())
                .paymentMethod("UPI")
                .build();

        assertThrows(IllegalArgumentException.class, () -> bookingService.bookSeats(request));
    }

    @Test
    @DisplayName("Sequential booking: can book available seats, but cannot re-book already booked seat")
    void testSequentialBookingValidation() {
        User user = userService.createUser(UserRequestDto.builder()
                .name("Sequential User")
                .email("seq_" + System.currentTimeMillis() + "@test.com")
                .build());

        Show show = showService.createShow(ShowRequestDto.builder()
                .title("Sequential Show")
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .rows(1)
                .seatsPerRow(2)
                .defaultPrice(new BigDecimal("15.50"))
                .build());

        List<ShowSeat> seats = showService.getSeatsForShow(show.getId());
        ShowSeat seat1 = seats.get(0);
        ShowSeat seat2 = seats.get(1);

        // Book seat 1
        BookingResponseDto booking1 = bookingService.bookSeats(BookingRequestDto.builder()
                .userId(user.getId())
                .showId(show.getId())
                .seatIds(List.of(seat1.getId()))
                .paymentMethod("CREDIT_CARD")
                .build());

        assertThat(booking1.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking1.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(booking1.getSeatNumbers()).containsExactly("A1");
        assertThat(booking1.getTotalAmount()).isEqualByComparingTo("15.50");

        // Attempt to book seat 1 again - should fail
        assertThrows(SeatAlreadyBookedException.class, () -> {
            bookingService.bookSeats(BookingRequestDto.builder()
                    .userId(user.getId())
                    .showId(show.getId())
                    .seatIds(List.of(seat1.getId()))
                    .paymentMethod("CREDIT_CARD")
                    .build());
        });

        // Book seat 2 - should succeed
        BookingResponseDto booking2 = bookingService.bookSeats(BookingRequestDto.builder()
                .userId(user.getId())
                .showId(show.getId())
                .seatIds(List.of(seat2.getId()))
                .paymentMethod("DEBIT_CARD")
                .build());

        assertThat(booking2.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking2.getSeatNumbers()).containsExactly("A2");
    }
}
