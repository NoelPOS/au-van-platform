import { useId, type InputHTMLAttributes } from "react";
import { controlClass, Field } from "./Field";

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
};

export function Input({ label, hint, className, id, ...props }: Props) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <Field className={className} hint={hint} id={inputId} label={label}>
      <input
        aria-describedby={hint ? `${inputId}-hint` : undefined}
        className={controlClass}
        id={inputId}
        {...props}
      />
    </Field>
  );
}
