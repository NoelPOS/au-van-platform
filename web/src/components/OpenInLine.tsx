import { useQuery } from "@tanstack/react-query";
import QRCode from "qrcode";
import { liffUrl } from "../services/liffConfig";

export function OpenInLine() {
  const url = liffUrl();
  const qr = useQuery({
    queryKey: ["liff-qr", url],
    queryFn: () =>
      QRCode.toDataURL(url ?? "", {
        errorCorrectionLevel: "M",
        margin: 1,
        width: 240,
        color: { dark: "#14172b", light: "#ffffff" },
      }),
    enabled: url !== null,
    staleTime: Infinity,
  });

  if (!url) return null;

  return (
    <>
      <a
        className="inline-flex h-12 items-center rounded-full border border-line bg-card px-6 text-[15px] font-semibold text-ink hover:border-ink/30 lg:hidden"
        href={url}
      >
        Open in LINE
      </a>
      <div className="hidden items-center gap-4 lg:flex">
        <div className="size-[5.5rem] rounded-lg border border-line bg-white p-1.5">
          {qr.data && (
            <img
              alt="QR code that opens AU-Van in LINE"
              className="size-full"
              src={qr.data}
            />
          )}
        </div>
        <p className="max-w-[10rem] text-sm leading-5 text-muted">
          <span className="block font-semibold text-ink">Open in LINE</span>
          Scan with your phone’s camera.
        </p>
      </div>
    </>
  );
}
