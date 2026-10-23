export type InsightTotals = {
  revenue: number;
  refundsDue: number;
  refunded: number;
  awaitingPayment: number;
  bookings: number;
  seatsSold: number;
  seatsOffered: number;
  occupancy: number;
  trips: number;
  cancelledTrips: number;
};

export type RouteInsight = {
  routeId: string;
  origin: string;
  destination: string;
  trips: number;
  seatsSold: number;
  seatsOffered: number;
  occupancy: number;
  revenue: number;
};

export type DayInsight = {
  date: string;
  revenue: number;
  seatsSold: number;
};

export type Insights = {
  totals: InsightTotals;
  byRoute: RouteInsight[];
  byDay: DayInsight[];
};

export type InsightPeriod = { from: string; to: string };
