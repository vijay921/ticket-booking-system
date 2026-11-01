# Ticket Booking System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot REST API for a Ticket Booking System backed by PostgreSQL with pessimistic row-level locking for atomic seat reservations and concurrent booking safety.

**Architecture:** Standard Spring Boot layered architecture (Controller -> Service -> Repository -> PostgreSQL) with JPA/Hibernate entities, DTO validation, custom exception handling, and concurrent test verification.

**Tech Stack:** Java 17/21, Spring Boot 3.x, Spring Data JPA, PostgreSQL 16 (Docker), Maven, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-01-ticket-booking-system-design.md`

## Global Constraints

- Java 17+ / Spring Boot 3.x.
- Database: PostgreSQL 16 via Docker Compose (`ticket_booking` db, user `postgres`, password `password`).
- Concurrency control: Spring Data JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` for seat booking.
- Unique constraints: `(show_id, seat_number)` on `show_seats`, unique `email` on `users`.

## Review Focus

1. **Double-booking under concurrent load:** 10 threads trying to book the exact same seat simultaneously must result in exactly 1 success and 9 failures with HTTP 409 Conflict / `SeatAlreadyBookedException`.
2. **Invalid Seat IDs / Show Mismatch:** Booking seats belonging to a different show or non-existent seat IDs must be cleanly rejected.
3. **Rollback on Payment/Booking Error:** Transaction rollback must release row locks and leave seat status as `AVAILABLE` if any step fails.
4. **Empty Seat List:** Attempting to book 0 seats must return a 400 Bad Request.
5. **Non-existent User/Show:** Booking with an invalid `userId` or `showId` must throw appropriate resource not found exceptions.

---

### Task 1: Project Scaffolding & Configuration

**Files:**
- Create: `pom.xml`
- Create: `docker-compose.yml`
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/example/ticketbooking/TicketBookingApplication.java`

**Interfaces:**
- Consumes: None
- Produces: Spring Boot application bootstrap and Postgres container setup.

- [ ] **Step 1: Write `pom.xml` with Spring Boot 3.x dependencies (web, data-jpa, postgresql, validation, lombok, test)**
- [ ] **Step 2: Write `docker-compose.yml` for PostgreSQL 16 service**
- [ ] **Step 3: Write `src/main/resources/application.yml` for datasource and JPA configuration**
- [ ] **Step 4: Write `TicketBookingApplication.java` main class**
- [ ] **Step 5: Commit scaffolding**

---

### Task 2: Domain Entities & Repositories

**Files:**
- Create: `src/main/java/com/example/ticketbooking/entity/User.java`
- Create: `src/main/java/com/example/ticketbooking/entity/Show.java`
- Create: `src/main/java/com/example/ticketbooking/entity/ShowSeat.java`
- Create: `src/main/java/com/example/ticketbooking/entity/Booking.java`
- Create: `src/main/java/com/example/ticketbooking/entity/Payment.java`
- Create: `src/main/java/com/example/ticketbooking/repository/UserRepository.java`
- Create: `src/main/java/com/example/ticketbooking/repository/ShowRepository.java`
- Create: `src/main/java/com/example/ticketbooking/repository/ShowSeatRepository.java`
- Create: `src/main/java/com/example/ticketbooking/repository/BookingRepository.java`

**Interfaces:**
- Consumes: Task 1 setup
- Produces: JPA Entities and repositories with `@Lock(LockModeType.PESSIMISTIC_WRITE)` query method `findSeatsWithLock`.

- [ ] **Step 1: Create `User`, `Show`, `ShowSeat`, `Booking`, `Payment` entity classes with annotations, relationships, and enums**
- [ ] **Step 2: Create `UserRepository`, `ShowRepository`, `BookingRepository` interfaces**
- [ ] **Step 3: Create `ShowSeatRepository` interface with pessimistic lock query:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM ShowSeat s WHERE s.id IN :seatIds AND s.show.id = :showId")
List<ShowSeat> findSeatsWithLock(@Param("seatIds") List<Long> seatIds, @Param("showId") Long showId);
```
- [ ] **Step 4: Commit entities and repositories**

