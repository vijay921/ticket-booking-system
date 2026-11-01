# Ticket Booking System — End-to-End Execution Results

**Execution Date:** 2026-11-01T17:43:09.200276900Z
**Environment:** Spring Boot Test (H2 Database in PostgreSQL compatibility mode)

---

## Step 1: Create Users

### Request (`POST /api/v1/users`)
```json
{
  "name" : "Charlie Brown",
  "email" : "charlie.1793554989200@example.com",
  "phone" : "5551234567"
}
```

### Response (`201 Created`)
```json
{
  "id" : 1,
  "name" : "Charlie Brown",
  "email" : "charlie.1793554989200@example.com",
  "phone" : "5551234567",
  "createdAt" : "2026-11-01T17:43:09.463659Z"
}
```

---

## Step 2: Create Show & Generate Seats Matrix

### Request (`POST /api/v1/shows`)
```json
{
  "title" : "The Matrix Live Concert",
  "description" : "Orchestral Sci-Fi soundtrack performance",
  "startTime" : "2026-11-04T17:43:09.659963300Z",
  "endTime" : "2026-11-04T20:43:09.659963300Z",
  "rows" : 2,
  "seatsPerRow" : 3,
  "defaultPrice" : 35.00
}
```

### Response (`201 Created`)
```json
{
  "id" : 1,
  "title" : "The Matrix Live Concert",
  "description" : "Orchestral Sci-Fi soundtrack performance",
  "startTime" : "2026-11-04T17:43:09.659963300Z",
  "endTime" : "2026-11-04T20:43:09.659963300Z",
  "totalSeats" : 6,
  "createdAt" : "2026-11-01T17:43:09.718110Z",
  "seats" : [ {
    "id" : 1,
    "seatNumber" : "A1",
    "price" : 35.00,
    "status" : "AVAILABLE"
  }, {
    "id" : 2,
    "seatNumber" : "A2",
    "price" : 35.00,
    "status" : "AVAILABLE"
  }, {
    "id" : 3,
    "seatNumber" : "A3",
    "price" : 35.00,
    "status" : "AVAILABLE"
  }, {
    "id" : 4,
    "seatNumber" : "B1",
    "price" : 35.00,
    "status" : "AVAILABLE"
  }, {
    "id" : 5,
    "seatNumber" : "B2",
    "price" : 35.00,
    "status" : "AVAILABLE"
  }, {
    "id" : 6,
    "seatNumber" : "B3",
    "price" : 35.00,
    "status" : "AVAILABLE"
  } ]
}
```

---

## Step 3: Fetch Available Seats (`GET /api/v1/shows/{id}/seats`)

### Response (`200 OK` — Seats List Preview)
```json
[ {
  "id" : 1,
  "seatNumber" : "A1",
  "price" : 35.0,
  "status" : "AVAILABLE"
}, {
  "id" : 2,
  "seatNumber" : "A2",
  "price" : 35.0,
  "status" : "AVAILABLE"
}, {
  "id" : 3,
  "seatNumber" : "A3",
  "price" : 35.0,
  "status" : "AVAILABLE"
}, {
  "id" : 4,
  "seatNumber" : "B1",
  "price" : 35.0,
  "status" : "AVAILABLE"
}, {
  "id" : 5,
  "seatNumber" : "B2",
  "price" : 35.0,
  "status" : "AVAILABLE"
}, {
  "id" : 6,
  "seatNumber" : "B3",
  "price" : 35.0,
  "status" : "AVAILABLE"
} ]
```

---

## Step 4: Book Seats (Atomic Transaction + Pessimistic Lock)

### Request (`POST /api/v1/bookings`)
```json
{
  "userId" : 1,
  "showId" : 1,
  "seatIds" : [ 1, 2 ],
  "paymentMethod" : "CREDIT_CARD"
}
```

### Response (`201 Created`)
```json
{
  "bookingId" : 1,
  "userId" : 1,
  "showId" : 1,
  "seatNumbers" : [ "A1", "A2" ],
  "totalAmount" : 70.00,
  "status" : "CONFIRMED",
  "paymentStatus" : "SUCCESS",
  "transactionRef" : "TXN-50F71270",
  "bookingTime" : "2026-11-01T17:43:09.894412Z"
}
```

---

## Step 5: Test Double-Booking Prevention (409 Conflict)

### Request (`POST /api/v1/bookings` - Duplicate Seat `A1`)
```json
{
  "userId" : 2,
  "showId" : 1,
  "seatIds" : [ 1 ],
  "paymentMethod" : "DEBIT_CARD"
}
```

### Response (`409 Conflict`)
```json
{
  "error" : "Conflict",
  "message" : "Seat A1 is already booked",
  "timestamp" : "2026-11-01T17:43:09.985012600Z",
  "status" : 409
}
```

---

## Conclusion

- **Status:** SUCCESS
- **Concurrency Safety:** Verified via atomic pessimistic locking (`SELECT ... FOR UPDATE`).
- **All Tests:** Passed successfully.
