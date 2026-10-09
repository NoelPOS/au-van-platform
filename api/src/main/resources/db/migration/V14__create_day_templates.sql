-- A named, reusable plan for one service day: departure times, each with a route and a van.
-- Applying a template copies its lines into trips; trips keep no link back (ADR-019).
CREATE TABLE day_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    CONSTRAINT day_templates_name_unique UNIQUE (name)
);

CREATE TABLE day_template_departures (
    id UUID PRIMARY KEY,
    day_template_id UUID NOT NULL REFERENCES day_templates(id),
    departure_time TIME NOT NULL,
    route_id UUID NOT NULL REFERENCES routes(id),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id)
);

CREATE INDEX day_template_departures_template_idx ON day_template_departures (day_template_id);

-- The calendar and day clearing read trips by departure range; V2 indexed only (vehicle_id, departure_at).
CREATE INDEX trips_departure_at_idx ON trips (departure_at);
