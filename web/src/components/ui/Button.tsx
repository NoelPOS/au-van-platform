import type { ButtonHTMLAttributes, ReactNode } from "react";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
  variant?: "primary" | "secondary" | "text";
};

const variants = {
  primary:
    "border-brand bg-brand text-white hover:bg-brand/90 disabled:bg-brand/60",
  secondary: "border-line bg-white text-muted hover:border-muted",
  text: "border-transparent bg-transparent text-brand hover:bg-brand-soft",
};

export function Button({
  children,
  className = "",
  variant = "primary",
  ...props
}: Props) {
  return (
    <button
      className={`rounded-lg border px-3 py-2 text-sm font-semibold transition-colors disabled:cursor-wait ${variants[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
