import { useQuery } from "@tanstack/react-query";
import QRCode from "qrcode";
import { Skeleton } from "./ui/Skeleton";
import { promptPayAccount } from "../services/paymentConfig";
import { formatBaht } from "../utils/format";
import { promptPayPayload } from "../utils/promptPay";

const steps = [
  "Scan it with any Thai banking app. The amount fills itself in.",
  "Paying on this phone? Save the QR, then pick it from your bank app’s scan screen.",
  "Upload the slip below.",
];

export function HowToPay({ amount, reference }: { amount: number; reference: string }) {
  const account = promptPayAccount();
  const payload = promptPayPayload(account.id, amount);
  const qr = useQuery({
    queryKey: ["promptpay", payload],
    queryFn: () =>
      QRCode.toDataURL(payload, {
        errorCorrectionLevel: "M",
        margin: 1,
        width: 480,
        color: { dark: "#14172b", light: "#ffffff" },
      }),
    staleTime: Infinity,
  });

  return (
    <section aria-label="How to pay" className="rounded-2xl border border-line bg-card p-5">
      <div className="flex items-baseline justify-between">
        <h2 className="font-mono text-[11px] tracking-[0.18em] text-muted uppercase">How to pay</h2>
        <span className="text-sm font-semibold text-brand-900">PromptPay</span>
      </div>
      <div className="mt-4 flex flex-col items-center text-center">
        <div className="rounded-xl border border-line bg-white p-2.5">
          {qr.data ? (
            <img alt={`PromptPay QR for ${formatBaht(amount)}`} className="size-48" src={qr.data} />
          ) : (
            <Skeleton className="size-48" />
          )}
        </div>
        <p className="mt-3 font-display text-[34px] leading-none text-brand-900">{formatBaht(amount)}</p>
        <p className="mt-1 text-sm text-muted">{`to ${account.name ?? "AU-Van"}`}</p>
        {qr.data && (
          <a
            className="mt-4 inline-flex min-h-11 items-center rounded-full border border-line bg-card px-5 text-sm font-semibold text-ink hover:border-ink/30"
            download={`au-van-${reference}.png`}
            href={qr.data}
          >
            Save QR to photos
          </a>
        )}
      </div>
      <ol className="mt-5 flex flex-col gap-2.5">
        {steps.map((step, index) => (
          <li className="flex gap-3 text-sm text-ink" key={step}>
            <span className="font-mono text-muted">{index + 1}</span>
            {step}
          </li>
        ))}
      </ol>
      {account.sample && (
        <p className="mt-4 rounded-xl border border-accent/50 bg-warning-soft px-3 py-2.5 text-[13px] text-warning">
          Sample QR for the demo. It pays no one, so don’t transfer real money.
        </p>
      )}
    </section>
  );
}
