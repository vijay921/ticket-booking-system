package com.example.ticketbooking.repository;

import com.example.ticketbooking.entity.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s WHERE s.id IN :seatIds AND s.show.id = :showId")
    List<ShowSeat> findSeatsWithLock(@Param("seatIds") List<Long> seatIds, @Param("showId") Long showId);

    List<ShowSeat> findByShowId(Long showId);
}
