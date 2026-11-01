package com.example.ticketbooking;

import com.example.ticketbooking.dto.BookingRequestDto;
import com.example.ticketbooking.dto.BookingResponseDto;
import com.example.ticketbooking.dto.ShowRequestDto;
import com.example.ticketbooking.dto.UserRequestDto;
import com.example.ticketbooking.entity.BookingStatus;
import com.example.ticketbooking.entity.PaymentStatus;
import com.example.ticketbooking.entity.Show;
import com.example.ticketbooking.entity.ShowSeat;
import com.example.ticketbooking.entity.User;
import com.example.ticketbooking.service.BookingService;
import com.example.ticketbooking.service.ShowService;
import com.example.ticketbooking.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class PostgreSQLIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ShowService showService;

    @Autowired
    private BookingService bookingService;

    @Test
    @DisplayName("Verify end-to-end booking flow directly on live PostgreSQL container")
    void testEndToEndOnPostgres() {
        // 1. Create User
        User user = userService.createUser(UserRequestDto.builder()
                .name("Postgres Tester")
                .email("pg_tester_" + System.currentTimeMillis() + "@example.com")
                .phone("1122334455")
                .build());
        assertThat(user.getId()).isNotNull();

        // 2. Create Show
        Show show = showService.createShow(ShowRequestDto.builder()
                .title("PostgreSQL Live Demo")
                .description("Running against Docker PostgreSQL 16")
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .rows(2)
                .seatsPerRow(3)
                .defaultPrice(new BigDecimal("50.00"))
                .build());
        assertThat(show.getId()).isNotNull();

        // 3. Query Seats
        List<ShowSeat> seats = showService.getSeatsForShow(show.getId());
        assertThat(seats).hasSize(6);

        // 4. Book Seats
        BookingResponseDto booking = bookingService.bookSeats(BookingRequestDto.builder()
                .userId(user.getId())
                .showId(show.getId())
                .seatIds(List.of(seats.get(0).getId(), seats.get(1).getId()))
                .paymentMethod("CREDIT_CARD")
                .build());

        assertThat(booking.getBookingId()).isNotNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("100.00");
    }
}
