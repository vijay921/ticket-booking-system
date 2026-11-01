package com.example.ticketbooking.controller;

import com.example.ticketbooking.dto.ShowRequestDto;
import com.example.ticketbooking.entity.Show;
import com.example.ticketbooking.entity.ShowSeat;
import com.example.ticketbooking.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @PostMapping
    public ResponseEntity<Show> createShow(@Valid @RequestBody ShowRequestDto showRequestDto) {
        Show created = showService.createShow(showRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Show>> getAllShows() {
        return ResponseEntity.ok(showService.getAllShows());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Show> getShowById(@PathVariable Long id) {
        return ResponseEntity.ok(showService.getShowById(id));
    }

    @GetMapping("/{id}/seats")
    public ResponseEntity<List<ShowSeat>> getSeatsForShow(@PathVariable Long id) {
        return ResponseEntity.ok(showService.getSeatsForShow(id));
    }
}
