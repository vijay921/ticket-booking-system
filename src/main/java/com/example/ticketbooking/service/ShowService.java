package com.example.ticketbooking.service;

import com.example.ticketbooking.dto.ShowRequestDto;
import com.example.ticketbooking.entity.Show;
import com.example.ticketbooking.entity.ShowSeat;
import com.example.ticketbooking.entity.SeatStatus;
import com.example.ticketbooking.exception.ResourceNotFoundException;
import com.example.ticketbooking.repository.ShowRepository;
import com.example.ticketbooking.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    @Transactional
    public Show createShow(ShowRequestDto dto) {
        int totalSeats = dto.getRows() * dto.getSeatsPerRow();
        Show show = Show.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .totalSeats(totalSeats)
                .build();

        show = showRepository.save(show);

        List<ShowSeat> seats = new ArrayList<>();
        for (int r = 0; r < dto.getRows(); r++) {
            char rowChar = (char) ('A' + r);
            for (int s = 1; s <= dto.getSeatsPerRow(); s++) {
                String seatNumber = rowChar + String.valueOf(s);
                ShowSeat seat = ShowSeat.builder()
                        .show(show)
                        .seatNumber(seatNumber)
                        .price(dto.getDefaultPrice())
                        .status(SeatStatus.AVAILABLE)
                        .build();
                seats.add(seat);
            }
        }
        showSeatRepository.saveAll(seats);
        show.setSeats(seats);
        return show;
    }

    @Transactional(readOnly = true)
    public Show getShowById(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Show> getAllShows() {
        return showRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ShowSeat> getSeatsForShow(Long showId) {
        getShowById(showId); // verify show exists
        return showSeatRepository.findByShowId(showId);
    }
}
