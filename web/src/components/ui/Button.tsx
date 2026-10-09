import type { ButtonHTMLAttributes, ReactNode } from "react";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
  variant?: "primary" | "secondary" | "danger" | "text";
};

const variants = {
  primary:
    "rounded-full bg-brand-600 px-5 text-white hover:bg-brand-700 active:bg-brand-900",
  secondary:
    "rounded-full border border-line bg-card px-5 text-ink hover:border-ink/30",
  danger:
    "rounded-full bg-danger px-5 text-white hover:bg-danger/90 active:bg-danger",
  text: "px-1 text-brand-500 underline decoration-1 underline-offset-4 hover:text-brand-700 hover:decoration-2",
};

export function Button({
  children,
  className = "",
  variant = "primary",
  ...props
}: Props) {
  return (
    <button
      className={`inline-flex min-h-11 items-center justify-center gap-2 text-sm font-semibold transition-colors duration-150 ease-out disabled:cursor-wait disabled:opacity-60 ${variants[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
