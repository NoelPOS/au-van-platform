const letterA =
  "M151 126H267Q277 126 277 135Q277 144 265 148Q257 152 259 162L317 348Q325 368 341 372Q347 374 347 380Q347 386 339 386H233Q225 386 225 379Q225 372 235 370Q247 367 243 353L227 302H141L127 346Q121 366 135 371Q143 374 143 380Q143 386 135 386H77Q69 386 69 380Q69 373 77 371Q91 367 99 346L159 160Q161 150 151 147Q141 144 141 135Q141 126 151 126ZM188 166L221 279H149Z";

export function Logo({
  onDark = false,
  tagline,
}: {
  onDark?: boolean;
  tagline?: string;
}) {
  const tile = onDark ? "#f6f3ec" : "#1b2566";
  const letter = onDark ? "#1b2566" : "#f6f3ec";

  return (
    <span className="flex items-center gap-2.5">
      <svg
        aria-hidden
        className={`shrink-0 ${tagline ? "size-[1.5em]" : "size-[1.25em]"}`}
        focusable="false"
        viewBox="0 0 512 512"
      >
        <rect fill={tile} height="512" rx="112" width="512" />
        <path d={letterA} fill={letter} fillRule="evenodd" />
        <circle cx="410" cy="298" fill="#d99a2b" r="34" />
      </svg>
      <span className="flex flex-col leading-none">
        <span>
          AU<span className="text-accent">·</span>Van
        </span>
        {tagline && (
          <span className="mt-1.5 font-sans text-[10px] font-semibold tracking-[0.2em] text-brand-100/80 uppercase">
            {tagline}
          </span>
        )}
      </span>
    </span>
  );
}
