# Low-Level Design (LLD): Ticket Booking System with Spring Boot & PostgreSQL

**Date:** 2026-11-01  
**Status:** Approved  
**Target Environment:** Java 21 / 17, Spring Boot 3.x, PostgreSQL 16 (Docker Compose), Spring Data JPA, Hibernate, Maven

---

## 1. Executive Summary

This document specifies the architecture and low-level design for a Ticket Booking System experimentation application built with Spring Boot and PostgreSQL running in Docker. The system provides core REST APIs for managing Users, Shows, Seats, Bookings, and Payments, utilizing database pessimistic locking (`SELECT ... FOR UPDATE`) to guarantee zero double-booking under concurrent load.

---

## 2. Requirements & Scope

### 2.1 Functional Requirements
- **User Management**: Register and retrieve users.
- **Show & Seat Management**:
  - Create shows and automatically generate seat matrices (e.g., Row A-J, Seats 1-10).
  - Query shows and view real-time seat availability (`AVAILABLE`, `BOOKED`).
- **Booking & Concurrency**:
  - Atomic booking of one or multiple seats for a show.
  - Concurrency control preventing duplicate bookings of the same seat using PostgreSQL row-level pessimistic write locks.
- **Payment Processing**:
  - Record payment status (`SUCCESS`, `FAILED`, `PENDING`) linked to each booking transaction.
- **Infrastructure**:
  - `docker-compose.yml` for running PostgreSQL database container with health checks and persistent volume.

### 2.2 Non-Functional Requirements
- **ACID Compliance**: Transactions are committed atomically; failure during payment or seat reservation rolls back the entire booking.
- **Data Integrity**: Enforce unique database constraints on `(show_id, seat_number)` and `email`.
- **Low Latency & High Isolation**: Efficient indexed lookups on seats by show and status.

---

## 3. Architecture & Domain Model

### 3.1 Entity Relationship Diagram (ERD)

```
+--------------------+           +------------------------+
|       User         |           |         Show           |
+--------------------+           +------------------------+
| id: Long (PK)      |           | id: Long (PK)          |
| name: String       |           | title: String          |
| email: String (UQ) |           | description: String    |
| phone: String      |           | startTime: Instant     |
| createdAt: Instant |           | endTime: Instant       |
+---------+----------+           | totalSeats: Integer    |
          |                      +-----------+------------+
          | 1                                | 1
          |                                  |
          | N                                | N
+---------v----------+           +-----------v------------+
|      Booking       | N       M |       ShowSeat         |
+--------------------+-----------+------------------------+
| id: Long (PK)      |           | id: Long (PK)          |
| user_id: Long (FK) |           | show_id: Long (FK)     |
| show_id: Long (FK) |           | seatNumber: String     |
| status: Enum       |           | price: BigDecimal      |
| totalAmount: Dec   |           | status: Enum (AVAIL,   |
| bookingTime: Inst  |           |               BOOKED)  |
+---------+----------+           +------------------------+
          | 1                     (Unique: show_id, seatNumber)
          |
          | 1
+---------v----------+
|      Payment       |
+--------------------+
| id: Long (PK)      |
| booking_id: (FK)   |
| amount: BigDecimal |
| method: Enum       |
| status: Enum       |
| transactionRef: Str|
| paymentTime: Inst  |
+--------------------+
```

