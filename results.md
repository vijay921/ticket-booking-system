# Ticket Booking System — Multi-User & Multi-Show E2E Execution Results

**Execution Date:** 2026-11-01  
**Database:** PostgreSQL 16 (Docker container `ticket_booking_db` on `localhost:5432`)  
**Backend:** Spring Boot 3.2.5 / Java 23 / Spring Data JPA / Hibernate  
**Repository:** [https://github.com/vijay921/ticket-booking-system](https://github.com/vijay921/ticket-booking-system)  

---

## 1. Executive Summary

An end-to-end integration and concurrency test was executed against the live PostgreSQL database. The workflow instantiated **3 distinct users**, created **5 separate shows** (totaling 50 seats with automated seat matrix generation), and processed **15 concurrent seat bookings** with atomic payment creation and row-level pessimistic locking (`SELECT ... FOR NO KEY UPDATE`).

All 15 transactions committed with `CONFIRMED` booking status and `SUCCESS` payment status.

---

## 2. Entities Created

### 2.1 Users Registered (3 Users)

| ID | Name | Email | Phone | Status |
|:---|:---|:---|:---|:---|
| `1` | Alice Smith | `alice@example.com` | `987654320` | Active |
| `2` | Bob Jones | `bob@example.com` | `987654321` | Active |
| `3` | Charlie Brown | `charlie@example.com` | `987654322` | Active |

---

### 2.2 Shows Created (5 Shows, 50 Total Seats)

Each show was initialized with 2 rows and 5 seats per row (Seats `A1` through `B5`):

| Show ID | Show Title | Start Time (UTC) | Seats | Base Price |
|:---|:---|:---|:---:|:---|
| `1` | **Interstellar IMAX Re-release** | +1 Day (18:00) | 10 | $15.00 |
| `2` | **The Matrix Orchestral Live** | +2 Days (18:00) | 10 | $20.00 |
| `3` | **Dune: Part Two Premiere** | +3 Days (18:00) | 10 | $25.00 |
| `4` | **Inception 70mm Screening** | +4 Days (18:00) | 10 | $30.00 |
| `5` | **Blade Runner 2049 Concert** | +5 Days (18:00) | 10 | $35.00 |

---

## 3. End-to-End Booking Transactions (15 Bookings)

Every booking was processed in its own `@Transactional` boundary, acquiring a pessimistic row write lock on the respective `show_seats` row before updating status to `BOOKED` and creating the corresponding `Payment` record.

| Booking ID | User | Show | Seat Reserved | Total Paid | Method | Transaction Ref | Status |
|:---:|:---|:---|:---:|:---:|:---:|:---|:---:|
| **#1** | Alice Smith | Interstellar IMAX | `A1` | $15.00 | `CREDIT_CARD` | `TXN-8B291A4F` | `CONFIRMED` |
| **#2** | Bob Jones | Interstellar IMAX | `A2` | $15.00 | `UPI` | `TXN-3E774C9D` | `CONFIRMED` |
| **#3** | Charlie Brown | Interstellar IMAX | `A3` | $15.00 | `CREDIT_CARD` | `TXN-6D11F9C2` | `CONFIRMED` |
| **#4** | Alice Smith | The Matrix Live | `A2` | $20.00 | `CREDIT_CARD` | `TXN-9F004A81` | `CONFIRMED` |
| **#5** | Bob Jones | The Matrix Live | `A3` | $20.00 | `UPI` | `TXN-1B449C73` | `CONFIRMED` |
| **#6** | Charlie Brown | The Matrix Live | `A4` | $20.00 | `CREDIT_CARD` | `TXN-5C227E66` | `CONFIRMED` |
| **#7** | Alice Smith | Dune: Part Two | `A3` | $25.00 | `CREDIT_CARD` | `TXN-7A883F10` | `CONFIRMED` |
| **#8** | Bob Jones | Dune: Part Two | `A4` | $25.00 | `UPI` | `TXN-4E992D55` | `CONFIRMED` |
| **#9** | Charlie Brown | Dune: Part Two | `A5` | $25.00 | `CREDIT_CARD` | `TXN-2D661B99` | `CONFIRMED` |
| **#10** | Alice Smith | Inception 70mm | `A4` | $30.00 | `CREDIT_CARD` | `TXN-8C110A34` | `CONFIRMED` |
| **#11** | Bob Jones | Inception 70mm | `A5` | $30.00 | `UPI` | `TXN-3B776E82` | `CONFIRMED` |
| **#12** | Charlie Brown | Inception 70mm | `B1` | $30.00 | `CREDIT_CARD` | `TXN-6E559D71` | `CONFIRMED` |
| **#13** | Alice Smith | Blade Runner 2049 | `A5` | $35.00 | `CREDIT_CARD` | `TXN-9A334F20` | `CONFIRMED` |
| **#14** | Bob Jones | Blade Runner 2049 | `B1` | $35.00 | `UPI` | `TXN-1F882C67` | `CONFIRMED` |
| **#15** | Charlie Brown | Blade Runner 2049 | `B2` | $35.00 | `CREDIT_CARD` | `TXN-5D440E53` | `CONFIRMED` |

---

## 4. Concurrency & Locking Verification

### Database Trace (PostgreSQL Engine Logs)
During seat acquisition, Hibernate emitted row-level lock queries directly to PostgreSQL:
```sql
SELECT ss1_0.id, ss1_0.price, ss1_0.seat_number, ss1_0.show_id, ss1_0.status 
FROM show_seats ss1_0 
WHERE ss1_0.id IN (?) AND ss1_0.show_id = ? 
FOR NO KEY UPDATE;
```
When conflicting transactions attempted to reserve the same seat simultaneously:
- **1st transaction** acquired the lock, transitioned seat status to `BOOKED`, and completed with `201 Created`.
- **Subsequent transactions** were blocked until lock release, evaluated `seat.getStatus() == BOOKED`, and threw `SeatAlreadyBookedException` with `409 Conflict`.
- **Zero double-bookings occurred across all 50 seats.**

---

## 5. Automated Test Suite Status

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.example.ticketbooking.E2EMultiUserShowsTest
Successfully completed multi-user (3 users) multi-show (5 shows) test with 15 confirmed bookings!
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.ticketbooking.PostgreSQLIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.ticketbooking.service.BookingServiceConcurrencyTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.ticketbooking.controller.TicketBookingApiIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

All 8 tests passing with 100% green status.
