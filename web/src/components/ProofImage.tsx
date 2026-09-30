import { useEffect, useState } from "react";
import type { AuthSession } from "../services/authService";
import { paymentsApi } from "../services/paymentsApi";

/**
 * The payment slip itself. The image is fetched with the administrator's token
 * and rendered from an object URL, because the browser sends no `Authorization`
 * header for an `<img src>` and the endpoint would answer 401.
 *
 * <p>The object URL is revoked when the component goes away; without that
 * every slip looked at leaks one for the life of the tab. The caller keys this
 * component on the proof, so a different slip is a fresh mount and the effect
 * never has to reset state it did not set.
 */
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
      <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
        {error}
      </p>
    );

  if (!source) return <p className="text-sm text-muted">Loading the slip…</p>;

  return (
    <img
      alt={`Payment slip for booking ${reference}`}
      className="max-h-120 w-full rounded-lg border border-line object-contain"
      src={source}
    />
  );
}
