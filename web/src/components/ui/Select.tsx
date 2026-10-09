import { Check, ChevronDown } from "lucide-react";
import {
  useEffect,
  useId,
  useRef,
  useState,
  type KeyboardEvent,
} from "react";
import { useDismiss } from "../../hooks/useDismiss";
import { controlClass, Field } from "./Field";

export type SelectOption = { value: string; label: string; detail?: string };

export function Select({
  label,
  hideLabel,
  hint,
  name,
  options,
  value,
  onChange,
  className,
}: {
  label: string;
  hideLabel?: boolean;
  hint?: string;
  name?: string;
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  className?: string;
}) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const list = useRef<HTMLUListElement>(null);
  const typed = useRef({ text: "", at: 0 });
  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option.value === value),
  );
  const selected = options[selectedIndex];

  useDismiss(root, open, () => setOpen(false));

  useEffect(() => {
    if (!open) return;
    list.current?.focus();
    list.current?.children[active]?.scrollIntoView({ block: "nearest" });
  }, [open, active]);

  function show(index: number) {
    setActive(index);
    setOpen(true);
  }

  function close() {
    setOpen(false);
    trigger.current?.focus();
  }

  function choose(index: number) {
    onChange(options[index].value);
    close();
  }

  function typeahead(event: KeyboardEvent, from: number) {
    const { key, timeStamp } = event;
    const continuing = timeStamp - typed.current.at < 600;
    const text = (continuing ? typed.current.text : "") + key;
    typed.current = { text, at: timeStamp };
    const start = text.length === 1 ? from + 1 : from;
    for (let step = 0; step < options.length; step += 1) {
      const index = (start + step) % options.length;
      if (options[index].label.toLowerCase().startsWith(text.toLowerCase()))
        return index;
    }
    return from;
  }

  function onTriggerKeyDown(event: KeyboardEvent) {
    const opening: Record<string, number> = {
      ArrowDown: selectedIndex,
      ArrowUp: selectedIndex,
      Home: 0,
      End: options.length - 1,
    };
    if (event.key in opening) {
      event.preventDefault();
      show(opening[event.key]);
    } else if (event.key.length === 1 && event.key !== " ") {
      show(typeahead(event, selectedIndex));
    }
  }

  function onListKeyDown(event: KeyboardEvent) {
    const moves: Record<string, number> = {
      ArrowDown: Math.min(active + 1, options.length - 1),
      ArrowUp: Math.max(active - 1, 0),
      Home: 0,
      End: options.length - 1,
    };
    if (event.key in moves) setActive(moves[event.key]);
    else if (event.key === "Enter" || event.key === " ") choose(active);
    // Without preventDefault the Escape would also close the drawer's modal dialog.
    else if (event.key === "Escape") close();
    else if (event.key === "Tab") return setOpen(false);
    else if (event.key.length === 1) setActive(typeahead(event, active));
    else return;
    event.preventDefault();
  }

  return (
    <Field
      className={className}
      hideLabel={hideLabel}
      hint={hint}
      id={id}
      label={label}
    >
      <div className="relative" ref={root}>
        <input name={name} type="hidden" value={value} />
        <button
          aria-describedby={hint ? `${id}-hint` : undefined}
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-labelledby={`${id}-label ${id}-value`}
          className={`${controlClass} flex items-center gap-3 pr-10 text-left`}
          id={id}
          onClick={() => (open ? setOpen(false) : show(selectedIndex))}
          onKeyDown={onTriggerKeyDown}
          ref={trigger}
          type="button"
        >
          <span className="min-w-0 truncate" id={`${id}-value`}>
            {selected?.label}
          </span>
          {selected?.detail && (
            <span className="ml-auto shrink-0 text-[13px] text-muted tabular-nums max-sm:hidden">
              {selected.detail}
            </span>
          )}
          <ChevronDown
            aria-hidden
            className={`pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted transition-transform duration-150 ${open ? "rotate-180" : ""}`}
          />
        </button>
        {open && (
          <ul
            aria-activedescendant={`${id}-option-${active}`}
            aria-label={label}
            className="absolute inset-x-0 top-full z-20 mt-1.5 max-h-72 overflow-y-auto rounded-xl border border-line bg-card p-1.5 shadow-[0_12px_32px_rgba(20,23,43,0.14)] outline-none"
            onKeyDown={onListKeyDown}
            ref={list}
            role="listbox"
            tabIndex={-1}
          >
            {options.map((option, index) => (
              <li
                aria-selected={option.value === value}
                className={`flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 transition-colors duration-100 ${index === active ? "bg-brand-50" : ""}`}
                id={`${id}-option-${index}`}
                key={option.value}
                onClick={() => choose(index)}
                onPointerMove={() => setActive(index)}
                role="option"
              >
                <span className="min-w-0 flex-1">
                  <span className="block text-[15px] text-ink">
                    {option.label}
                  </span>
                  {option.detail && (
                    <span className="block text-[13px] text-muted tabular-nums">
                      {option.detail}
                    </span>
                  )}
                </span>
                {option.value === value && (
                  <Check aria-hidden className="size-4 shrink-0 text-brand-500" />
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Field>
  );
}
