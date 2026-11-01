# Ticket Booking System (Spring Boot + PostgreSQL LLD)

A production-grade experimentation project demonstrating Low-Level Design (LLD) for a concurrent ticket booking system using **Spring Boot 3.x**, **Spring Data JPA**, and **PostgreSQL** with pessimistic database locking (`SELECT ... FOR UPDATE`) to prevent double-booking under high concurrency.

---

## 1. Architectural Highlights

- **Concurrency & Double-Booking Prevention**:
  Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `ShowSeat` entities during booking. When concurrent users select the same seat, database row-level locking guarantees that only the first transaction proceeds to `CONFIRMED` status, while subsequent contenders cleanly fail with `SeatAlreadyBookedException` (HTTP 409 Conflict).
- **Relational Domain Design**:
  - `User`: Customers creating reservations.
  - `Show`: Events/performances with start and end timestamps.
  - `ShowSeat`: Matrix of seats belonging to a show with price and status (`AVAILABLE`, `BOOKED`), protected by a unique constraint on `(show_id, seat_number)`.
  - `Booking`: Represents a confirmed ticket reservation with atomic total price calculation.
  - `Payment`: Associated payment transaction record (`SUCCESS`, `PENDING`, `FAILED`) committed atomically with the booking.
- **Error Handling**:
  `@RestControllerAdvice` translating domain exceptions into standard HTTP error responses (409 Conflict, 404 Not Found, 400 Bad Request).

---

## 2. Prerequisites & Running

### Requirements
- **Java 17+** (tested and verified on Java 23)
- **Apache Maven 3.9+** (portable distribution included under `maven_extracted/` or use your installed `mvn`)
- **Docker & Docker Compose** (optional: for local PostgreSQL 16 container)

### Step 1: Start PostgreSQL (via Docker)
```bash
docker compose up -d
```
*Note: If running without Docker, the test profile defaults to an embedded in-memory H2 database with all schemas generated automatically.*

### Step 2: Build and Run Tests
```bash
# Using installed maven or extracted binary:
./maven_extracted/apache-maven-3.9.6/bin/mvn clean test
```
All 6 automated unit, integration, and multi-threaded concurrency tests will run:
- Multi-threaded concurrency test (10 parallel threads competing for the exact same seat simultaneously).
- Validation tests (empty seat lists, duplicate booking rejection).
- Full MockMvc HTTP REST controller flows.

### Step 3: Run the Application
```bash
./maven_extracted/apache-maven-3.9.6/bin/mvn spring-boot:run
```
The server will start on `http://localhost:8080`.

---

## 3. REST API Usage & Examples

### 1. Create a User
```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Smith",
    "email": "alice@example.com",
    "phone": "9876543210"
  }'
```

### 2. Create a Show (with automated Seat Matrix Generation)
```bash
curl -X POST http://localhost:8080/api/v1/shows \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Interstellar IMAX 70mm",
    "description": "Christopher Nolan classic screening",
    "startTime": "2026-11-10T18:00:00Z",
    "endTime": "2026-11-10T21:00:00Z",
    "rows": 5,
    "seatsPerRow": 10,
    "defaultPrice": 20.00
  }'
```

### 3. List Available Seats for a Show
```bash
curl -X GET http://localhost:8080/api/v1/shows/1/seats
```

### 4. Book Seats (Atomic Transaction + Pessimistic Lock)
```bash
curl -X POST http://localhost:8080/api/v1/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "showId": 1,
    "seatIds": [1, 2],
    "paymentMethod": "CREDIT_CARD"
  }'
```

**Success Response (201 Created):**
```json
{
  "bookingId": 1,
  "userId": 1,
  "showId": 1,
  "seatNumbers": ["A1", "A2"],
  "totalAmount": 40.00,
  "status": "CONFIRMED",
  "paymentStatus": "SUCCESS",
  "transactionRef": "TXN-A3B8C1D2",
  "bookingTime": "2026-11-01T17:35:00Z"
}
```

**Conflict Response (409 Conflict - Double-booking attempt):**
```json
{
  "timestamp": "2026-11-01T17:35:05Z",
  "status": 409,
  "error": "Conflict",
  "message": "Seat A1 is already booked"
}
```
