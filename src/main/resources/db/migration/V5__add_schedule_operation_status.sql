-- Departure operations: an employee marks a confirmed schedule as departed (souvenirs leave stock),
-- then as completed. Existing schedules start as SCHEDULED.
ALTER TABLE tour_schedule ADD COLUMN operation_status VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED';
ALTER TABLE tour_schedule ADD CONSTRAINT ck_schedule_operation_status
    CHECK (operation_status IN ('SCHEDULED', 'DEPARTED', 'COMPLETED'));
