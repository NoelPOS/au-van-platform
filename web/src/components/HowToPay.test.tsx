import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import QRCode from "qrcode";
import { afterEach, describe, expect, it, vi } from "vitest";
import { HowToPay } from "./HowToPay";

function renderCard(amount = 70) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <HowToPay amount={amount} reference="AUV-1" />
    </QueryClientProvider>,
  );
}

describe("HowToPay", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllEnvs();
    vi.mocked(QRCode.toDataURL).mockClear();
  });

  it("encodes the exact fare for the configured PromptPay number", async () => {
    vi.stubEnv("VITE_PROMPTPAY_ID", "081-234-5678");
    vi.stubEnv("VITE_PROMPTPAY_NAME", "AU Van Co.");

    renderCard(140);

    expect(await screen.findByRole("img", { name: "PromptPay QR for ฿140" })).toBeInTheDocument();
    const payload = String(vi.mocked(QRCode.toDataURL).mock.calls[0][0]);
    expect(payload).toContain("01130066812345678");
    expect(payload).toContain("5406140.00");
    expect(screen.getByText("to AU Van Co.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Save QR to photos" })).toHaveAttribute(
      "download",
      "au-van-AUV-1.png",
    );
    expect(screen.queryByText(/Sample QR/)).not.toBeInTheDocument();
  });

  it("marks the sample QR so nobody pays with it", async () => {
    vi.stubEnv("VITE_PROMPTPAY_ID", "");

    renderCard();

    await screen.findByRole("img", { name: "PromptPay QR for ฿70" });
    expect(String(vi.mocked(QRCode.toDataURL).mock.calls[0][0])).toContain("0066000000000");
    expect(screen.getByText(/Sample QR for the demo/)).toBeInTheDocument();
  });
});
