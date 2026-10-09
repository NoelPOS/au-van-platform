import type { AuthSession } from "../types/auth";
import type { Booking } from "../types/booking";
import { adminRequest } from "./inventoryApi";

export const refundsApi = {
  listDue: (session: AuthSession) => adminRequest<Booking[]>(session, "/refunds"),
  markRefunded: (session: AuthSession, bookingId: string, note: string) =>
    adminRequest<Booking>(session, `/refunds/${bookingId}/mark-refunded`, {
      method: "POST",
      body: JSON.stringify({ note: note || null }),
    }),
};
