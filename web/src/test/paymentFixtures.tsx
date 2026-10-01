import { fireEvent, screen } from "@testing-library/react";
import { vi } from "vitest";
import { AdminPaymentReviewPage } from "../pages/AdminPaymentReviewPage";
import { adminSession, json, renderAdminPage } from "./renderAdminPage";

export function slip(id: string, bookingReference: string) {
  return {
    id,
    bookingId: `booking-${id}`,
    bookingReference,
    passengerName: "Somchai P.",
    passengerPhone: "0812345678",
    totalFare: 35,
    trip: {
      id: "trip-1",
      origin: "AU",
      destination: "Mega Bangna",
      departureAt: "2026-10-01T01:00:00Z",
    },
    submittedByUserId: "student-id",
    contentType: "image/jpeg",
    sizeBytes: 12,
    status: "SUBMITTED",
    submittedAt: "2026-09-21T10:01:12Z",
  };
}

export const proof = slip("proof-1", "AUV-260921-7KQ2M4XR");

type Routes = {
  queue?: () => Response;
  image?: () => Response;
  decision?: (init: RequestInit) => Response;
};

export function stubApi(routes: Routes = {}) {
  const fetcher = vi.fn(
    async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/image"))
        return (
          routes.image?.() ??
          new Response("slip-bytes", {
            headers: { "Content-Type": "image/jpeg" },
          })
        );
      if (url.includes("/approve") || url.includes("/reject"))
        return routes.decision?.(init ?? {}) ?? json({ id: "booking-1" });
      if (url.endsWith("/payment-proofs")) return routes.queue?.() ?? json([proof]);
      throw new Error(`unexpected request: ${url}`);
    },
  );
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

// jsdom implements neither, and the slip is rendered from an object URL.
export function stubObjectUrls() {
  vi.stubGlobal("URL", {
    ...URL,
    createObjectURL: vi.fn(() => "blob:the-slip"),
    revokeObjectURL: vi.fn(),
  });
}

export function renderPage() {
  return renderAdminPage(<AdminPaymentReviewPage session={adminSession} />);
}

export async function openSlip(reference: string) {
  fireEvent.click(
    await screen.findByRole("button", { name: new RegExp(reference) }),
  );
}
