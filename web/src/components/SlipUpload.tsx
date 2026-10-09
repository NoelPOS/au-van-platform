import { ImageUp } from "lucide-react";
import { useEffect, useId, useState, type ChangeEvent } from "react";
import { Button } from "./ui/Button";

// Mirrors the API's own allowlist and ceiling; its 400 is the backstop.
const acceptedTypes = ["image/jpeg", "image/png", "image/webp"];
const maxBytes = 5 * 1024 * 1024;

function problemWith(file: File): string | null {
  if (!acceptedTypes.includes(file.type))
    return "A payment proof must be a JPEG, PNG, or WebP image.";
  if (file.size > maxBytes) return "A payment proof must be 5MB or smaller.";
  return null;
}

export function SlipUpload({
  sending,
  error,
  onChoose,
  onSend,
}: {
  sending: boolean;
  error: string | null;
  onChoose: () => void;
  onSend: (file: File) => void;
}) {
  const inputId = useId();
  const [chosen, setChosen] = useState<{ file: File; url: string } | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const shown = problem ?? error;

  useEffect(() => {
    if (chosen) return () => URL.revokeObjectURL(chosen.url);
  }, [chosen]);

  function choose(event: ChangeEvent<HTMLInputElement>) {
    const next = event.currentTarget.files?.[0];
    event.currentTarget.value = "";
    if (!next) return;
    onChoose();
    const reason = problemWith(next);
    setProblem(reason);
    setChosen(reason ? null : { file: next, url: URL.createObjectURL(next) });
  }

  return (
    <div className="flex flex-col gap-3">
      <input
        accept={acceptedTypes.join(",")}
        aria-label="Payment slip image"
        className="sr-only"
        id={inputId}
        onChange={choose}
        type="file"
      />
      {chosen ? (
        <div className="overflow-hidden rounded-2xl border border-line bg-card">
          <img
            alt="Your payment slip"
            className="max-h-80 w-full bg-paper object-contain"
            src={chosen.url}
          />
          <div className="flex flex-wrap items-center gap-2 border-t border-line p-3">
            <p className="min-w-0 flex-1 truncate text-sm text-muted">{chosen.file.name}</p>
            <label
              className="inline-flex min-h-11 cursor-pointer items-center rounded-full border border-line bg-card px-4 text-sm font-semibold text-ink hover:border-ink/30"
              htmlFor={inputId}
            >
              Choose another
            </label>
            <Button disabled={sending} onClick={() => onSend(chosen.file)} type="button">
              {sending ? "Sending…" : "Send slip"}
            </Button>
          </div>
        </div>
      ) : (
        <label
          className="flex cursor-pointer flex-col items-center gap-2 rounded-2xl border-2 border-dashed border-brand-500/35 bg-brand-50/60 px-5 py-8 text-center transition-colors duration-150 ease-out hover:border-brand-500 hover:bg-brand-50"
          htmlFor={inputId}
        >
          <ImageUp aria-hidden className="size-7 text-brand-600" strokeWidth={1.5} />
          <span className="font-semibold text-ink">Upload your payment slip</span>
          <span className="max-w-64 text-sm text-muted">
            A screenshot or photo of your transfer slip. JPG, PNG or WebP, up to 5 MB.
          </span>
        </label>
      )}
      <p className={shown ? "text-sm text-danger" : "sr-only"} role="alert">
        {shown ?? ""}
      </p>
    </div>
  );
}
