import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LandingPage } from "./LandingPage";

vi.mock("../services/liffConfig", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/liffConfig")>()),
  demoLoginEnabled: true,
}));

const demoStudent = {
  accessToken: "demo-token",
  expiresIn: 900,
  user: { id: "demo-id", role: "STUDENT", displayName: "Demo Student" },
};

function stubApi(answer: (url: string) => Response) {
  const fetcher = vi.fn(async (input: RequestInfo | URL) => answer(String(input)));
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

function renderLanding() {
  const onSignedIn = vi.fn();
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <LandingPage onSignedIn={onSignedIn} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return onSignedIn;
}

async function submitPasscode(passcode: string) {
  fireEvent.change(screen.getByLabelText("Demo staff passcode"), {
    target: { value: passcode },
  });
  fireEvent.click(screen.getByRole("button", { name: "Open staff demo" }));
}

describe("landing page", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it("walks a visitor through the product, the screens and the engineering", () => {
    stubApi(() => new Response(null));

    renderLanding();

    expect(
      screen.getByRole("heading", { level: 1, name: /without leaving LINE/ }),
    ).toBeInTheDocument();
    for (const title of [
      "From the departure board to a seat in four steps",
      "Two apps, one set of seats",
      "Watch a booking go through",
      "Built for the rush before the 07:30 van",
      "The other side of the counter",
    ]) {
      expect(screen.getByRole("heading", { level: 2, name: title })).toBeInTheDocument();
    }
    expect(screen.getByText(/Payments are samples; nothing is charged/)).toBeInTheDocument();
  });

  it("signs a visitor into the student app as a demo student", async () => {
    const fetcher = stubApi(() => Response.json(demoStudent));

    const onSignedIn = renderLanding();
    fireEvent.click(screen.getByRole("button", { name: "Try the student app" }));

    await vi.waitFor(() =>
      expect(onSignedIn).toHaveBeenCalledWith({ ...demoStudent, demo: true }),
    );
    expect(fetcher).toHaveBeenCalledWith("/api/v1/auth/demo/student", expect.anything());
  });

  it("says so when the student demo is switched off", async () => {
    stubApi(() => new Response(null, { status: 404 }));

    const onSignedIn = renderLanding();
    fireEvent.click(screen.getByRole("button", { name: "Try the student app" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The demo is not switched on.");
    expect(onSignedIn).not.toHaveBeenCalled();
  });

  it("offers LINE as a link and a QR code only when a LIFF ID is built in", async () => {
    stubApi(() => new Response(null));
    renderLanding();

    expect(screen.queryAllByText("Open in LINE")).toHaveLength(0);
    expect(screen.queryByAltText("QR code that opens AU-Van in LINE")).toBeNull();
    cleanup();

    vi.stubEnv("VITE_LIFF_ID", "1234567890-abcdefgh");
    renderLanding();

    expect(screen.getByRole("link", { name: "Open in LINE" })).toHaveAttribute(
      "href",
      "https://liff.line.me/1234567890-abcdefgh",
    );
    expect(await screen.findByAltText("QR code that opens AU-Van in LINE")).toBeInTheDocument();
  });

  it("tells staff when the demo passcode is wrong and lets them try again", async () => {
    stubApi(() => Response.json({ code: "invalid_passcode" }, { status: 401 }));

    const onSignedIn = renderLanding();
    await submitPasscode("guess");

    expect(await screen.findByRole("alert")).toHaveTextContent("That passcode is not right.");
    expect(screen.getByRole("button", { name: "Open staff demo" })).toBeEnabled();
    expect(onSignedIn).not.toHaveBeenCalled();
  });

  it("drops the passcode form when the staff demo is switched off", async () => {
    stubApi(() => new Response(null, { status: 404 }));

    renderLanding();
    await submitPasscode("open-sesame");

    expect(await screen.findByText("Staff demo is not switched on.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Demo staff passcode")).toBeNull();
  });

  it("signs staff into the demo with the right passcode", async () => {
    const staff = { ...demoStudent, user: { ...demoStudent.user, role: "ADMIN" } };
    stubApi(() => Response.json(staff));

    const onSignedIn = renderLanding();
    await submitPasscode("open-sesame");

    await vi.waitFor(() => expect(onSignedIn).toHaveBeenCalledWith({ ...staff, demo: true }));
  });

  it("frames a screenshot that has not been captured yet instead of a broken image", () => {
    stubApi(() => new Response(null));
    renderLanding();

    fireEvent.error(screen.getByAltText("Trips by route"));

    expect(screen.queryByAltText("Trips by route")).toBeNull();
    expect(screen.getAllByText("Trips by route").length).toBe(2);
  });
});