---

### Task 3: DTOs & Exception Handling

**Files:**
- Create: `src/main/java/com/example/ticketbooking/dto/UserRequestDto.java`
- Create: `src/main/java/com/example/ticketbooking/dto/ShowRequestDto.java`
- Create: `src/main/java/com/example/ticketbooking/dto/BookingRequestDto.java`
- Create: `src/main/java/com/example/ticketbooking/dto/BookingResponseDto.java`
- Create: `src/main/java/com/example/ticketbooking/exception/SeatAlreadyBookedException.java`
- Create: `src/main/java/com/example/ticketbooking/exception/ResourceNotFoundException.java`
- Create: `src/main/java/com/example/ticketbooking/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: Task 2 entities
- Produces: Request/Response DTOs and REST exception handlers for 409 Conflict, 404 Not Found, 400 Bad Request.

- [ ] **Step 1: Create DTO classes with validation annotations (`@NotNull`, `@NotEmpty`, etc.)**
- [ ] **Step 2: Create custom exception classes (`SeatAlreadyBookedException`, `ResourceNotFoundException`)**
- [ ] **Step 3: Create `GlobalExceptionHandler` with `@ControllerAdvice` handling custom exceptions and method argument validation**
- [ ] **Step 4: Commit DTOs and exception handling**

---

### Task 4: Service Layer

**Files:**
- Create: `src/main/java/com/example/ticketbooking/service/UserService.java`
- Create: `src/main/java/com/example/ticketbooking/service/ShowService.java`
- Create: `src/main/java/com/example/ticketbooking/service/BookingService.java`

**Interfaces:**
- Consumes: Task 2 repositories & Task 3 exceptions/DTOs
- Produces: Business logic services including atomic `@Transactional` booking method with pessimistic locking.

- [ ] **Step 1: Create `UserService` for creating and fetching users**
- [ ] **Step 2: Create `ShowService` for creating shows (with automatic seat matrix generation) and fetching shows/seats**
- [ ] **Step 3: Create `BookingService` with `@Transactional`: locks seats via `findSeatsWithLock`, validates availability, updates seats to `BOOKED`, creates `Booking`, creates successful `Payment` record, and returns `BookingResponseDto`**
- [ ] **Step 4: Commit service layer**

---

### Task 5: REST Controllers

**Files:**
- Create: `src/main/java/com/example/ticketbooking/controller/UserController.java`
- Create: `src/main/java/com/example/ticketbooking/controller/ShowController.java`
- Create: `src/main/java/com/example/ticketbooking/controller/BookingController.java`

**Interfaces:**
- Consumes: Task 4 services & Task 3 DTOs
- Produces: REST endpoints `/api/v1/users`, `/api/v1/shows`, `/api/v1/bookings`.

- [ ] **Step 1: Create `UserController`**
- [ ] **Step 2: Create `ShowController`**
- [ ] **Step 3: Create `BookingController`**
- [ ] **Step 4: Commit controllers**

---

### Task 6: Concurrency & Integration Testing

**Files:**
- Create: `src/test/java/com/example/ticketbooking/service/BookingServiceConcurrencyTest.java`

**Interfaces:**
- Consumes: Task 4 `BookingService` and infrastructure
- Produces: Multi-threaded concurrency test verifying lock safety and prevention of double-booking.

- [ ] **Step 1: Write `BookingServiceConcurrencyTest` using `ExecutorService` and `CountDownLatch` to invoke concurrent booking requests for the same seat**
- [ ] **Step 2: Run test verifying exactly 1 success and N-1 `SeatAlreadyBookedException` / HTTP 409 responses**
- [ ] **Step 3: Commit tests**
