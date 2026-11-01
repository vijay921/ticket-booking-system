package com.example.ticketbooking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "show_seats",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_show_seat", columnNames = {"show_id", "seat_number"})
    },
    indexes = {
        @Index(name = "idx_show_seats_show_status", columnList = "show_id, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "seat_number", nullable = false, length = 10)
    private String seatNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;
}
