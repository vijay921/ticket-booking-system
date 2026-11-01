package com.example.ticketbooking;

import com.example.ticketbooking.dto.BookingRequestDto;
import com.example.ticketbooking.dto.BookingResponseDto;
import com.example.ticketbooking.dto.ShowRequestDto;
import com.example.ticketbooking.dto.UserRequestDto;
import com.example.ticketbooking.entity.SeatStatus;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class E2EMultiUserShowsTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ShowService showService;

    @Autowired
    private BookingService bookingService;

    @Test
    @DisplayName("Test E2E flow with 3 users and 5 shows booking seats successfully")
    void testMultiUserMultiShowWorkflow() {
        // 1. Create 3 Users
        List<User> users = new ArrayList<>();
        String[] userNames = {"Alice Smith", "Bob Jones", "Charlie Brown"};
        for (int i = 0; i < 3; i++) {
            User user = userService.createUser(UserRequestDto.builder()
                    .name(userNames[i])
                    .email("user" + (i + 1) + "_" + System.currentTimeMillis() + "@example.com")
                    .phone("98765432" + i)
                    .build());
            users.add(user);
        }
        assertThat(users).hasSize(3);

        // 2. Create 5 Shows
        List<Show> shows = new ArrayList<>();
        String[] showTitles = {
            "Interstellar IMAX Re-release",
            "The Matrix Orchestral Live",
            "Dune: Part Two Premiere",
            "Inception 70mm Screening",
            "Blade Runner 2049 Concert"
        };

        for (int i = 0; i < 5; i++) {
            Show show = showService.createShow(ShowRequestDto.builder()
                    .title(showTitles[i])
                    .description("Sci-Fi Masterpiece screening #" + (i + 1))
                    .startTime(Instant.now().plus(i + 1, ChronoUnit.DAYS))
                    .endTime(Instant.now().plus(i + 1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                    .rows(2)
                    .seatsPerRow(5) // 10 seats per show
                    .defaultPrice(new BigDecimal("15.00").add(new BigDecimal(i * 5)))
                    .build());
            shows.add(show);
        }
        assertThat(shows).hasSize(5);

        // 3. Perform bookings across multiple shows by the 3 users
        int totalBookings = 0;
        for (int s = 0; s < shows.size(); s++) {
            Show show = shows.get(s);
            List<ShowSeat> seats = showService.getSeatsForShow(show.getId());
            assertThat(seats).isNotEmpty();

            // Each user books 1-2 seats in this show
            for (int u = 0; u < users.size(); u++) {
                User user = users.get(u);
                int seatIndex = (s + u) % seats.size();
                ShowSeat seat = seats.get(seatIndex);

                if (seat.getStatus() == SeatStatus.AVAILABLE) {
                    BookingResponseDto booking = bookingService.bookSeats(BookingRequestDto.builder()
                            .userId(user.getId())
                            .showId(show.getId())
                            .seatIds(List.of(seat.getId()))
                            .paymentMethod(u % 2 == 0 ? "CREDIT_CARD" : "UPI")
                            .build());

                    assertThat(booking).isNotNull();
                    assertThat(booking.getStatus()).isEqualTo(com.example.ticketbooking.entity.BookingStatus.CONFIRMED);
                    totalBookings++;
                }
            }
        }

        System.out.println("Successfully completed multi-user (3 users) multi-show (5 shows) test with " + totalBookings + " confirmed bookings!");
        assertThat(totalBookings).isGreaterThan(0);
    }
}