### 3.2 Database Schema DDL

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    phone VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shows (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    total_seats INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE show_seats (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    seat_number VARCHAR(10) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT uq_show_seat UNIQUE (show_id, seat_number)
);
CREATE INDEX idx_show_seats_show_status ON show_seats(show_id, status);

CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    show_id BIGINT NOT NULL REFERENCES shows(id),
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    booking_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE booking_seats (
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    seat_id BIGINT NOT NULL REFERENCES show_seats(id),
    PRIMARY KEY (booking_id, seat_id)
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    amount NUMERIC(10, 2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_ref VARCHAR(100) NOT NULL,
    payment_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

---

## 4. Concurrency & Booking Logic (LLD)

### 4.1 Locking Strategy
To prevent double booking:
- The `ShowSeatRepository` uses Spring Data JPA's `@Lock(LockModeType.PESSIMISTIC_WRITE)`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM ShowSeat s WHERE s.id IN :seatIds AND s.show.id = :showId")
List<ShowSeat> findSeatsWithLock(@Param("seatIds") List<Long> seatIds, @Param("showId") Long showId);
```
- In SQL, this translates to:
  ```sql
  SELECT * FROM show_seats 
  WHERE id IN (?, ?) AND show_id = ? 
  FOR UPDATE;
  ```
- Any parallel transaction trying to select these same rows for booking will be blocked at the database row level until the first transaction completes (commits or rolls back).

### 4.2 Booking Service Step-by-Step Flow
1. **Validate Request**: Ensure `seatIds` is not empty, `userId` exists, `showId` exists.
2. **Acquire Locks**: Call `findSeatsWithLock(seatIds, showId)`.
3. **Validate Availability**:
   - Verify that all requested `seatIds` exist in the result.
   - For every seat, ensure `seat.getStatus() == SeatStatus.AVAILABLE`.
   - If any seat is `BOOKED`, throw `SeatAlreadyBookedException("Seat " + seat.getSeatNumber() + " is already booked")`.
4. **Update Seats**:
   - Set each seat's status to `BOOKED`.
   - Sum `seat.getPrice()` to compute `totalAmount`.
5. **Create & Save Booking**:
   - Build `Booking` with `status = BookingStatus.CONFIRMED`.
   - Associate seats with booking.
6. **Process Payment Record**:
   - Mock payment processing (instant success or parameterized method).
   - Create `Payment` record with status `SUCCESS` and unique UUID `transactionRef`.
7. **Commit & Return DTO**:
   - Transaction commits, releasing DB locks.
   - Return `BookingResponseDto` containing booking ID, seat numbers, total amount, and confirmation details.

---

## 5. REST API Specifications

### 5.1 User Endpoints
- `POST /api/v1/users`
  - Body: `{"name": "Alice", "email": "alice@example.com", "phone": "1234567890"}`
  - Response: `201 Created` with `UserDto`
- `GET /api/v1/users/{id}`
  - Response: `200 OK`

### 5.2 Show & Seat Endpoints
- `POST /api/v1/shows`
  - Body:
    ```json
    {
      "title": "Interstellar Screening",
      "description": "IMAX 70mm re-release",
      "startTime": "2026-11-10T18:00:00Z",
      "endTime": "2026-11-10T21:00:00Z",
      "rows": 5,
      "seatsPerRow": 10,
      "defaultPrice": 15.00
    }
    ```
  - Response: `201 Created` with `ShowDto` and generated seats count.
- `GET /api/v1/shows`
  - Response: `200 OK` with `List<ShowDto>`
- `GET /api/v1/shows/{id}/seats`
  - Query params: `?status=AVAILABLE` (optional filter)
  - Response: `200 OK` with `List<ShowSeatDto>`

### 5.3 Booking Endpoints
- `POST /api/v1/bookings`
  - Body:
    ```json
    {
      "userId": 1,
      "showId": 1,
      "seatIds": [1, 2],
      "paymentMethod": "CREDIT_CARD"
    }
    ```
  - Success Response: `201 Created`
    ```json
    {
      "bookingId": 101,
      "userId": 1,
      "showId": 1,
      "seats": ["A1", "A2"],
      "totalAmount": 30.00,
      "status": "CONFIRMED",
      "paymentStatus": "SUCCESS",
      "transactionRef": "TXN-8f3b-456c",
      "bookingTime": "2026-11-01T12:00:00Z"
    }
    ```
  - Conflict Response: `409 Conflict` (when seat is already booked)
- `GET /api/v1/bookings/{id}`
  - Response: `200 OK` with full booking details.

---

## 6. Project Setup & Infrastructure

### 6.1 Directory Structure
```
ticket-booking-system/
├── docker-compose.yml
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/ticketbooking/
    │   │   ├── TicketBookingApplication.java
    │   │   ├── controller/
    │   │   ├── dto/
    │   │   ├── entity/
    │   │   ├── exception/
    │   │   ├── repository/
    │   │   └── service/
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/ (or schema.sql / Hibernate auto-ddl)
    └── test/
        └── java/com/example/ticketbooking/
            └── service/BookingServiceConcurrencyTest.java
```

### 6.2 Docker Compose Configuration
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    container_name: ticket_booking_db
    environment:
      POSTGRES_DB: ticket_booking
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d ticket_booking"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

---

## 7. Testing Strategy
- **Unit Tests**: Mockito tests verifying business rules, seat validation, and status transitions.
- **Integration & Concurrency Tests**:
  - `BookingServiceConcurrencyTest`: Spin up parallel threads (e.g. `ExecutorService` with 10 threads) attempting to book the exact same seat simultaneously.
  - Assert that exactly 1 booking succeeds (`201 CONFIRMED`) and all 9 others receive `SeatAlreadyBookedException`.
