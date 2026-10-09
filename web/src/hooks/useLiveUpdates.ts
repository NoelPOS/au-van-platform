import { useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useEffect } from "react";
import { bookingApi } from "../services/bookingApi";
import type { AuthSession } from "../types/auth";
import { isUnauthorized } from "../utils/errors";

type LiveSignal = { kind: string; id: string };

const allLiveKeys: QueryKey[] = [
  ["booking"],
  ["payments"],
  ["overview"],
  ["operations"],
];

function keysFor(signal: LiveSignal): QueryKey[] {
  if (signal.kind === "booking") return allLiveKeys;
  if (signal.kind === "trip")
    return [
      ["booking", "trips"],
      ["booking", "seat-map", signal.id],
      ["overview"],
      ["operations"],
    ];
  return [];
}

const firstRetryMs = 1_000;
const maxRetryMs = 30_000;

export function useLiveUpdates(session: AuthSession) {
  const queryClient = useQueryClient();

  useEffect(() => {
    let source: EventSource | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let failures = 0;
    let connectedBefore = false;
    let stopped = false;

    const invalidate = (keys: QueryKey[]) =>
      keys.forEach((queryKey) => void queryClient.invalidateQueries({ queryKey }));

    const reconnect = () => {
      source?.close();
      source = null;
      if (stopped) return;
      retry = setTimeout(connect, Math.min(firstRetryMs * 2 ** failures, maxRetryMs));
      failures += 1;
    };

    async function connect() {
      try {
        const { ticket } = await bookingApi.openLiveTicket(session);
        if (stopped) return;
        source = new EventSource(bookingApi.liveStreamUrl(ticket));
        source.onopen = () => {
          failures = 0;
          // Anything that changed while the stream was down sent no signal.
          if (connectedBefore) invalidate(allLiveKeys);
          connectedBefore = true;
        };
        source.onmessage = (event: MessageEvent<string>) =>
          invalidate(keysFor(JSON.parse(event.data) as LiveSignal));
        source.onerror = reconnect;
      } catch (error) {
        if (!isUnauthorized(error)) reconnect();
      }
    }

    void connect();
    return () => {
      stopped = true;
      clearTimeout(retry);
      source?.close();
    };
  }, [queryClient, session]);
}
