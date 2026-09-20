CREATE TABLE routes (
    id UUID PRIMARY KEY,
    origin VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    fare NUMERIC(10, 2) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT routes_origin_destination_different CHECK (origin <> destination),
    CONSTRAINT routes_fare_non_negative CHECK (fare >= 0),
    CONSTRAINT routes_duration_positive CHECK (duration_minutes > 0)
);

CREATE TABLE seat_layouts (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE seat_layout_seats (
    id UUID PRIMARY KEY,
    seat_layout_id UUID NOT NULL REFERENCES seat_layouts(id),
    label VARCHAR(32) NOT NULL,
    row_number INTEGER NOT NULL,
    column_number INTEGER NOT NULL,
    CONSTRAINT seat_layout_seats_label_unique UNIQUE (seat_layout_id, label),
    CONSTRAINT seat_layout_seats_position_unique UNIQUE (seat_layout_id, row_number, column_number),
    CONSTRAINT seat_layout_seats_row_positive CHECK (row_number > 0),
    CONSTRAINT seat_layout_seats_column_positive CHECK (column_number > 0)
);

CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    seat_layout_id UUID NOT NULL REFERENCES seat_layouts(id),
    status VARCHAR(32) NOT NULL
);

CREATE TABLE trips (
    id UUID PRIMARY KEY,
    route_id UUID NOT NULL REFERENCES routes(id),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    departure_at TIMESTAMP WITH TIME ZONE NOT NULL,
    fare NUMERIC(10, 2) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT trips_vehicle_departure_unique UNIQUE (vehicle_id, departure_at),
    CONSTRAINT trips_fare_non_negative CHECK (fare >= 0),
    CONSTRAINT trips_duration_positive CHECK (duration_minutes > 0)
);

CREATE TABLE trip_seats (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id),
    label VARCHAR(32) NOT NULL,
    row_number INTEGER NOT NULL,
    column_number INTEGER NOT NULL,
    CONSTRAINT trip_seats_label_unique UNIQUE (trip_id, label),
    CONSTRAINT trip_seats_position_unique UNIQUE (trip_id, row_number, column_number),
    CONSTRAINT trip_seats_row_positive CHECK (row_number > 0),
    CONSTRAINT trip_seats_column_positive CHECK (column_number > 0)
);
