import { ChevronDown } from "lucide-react";
import { useId, type SelectHTMLAttributes } from "react";
import { controlClass, Field } from "./Field";

type Props = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  hint?: string;
};

export function Select({ label, hint, className, id, children, ...props }: Props) {
  const generated = useId();
  const selectId = id ?? generated;
  return (
    <Field className={className} hint={hint} id={selectId} label={label}>
      <div className="relative">
        <select
          aria-describedby={hint ? `${selectId}-hint` : undefined}
          className={`${controlClass} appearance-none pr-10`}
          id={selectId}
          {...props}
        >
          {children}
        </select>
        <ChevronDown
          aria-hidden
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted"
        />
      </div>
    </Field>
  );
}
