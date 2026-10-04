import {
  useId,
  type FocusEvent,
  type InputHTMLAttributes,
  type MouseEvent,
} from "react";
import { controlClass, Field } from "./Field";

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
  error?: string;
};

const selectOnFocus = {
  onFocus: (event: FocusEvent<HTMLInputElement>) =>
    event.currentTarget.select(),
  // WebKit clears a selection made on focus when the click's mouseup lands.
  onMouseUp: (event: MouseEvent) => event.preventDefault(),
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
        {...(props.type === "number" ? selectOnFocus : {})}
        {...props}
      />
    </Field>
  );
}
