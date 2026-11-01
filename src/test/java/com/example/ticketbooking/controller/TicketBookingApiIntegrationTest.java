package com.example.ticketbooking.controller;

import com.example.ticketbooking.dto.BookingRequestDto;
import com.example.ticketbooking.dto.ShowRequestDto;
import com.example.ticketbooking.dto.UserRequestDto;
import com.example.ticketbooking.entity.Show;
import com.example.ticketbooking.entity.ShowSeat;
import com.example.ticketbooking.entity.User;
import com.example.ticketbooking.service.ShowService;
import com.example.ticketbooking.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TicketBookingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ShowService showService;

    @Test
    @DisplayName("POST /api/v1/users registers user successfully")
    void testCreateUserEndpoint() throws Exception {
        UserRequestDto userDto = UserRequestDto.builder()
                .name("Bob Builder")
                .email("bob_" + System.currentTimeMillis() + "@example.com")
                .phone("9876543210")
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Bob Builder"))
                .andExpect(jsonPath("$.email").value(userDto.getEmail()));
    }

    @Test
    @DisplayName("POST /api/v1/shows creates show and generates seats matrix")
    void testCreateShowEndpoint() throws Exception {
        ShowRequestDto showDto = ShowRequestDto.builder()
                .title("Inception IMAX")
                .description("Sci-Fi Thriller")
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .rows(2)
                .seatsPerRow(3)
                .defaultPrice(new BigDecimal("12.50"))
                .build();

        mockMvc.perform(post("/api/v1/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(showDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.totalSeats").value(6))
                .andExpect(jsonPath("$.title").value("Inception IMAX"));
    }

    @Test
    @DisplayName("POST /api/v1/bookings books seats and returns 201 Created")
    void testBookSeatsEndpoint() throws Exception {
        User user = userService.createUser(UserRequestDto.builder()
                .name("REST User")
                .email("rest_" + System.currentTimeMillis() + "@example.com")
                .build());

        Show show = showService.createShow(ShowRequestDto.builder()
                .title("REST Show")
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .rows(1)
                .seatsPerRow(2)
                .defaultPrice(new BigDecimal("25.00"))
                .build());

        List<ShowSeat> seats = showService.getSeatsForShow(show.getId());

        BookingRequestDto bookingRequest = BookingRequestDto.builder()
                .userId(user.getId())
                .showId(show.getId())
                .seatIds(List.of(seats.get(0).getId()))
                .paymentMethod("CREDIT_CARD")
                .build();

        // 1st booking attempt -> 201 Created
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.seatNumbers[0]").value("A1"));

        // 2nd booking attempt for same seat -> 409 Conflict
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Seat A1 is already booked"));
    }
}
