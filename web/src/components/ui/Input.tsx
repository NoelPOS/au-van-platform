import { useId, type InputHTMLAttributes } from "react";
import { controlClass, Field } from "./Field";

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
  error?: string;
};

export function Input({ label, hint, error, className, id, ...props }: Props) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <Field
      className={className}
      error={error}
      hint={hint}
      id={inputId}
      label={label}
    >
      <input
        aria-describedby={(error ?? hint) ? `${inputId}-hint` : undefined}
        aria-invalid={error ? true : undefined}
        className={controlClass}
        id={inputId}
        {...props}
      />
    </Field>
  );
}
