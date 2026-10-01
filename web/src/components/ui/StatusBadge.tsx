import { CircleCheck, CircleX, Clock, type LucideIcon } from "lucide-react";
import { Chip, type ChipTone } from "./Chip";

type Look = { tone: ChipTone; icon: LucideIcon };

const settled: Look = { tone: "success", icon: CircleCheck };
const waiting: Look = { tone: "warning", icon: Clock };
const stopped: Look = { tone: "danger", icon: CircleX };

const looks: Record<string, Look> = {
  ACTIVE: settled,
  CONFIRMED: settled,
  PENDING_PAYMENT: waiting,
  PAYMENT_UNDER_REVIEW: waiting,
};

export function StatusBadge({ value }: { value: string }) {
  const { tone, icon } = looks[value] ?? stopped;
  return (
    <Chip tone={tone} icon={icon}>
      {value}
    </Chip>
  );
}
