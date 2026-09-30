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

const proof = {
  id: "proof-1",
  bookingId: "booking-1",
  bookingReference: "AUV-260921-7KQ2M4XR",
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

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

type Routes = {
  queue?: () => Response;
  image?: () => Response;
  decision?: (init: RequestInit) => Response;
};

function stubApi(routes: Routes = {}) {
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

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminPaymentReviewPage session={session} />
    </QueryClientProvider>,
  );
}

describe("AdminPaymentReviewPage", () => {
  beforeEach(() => {
    // jsdom implements neither, and the slip is rendered from an object URL.
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

  it("lists what is waiting and fetches the slip with the administrator's token", async () => {
    const fetcher = stubApi();

    renderPage();
    fireEvent.click(
      await screen.findByRole("button", { name: "AUV-260921-7KQ2M4XR" }),
    );

    expect(await screen.findByAltText(/Payment slip for booking/)).toHaveAttribute(
      "src",
      "blob:the-slip",
    );
    const call = fetcher.mock.calls.find(([url]) => String(url).endsWith("/image"));
    expect(call?.[0]).toBe("/api/v1/admin/payment-proofs/proof-1/image");
    expect(new Headers(call?.[1]?.headers).get("Authorization")).toBe(
      "Bearer admin-token",
    );
    expect(screen.getByText("Somchai P.")).toBeInTheDocument();
    expect(screen.getByText(/AU → Mega Bangna/)).toBeInTheDocument();
  });

  it("revokes the slip's object URL when the screen goes away", async () => {
    stubApi();

    const view = renderPage();
    fireEvent.click(
      await screen.findByRole("button", { name: "AUV-260921-7KQ2M4XR" }),
    );
    await screen.findByAltText(/Payment slip for booking/);
    view.unmount();

    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:the-slip");
  });

  it("approves a slip and drops it from the queue", async () => {
    let decided = 0;
    const fetcher = stubApi({
      queue: () => json(decided === 0 ? [proof] : []),
      decision: () => {
        decided += 1;
        return json({ id: "booking-1", status: "CONFIRMED" });
      },
    });

    renderPage();
    fireEvent.click(
      await screen.findByRole("button", { name: "AUV-260921-7KQ2M4XR" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Approve payment" }));

    expect(await screen.findByText("Nothing to review")).toBeInTheDocument();
    const call = fetcher.mock.calls.find(([url]) =>
      String(url).includes("/approve"),
    );
    expect(call?.[0]).toBe("/api/v1/admin/payment-proofs/proof-1/approve");
    const init = call?.[1] as RequestInit;
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ note: "" }));
  });

  it("refuses to reject without a reason and sends the reason once it has one", async () => {
    const fetcher = stubApi();

    renderPage();
    fireEvent.click(
      await screen.findByRole("button", { name: "AUV-260921-7KQ2M4XR" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Reject payment" }));

    expect(
      await screen.findByText(
        "Say why the slip was rejected, so the student can fix it.",
      ),
    ).toBeInTheDocument();
    expect(
      fetcher.mock.calls.filter(([url]) => String(url).includes("/reject")),
    ).toHaveLength(0);

    fireEvent.change(screen.getByLabelText("Note to the student"), {
      target: { value: "  The slip is too blurred to read.  " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Reject payment" }));

    await vi.waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        "/api/v1/admin/payment-proofs/proof-1/reject",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ note: "The slip is too blurred to read." }),
        }),
      ),
    );
  });

  it("shows why the queue could not be loaded rather than an empty screen", async () => {
    stubApi({ queue: () => json({ detail: "Access denied." }, 403) });

    renderPage();

    expect(
      await screen.findByText("Could not load payment proofs"),
    ).toBeInTheDocument();
    expect(screen.getByText("Access denied.")).toBeInTheDocument();
  });

  it("keeps the slip open and reports a decision the API refused", async () => {
    stubApi({
      decision: () =>
        json(
          {
            detail: "That payment proof has already been reviewed.",
            code: "payment_proof_already_decided",
          },
          409,
        ),
    });

    renderPage();
    fireEvent.click(
      await screen.findByRole("button", { name: "AUV-260921-7KQ2M4XR" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Approve payment" }));

    expect(
      await screen.findByText("That payment proof has already been reviewed."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Approve payment" }),
    ).toBeInTheDocument();
  });
});
