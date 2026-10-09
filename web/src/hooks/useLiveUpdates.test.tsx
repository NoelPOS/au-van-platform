import { QueryClient, QueryClientProvider, type QueryKey } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { session } from "../test/bookingFixtures";
import { useLiveUpdates } from "./useLiveUpdates";

class FakeEventSource {
  static opened: FakeEventSource[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  url: string;

  constructor(url: string) {
    this.url = url;
    FakeEventSource.opened.push(this);
  }

  close() {
    this.closed = true;
  }

  signal(kind: string, id: string) {
    this.onmessage?.(new MessageEvent("message", { data: JSON.stringify({ kind, id }) }));
  }
}

const latest = () => FakeEventSource.opened[FakeEventSource.opened.length - 1];
const live = () => FakeEventSource.opened.filter((source) => !source.closed);

let tickets = 0;
let ticketStatus = 200;
const fetcher = vi.fn(async (_input: string, _init: RequestInit) => {
  tickets += 1;
  return new Response(JSON.stringify({ ticket: `ticket-${tickets}` }), {
    status: ticketStatus,
    headers: { "Content-Type": "application/json" },
  });
});

const queryClient = new QueryClient();
const known: QueryKey[] = [
  ["booking", "bookings"],
  ["booking", "seat-map", "trip-1"],
  ["booking", "seat-map", "trip-2"],
  ["booking", "trips"],
  ["payments", "proofs"],
  ["overview", "payment-proofs"],
];

function invalidated(): string[] {
  return known
    .filter((key) => queryClient.getQueryState(key)?.isInvalidated)
    .map((key) => key.join("/"));
}

async function mount() {
  const view = renderHook(() => useLiveUpdates(session), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
  await flush();
  return view;
}

async function flush(milliseconds = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds);
  });
}

describe("useLiveUpdates", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", fetcher);
    vi.stubGlobal("EventSource", FakeEventSource);
    FakeEventSource.opened = [];
    tickets = 0;
    ticketStatus = 200;
    fetcher.mockClear();
    queryClient.clear();
    known.forEach((key) => queryClient.setQueryData(key, []));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("opens one stream with a ticket fetched using the session's token", async () => {
    await mount();

    const [url, init] = fetcher.mock.calls[0] ?? [];
    expect(url).toBe("/api/v1/events/ticket");
    expect(init?.method).toBe("POST");
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer student-token");
    expect(FakeEventSource.opened).toHaveLength(1);
    expect(latest()?.url).toBe("/api/v1/events?ticket=ticket-1");
  });

  it("refreshes everything about the student's bookings and the admin queues on a booking signal", async () => {
    await mount();

    latest()?.signal("booking", "booking-1");

    expect(invalidated()).toEqual(known.map((key) => key.join("/")));
  });

  it("refreshes the trip list and only that trip's seat map on a trip signal", async () => {
    await mount();

    latest()?.signal("trip", "trip-1");

    expect(invalidated()).toEqual([
      "booking/seat-map/trip-1",
      "booking/trips",
      "overview/payment-proofs",
    ]);
  });

  it("reconnects with a fresh ticket after a growing pause when the stream drops", async () => {
    await mount();

    latest()?.onerror?.();
    expect(live()).toHaveLength(0);
    await flush(999);
    expect(FakeEventSource.opened).toHaveLength(1);
    await flush(1);
    expect(latest()?.url).toBe("/api/v1/events?ticket=ticket-2");

    latest()?.onerror?.();
    await flush(1_999);
    expect(FakeEventSource.opened).toHaveLength(2);
    await flush(1);
    expect(FakeEventSource.opened).toHaveLength(3);
    expect(live()).toHaveLength(1);
  });

  it("starts the pause over once a stream opens again", async () => {
    await mount();
    latest()?.onerror?.();
    await flush(1_000);
    latest()?.onopen?.();

    latest()?.onerror?.();
    await flush(1_000);

    expect(FakeEventSource.opened).toHaveLength(3);
  });

  it("catches up on a reconnect, but not on the first connection", async () => {
    await mount();
    latest()?.onopen?.();
    expect(invalidated()).toEqual([]);

    latest()?.onerror?.();
    await flush(1_000);
    latest()?.onopen?.();

    expect(invalidated()).toContain("booking/bookings");
  });

  it("closes the stream when the layout unmounts and never reconnects", async () => {
    const view = await mount();

    view.unmount();
    expect(live()).toHaveLength(0);
    await flush(60_000);

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(FakeEventSource.opened).toHaveLength(1);
  });

  it("does not retry a ticket request that fails after the layout unmounts", async () => {
    let answer: (response: Response) => void = () => {};
    fetcher.mockImplementationOnce(
      () => new Promise<Response>((resolve) => (answer = resolve)),
    );
    const view = await mount();

    view.unmount();
    answer(new Response(null, { status: 503 }));
    await flush(60_000);

    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("opens no stream for a ticket that arrives after the layout unmounts", async () => {
    let answer: (response: Response) => void = () => {};
    fetcher.mockImplementationOnce(
      () => new Promise<Response>((resolve) => (answer = resolve)),
    );
    const view = await mount();

    view.unmount();
    answer(new Response(JSON.stringify({ ticket: "late" }), { status: 200 }));
    await flush(0);

    expect(FakeEventSource.opened).toHaveLength(0);
  });

  it("stops trying once the session is no longer accepted, leaving polling in charge", async () => {
    ticketStatus = 401;

    await mount();
    await flush(60_000);

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(FakeEventSource.opened).toHaveLength(0);
  });

  it("keeps retrying while the API is unreachable", async () => {
    ticketStatus = 503;

    await mount();
    await flush(1_000 + 2_000);

    expect(fetcher).toHaveBeenCalledTimes(3);
  });
});
