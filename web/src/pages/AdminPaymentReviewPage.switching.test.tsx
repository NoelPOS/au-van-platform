import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthSession } from "../types/auth";
import { AdminPaymentReviewPage } from "./AdminPaymentReviewPage";

const session: AuthSession = {
  accessToken: "admin-token",
  expiresIn: 900,
  user: { id: "admin-id", role: "ADMIN", displayName: "Noel" },
};

function slip(id: string, bookingReference: string) {
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

const slipA = slip("proof-a", "AUV-260921-AAAAAAAA");
const slipB = slip("proof-b", "AUV-260921-BBBBBBBB");

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const image = () =>
  Promise.resolve(new Response("slip-bytes", { headers: { "Content-Type": "image/jpeg" } }));
const missing = (detail: string) => () => Promise.resolve(json({ detail }, 404));
const loading = () => new Promise<Response>(() => {});

function stubApi(images: Record<string, () => Promise<Response>>) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      const id = /payment-proofs\/([^/]+)\/image$/.exec(url)?.[1];
      if (id) return images[id]();
      if (url.endsWith("/payment-proofs")) return json([slipA, slipB]);
      throw new Error(`unexpected request: ${url}`);
    }),
  );
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <AdminPaymentReviewPage session={session} />
    </QueryClientProvider>,
  );
}

async function open(reference: string) {
  fireEvent.click(await screen.findByRole("button", { name: reference }));
}

describe("AdminPaymentReviewPage switching slips", () => {
  beforeEach(() => {
    vi.stubGlobal("URL", {
      ...URL,
      createObjectURL: vi.fn(() => "blob:the-slip"),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("does not show slip A's image while slip B is still loading", async () => {
    stubApi({ "proof-a": image, "proof-b": loading });

    renderPage();
    await open(slipA.bookingReference);
    await screen.findByAltText(`Payment slip for booking ${slipA.bookingReference}`);
    await open(slipB.bookingReference);

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("Loading the slip…")).toBeInTheDocument();
  });

  it("does not carry slip A's error over to slip B, and shows slip B's own error", async () => {
    let failB = () => {};
    stubApi({
      "proof-a": missing("Slip A could not be found."),
      "proof-b": () =>
        new Promise<Response>((resolve) => {
          failB = () => resolve(json({ detail: "Slip B could not be found." }, 404));
        }),
    });

    renderPage();
    await open(slipA.bookingReference);
    await screen.findByText("Slip A could not be found.");
    await open(slipB.bookingReference);

    expect(screen.queryByText("Slip A could not be found.")).not.toBeInTheDocument();
    expect(screen.getByText("Loading the slip…")).toBeInTheDocument();

    failB();

    expect(await screen.findByText("Slip B could not be found.")).toBeInTheDocument();
    expect(screen.queryByText("Slip A could not be found.")).not.toBeInTheDocument();
  });
});
