# Mister World — 최종 시연 시나리오용 API 추가 (예약 수정·출발·완료)

All existing endpoints and shapes stay unchanged. JSON field names are camelCase like the existing API.
Errors use the existing ApiError shape: `{ "code": string, "message": string, "fieldErrors": [{ "field", "code", "message" }] }`.

## 1. Customer: modify a reservation's configuration (scenario 2.2.1)

`PUT /api/v1/reservations/{reservationId}/configuration` — CUSTOMER bearer token, own reservation only.

Request body (same Configuration object as reservation create; participantCount and schedule cannot change):
```json
{ "configuration": { "style": "GRAND", "hotelOption": "HOTEL_4_STAR", "transportOption": "PREMIUM_VAN_10",
                     "mealOption": "LOCAL_RESTAURANT", "extraOptions": ["COFFEE", "CHAMPAGNE"] } }
```
Response 200: the full `ReservationResponse` (identical shape to `GET /api/v1/reservations/{id}`), with the price
recalculated from the new style's unit price × the unchanged participantCount (loyalty discount kept as originally granted).

Errors:
- 401 missing/invalid token, 403 non-customer.
- 404 `RESERVATION_NOT_FOUND` (not found or not owned).
- 409 `RESERVATION_NOT_MODIFIABLE` — the schedule has already departed/completed, or its start date is not after today.
- 422 `VALIDATION_FAILED` with fieldErrors, same rules as create:
  `configuration.style` NOT_ALLOWED, `configuration.transportOption` CAPACITY_EXCEEDED, `configuration.extraOptions` DUPLICATE_VALUE.

## 2. Employee: departure operations (scenario 3)

All under `/api/v1/employee/**` — EMPLOYEE bearer token.

`GET /api/v1/employee/tour-schedules` → 200, ordered by startDate then id:
```json
[ { "id": 5, "tourId": 2, "tourName": "…", "theme": "OUTDOOR_TREKKING",
    "startDate": "2026-12-19", "endDate": "2026-12-21",
    "participantCount": 3, "confirmed": true, "operationStatus": "SCHEDULED" } ]
```
`operationStatus` ∈ `SCHEDULED` | `DEPARTED` | `COMPLETED`.

`POST /api/v1/employee/tour-schedules/{scheduleId}/departure` (no body) → 200:
```json
{ "schedule": { …same item as above, operationStatus "DEPARTED"… },
  "deductedItems": [ { "itemType": "SCARF", "quantity": 3, "remainingQuantity": 17 } ] }
```
Souvenir deduction per Theme (one item type per Theme):
- HONEYMOON_ROMANCE → COUPLE_TSHIRT × (participants / 2)  (one per couple)
- PARENTS_HEALING → GINSENG_GIFT × participants
- GOLF_CHALLENGE → GOLF_BALL × participants
- OUTDOOR_TREKKING → SCARF × participants
If the schedule has 0 participants, `deductedItems` is empty.

Errors: 404 `TOUR_SCHEDULE_NOT_FOUND`; 409 `SCHEDULE_NOT_CONFIRMED` (recruitment not confirmed yet);
409 `SCHEDULE_ALREADY_DEPARTED` (status is not SCHEDULED); 409 `INSUFFICIENT_INVENTORY` (stock below required quantity — nothing is changed).

`POST /api/v1/employee/tour-schedules/{scheduleId}/completion` (no body) → 200 `{ "schedule": { …operationStatus "COMPLETED"… } }`
Errors: 404 `TOUR_SCHEDULE_NOT_FOUND`; 409 `SCHEDULE_NOT_DEPARTED` (status is not DEPARTED).

## 3. Side effects on existing endpoints
- Public `GET /api/v1/tour-schedules…`: `reservable` is false once a schedule is DEPARTED or COMPLETED. Reservation create on such a schedule → 409 `SCHEDULE_NOT_RESERVABLE` (existing code).
- `GET /api/v1/customers/me/travel-history` (My Trips) now also lists reservations whose schedule is COMPLETED, even before the end date. Loyalty discount uses the same "completed" rule.
