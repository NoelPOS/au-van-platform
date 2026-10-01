import { ZoomIn } from "lucide-react";
import { useEffect, useState } from "react";
import type { AuthSession } from "../types/auth";
import { paymentsApi } from "../services/paymentsApi";
import { SlipLightbox } from "./SlipLightbox";
import { Skeleton } from "./ui/Skeleton";

export function ProofImage({
  session,
  proofId,
  reference,
}: {
  session: AuthSession;
  proofId: string;
  reference: string;
}) {
  const [source, setSource] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enlarged, setEnlarged] = useState(false);

  useEffect(() => {
    let url: string | null = null;
    let cancelled = false;

    paymentsApi
      .loadProofImage(session, proofId)
      .then((blob) => {
        if (cancelled) return;
        url = URL.createObjectURL(blob);
        setSource(url);
      })
      .catch((failure: unknown) =>
        setError(
          failure instanceof Error
            ? failure.message
            : "The payment slip could not be loaded.",
        ),
      );

    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [session, proofId]);

  if (error)
    return (
      <p
        className="rounded-xl border border-danger/20 bg-danger-soft px-4 py-3 text-sm text-danger"
        role="alert"
      >
        {error}
      </p>
    );

  if (!source)
    return (
      <div className="relative grid h-80 place-items-center">
        <Skeleton className="absolute inset-0" />
        <p className="relative text-sm text-muted">Loading the slip…</p>
      </div>
    );

  return (
    <>
      <figure className="relative overflow-hidden rounded-xl border border-line bg-paper">
        <img
          alt={`Payment slip for booking ${reference}`}
          className="max-h-120 w-full object-contain"
          src={source}
        />
        <button
          aria-label="Enlarge the slip"
          className="absolute inset-0 cursor-zoom-in focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-500"
          onClick={() => setEnlarged(true)}
          type="button"
        >
          <span className="absolute right-3 bottom-3 inline-flex items-center gap-1.5 rounded-full bg-brand-900 px-3 py-1.5 text-xs font-semibold text-paper">
            <ZoomIn aria-hidden className="size-3.5" />
            Enlarge
          </span>
        </button>
      </figure>
      {enlarged && (
        <SlipLightbox
          onClose={() => setEnlarged(false)}
          reference={reference}
          source={source}
        />
      )}
    </>
  );
}
