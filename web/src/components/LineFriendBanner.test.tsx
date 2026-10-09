import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { isOfficialAccountFriend } from "../services/liffService";
import { LineFriendBanner } from "./LineFriendBanner";

vi.mock("../services/liffService", () => ({ isOfficialAccountFriend: vi.fn() }));

function renderBanner() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <LineFriendBanner />
    </QueryClientProvider>,
  );
}

describe("LineFriendBanner", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllEnvs();
  });

  it("asks a student who has not added the official account to add it", async () => {
    vi.stubEnv("VITE_LINE_OA_ID", "@auvan");
    vi.mocked(isOfficialAccountFriend).mockResolvedValue(false);

    renderBanner();

    expect(
      await screen.findByRole("link", { name: "Add AU-Van on LINE" }),
    ).toHaveAttribute("href", "https://line.me/R/ti/p/%40auvan");
  });

  it("stays out of the way for a friend, or when LINE cannot say", async () => {
    vi.stubEnv("VITE_LINE_OA_ID", "@auvan");
    vi.mocked(isOfficialAccountFriend).mockResolvedValue(null);

    renderBanner();

    await vi.waitFor(() => expect(isOfficialAccountFriend).toHaveBeenCalled());
    expect(screen.queryByText("Turn on LINE updates")).not.toBeInTheDocument();
  });

  it("does not ask LINE at all when no official account is configured", () => {
    vi.stubEnv("VITE_LINE_OA_ID", "");
    vi.mocked(isOfficialAccountFriend).mockClear();

    renderBanner();

    expect(isOfficialAccountFriend).not.toHaveBeenCalled();
  });
});
