import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { booking } from "../test/bookingFixtures";
import {
  adminSession,
  json,
  renderAdminPage,
  sentTo,
  stubAdminApi,
} from "../test/renderAdminPage";
import { AdminRefundsPage } from "./AdminRefundsPage";

function refundDue(id: string, reference: string, cancelledBy: "TRIP_CANCELLED" | "CANCELLED") {
  return {
    ...booking,
    id,
    reference,
    status: "CANCELLED",
    totalFare: 70,
    refundStatus: "DUE",
    events: [
      ...booking.events,
      { type: cancelledBy, detail: null, actorUserId: "someone", createdAt: "2026-09-22T08:00:00Z" },
    ],
  };
}

const older = refundDue("booking-1", "AUV-260921-OLDER111", "TRIP_CANCELLED");
const newer = refundDue("booking-2", "AUV-260922-NEWER222", "CANCELLED");

function row(reference: string) {
  return within(screen.getByText(reference).closest("li") as HTMLElement);
}

async function openRecord(reference: string) {
  renderAdminPage(<AdminRefundsPage session={adminSession} />);
  await screen.findByText(reference);
  fireEvent.click(row(reference).getByRole("button", { name: "Mark refunded" }));
}

describe("AdminRefundsPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("lists every refund due in the order the API gives, oldest first, with what is owed", async () => {
    stubAdminApi({ "GET /refunds": () => json([older, newer]) });

    renderAdminPage(<AdminRefundsPage session={adminSession} />);

    const list = within(await screen.findByRole("list", { name: "Refunds due" }));
    const references = list.getAllByText(/^AUV-/).map((node) => node.textContent);
    expect(references).toEqual(["AUV-260921-OLDER111", "AUV-260922-NEWER222"]);
    expect(screen.getByText("2 to send · ฿140 in total")).toBeInTheDocument();
    expect(row(older.reference).getByText("฿70")).toBeInTheDocument();
    expect(row(older.reference).getByRole("link", { name: "0812345678" })).toHaveAttribute("href", "tel:0812345678");
    expect(row(older.reference).getByText(/trip cancelled/)).toBeInTheDocument();
    expect(row(newer.reference).getByText(/student cancelled/)).toBeInTheDocument();
  });

  it("records a refund with its note and drops it from the list", async () => {
    let recorded = false;
    const fetcher = stubAdminApi({
      "GET /refunds": () => json(recorded ? [] : [older]),
      "POST /refunds/booking-1/mark-refunded": () => {
        recorded = true;
        return json({ ...older, refundStatus: "REFUNDED" });
      },
    });

    await openRecord(older.reference);
    fireEvent.change(screen.getByLabelText("Note for the record"), { target: { value: " PromptPay ref 4471 " } });
    fireEvent.click(screen.getByRole("button", { name: "Record ฿70 refunded" }));

    expect(await screen.findByText("Refund of ฿70 recorded for AUV-260921-OLDER111")).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /refunds/booking-1/mark-refunded")).toEqual([{ note: "PromptPay ref 4471" }]);
    expect(await screen.findByText("Nothing to refund")).toBeInTheDocument();
  });

  it("records a refund without a note as no note at all", async () => {
    const fetcher = stubAdminApi({
      "GET /refunds": () => json([older]),
      "POST /refunds/booking-1/mark-refunded": () => json({ ...older, refundStatus: "REFUNDED" }),
    });

    await openRecord(older.reference);
    expect(screen.getByLabelText("Note for the record")).toHaveAttribute("maxLength", "500");
    fireEvent.click(screen.getByRole("button", { name: "Record ฿70 refunded" }));

    await vi.waitFor(() =>
      expect(sentTo(fetcher, "POST /refunds/booking-1/mark-refunded")).toEqual([{ note: null }]),
    );
  });

  it("records nothing when the administrator says not yet", async () => {
    const fetcher = stubAdminApi({ "GET /refunds": () => json([older]) });

    await openRecord(older.reference);
    fireEvent.click(screen.getByRole("button", { name: "Not yet" }));

    expect(row(older.reference).getByRole("button", { name: "Mark refunded" })).toBeInTheDocument();
    expect(sentTo(fetcher, "POST /refunds/booking-1/mark-refunded")).toEqual([]);
  });

  it.each([
    ["refund_already_recorded", "That refund has already been recorded."],
    ["refund_not_due", "No refund is due on that booking."],
  ])("says so and refreshes the list when the API answers %s", async (code, detail) => {
    let refused = false;
    stubAdminApi({
      "GET /refunds": () => json(refused ? [] : [older]),
      "POST /refunds/booking-1/mark-refunded": () => {
        refused = true;
        return json({ code, detail }, 409);
      },
    });

    await openRecord(older.reference);
    fireEvent.click(screen.getByRole("button", { name: "Record ฿70 refunded" }));

    expect(await screen.findByText("Nothing to refund")).toBeInTheDocument();
    expect(screen.getByText(detail)).toBeInTheDocument();
  });

  it("keeps the row open with the reason when recording fails for another reason", async () => {
    stubAdminApi({
      "GET /refunds": () => json([older]),
      "POST /refunds/booking-1/mark-refunded": () => json({ detail: "Service unavailable." }, 503),
    });

    await openRecord(older.reference);
    fireEvent.click(screen.getByRole("button", { name: "Record ฿70 refunded" }));

    expect(await row(older.reference).findByRole("alert")).toHaveTextContent("Service unavailable.");
    expect(screen.getByRole("button", { name: "Record ฿70 refunded" })).toBeEnabled();
  });

  it("offers a retry when the list cannot load", async () => {
    let refuse = true;
    stubAdminApi({
      "GET /refunds": () => (refuse ? json({ detail: "Access denied." }, 403) : json([])),
    });

    renderAdminPage(<AdminRefundsPage session={adminSession} />);

    expect(await screen.findByText("Could not load refunds")).toBeInTheDocument();
    refuse = false;
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("Nothing to refund")).toBeInTheDocument();
  });
});
