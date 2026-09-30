export type RouteStatus = "ACTIVE" | "INACTIVE";
export type VehicleStatus = "ACTIVE" | "INACTIVE";
export type TripStatus = "ACTIVE" | "CANCELLED";

export type VanRoute = {
  id: string;
  origin: string;
  destination: string;
  fare: number;
  durationMinutes: number;
  status: RouteStatus;
};

export type Seat = {
  label: string;
  rowNumber: number;
  columnNumber: number;
};

export type SeatLayout = {
  id: string;
  name: string;
  seats: Seat[];
};

export type Vehicle = {
  id: string;
  code: string;
  name: string;
  seatLayoutId: string;
  status: VehicleStatus;
};

export type Trip = {
  id: string;
  routeId: string;
  vehicleId: string;
  departureAt: string;
  fare: number;
  durationMinutes: number;
  status: TripStatus;
  seats: Seat[];
};
