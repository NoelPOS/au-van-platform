import { ArrowRight } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router";

export function TextLink({
  to,
  children,
}: {
  to: string;
  children: ReactNode;
}) {
  return (
    <Link
      className="group inline-flex min-h-11 items-center gap-1.5 text-sm font-semibold text-brand-500 underline decoration-1 underline-offset-4 hover:text-brand-700 hover:decoration-2"
      to={to}
    >
      {children}
      <ArrowRight
        aria-hidden
        className="size-4 transition-transform duration-150 ease-out group-hover:translate-x-0.5"
      />
    </Link>
  );
}
