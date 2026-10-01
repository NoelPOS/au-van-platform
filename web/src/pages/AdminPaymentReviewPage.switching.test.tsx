import { cleanup, fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  json,
  openSlip,
  renderPage,
  slip,
  stubObjectUrls,
} from "../test/paymentFixtures";

const slipA = slip("proof-a", "AUV-260921-AAAAAAAA");
const slipB = slip("proof-b", "AUV-260921-BBBBBBBB");

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

describe("AdminPaymentReviewPage switching slips", () => {
  beforeEach(stubObjectUrls);

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("does not show slip A's image while slip B is still loading", async () => {
    stubApi({ "proof-a": image, "proof-b": loading });

    renderPage();
    await openSlip(slipA.bookingReference);
    await screen.findByAltText(`Payment slip for booking ${slipA.bookingReference}`);
    await openSlip(slipB.bookingReference);

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
    await openSlip(slipA.bookingReference);
    await screen.findByText("Slip A could not be found.");
    await openSlip(slipB.bookingReference);

    expect(screen.queryByText("Slip A could not be found.")).not.toBeInTheDocument();
    expect(screen.getByText("Loading the slip…")).toBeInTheDocument();

    failB();

    expect(await screen.findByText("Slip B could not be found.")).toBeInTheDocument();
    expect(screen.queryByText("Slip A could not be found.")).not.toBeInTheDocument();
  });

  it("starts the next slip with an empty note rather than the last one's", async () => {
    stubApi({ "proof-a": image, "proof-b": image });

    renderPage();
    await openSlip(slipA.bookingReference);
    fireEvent.change(screen.getByLabelText("Note to the student"), {
      target: { value: "Meant for slip A only." },
    });
    await openSlip(slipB.bookingReference);

    expect(screen.getByLabelText("Note to the student")).toHaveValue("");
  });
});
