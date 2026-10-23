import { useState } from "react";
import { InsightsFigures } from "../components/InsightsFigures";
import { RevenueChart } from "../components/RevenueChart";
import { RouteInsightsTable } from "../components/RouteInsightsTable";
import { Button } from "../components/ui/Button";
import { EmptyState } from "../components/ui/EmptyState";
import { FailurePanel } from "../components/ui/FailurePanel";
import { PageHeader } from "../components/ui/PageHeader";
import { SegmentedControl } from "../components/ui/SegmentedControl";
import { Skeleton } from "../components/ui/Skeleton";
import { useInsights } from "../hooks/useInsightsQueries";
import type { AuthSession } from "../types/auth";
import type { InsightPeriod } from "../types/insights";
import { addDays, bangkokToday, shortDay } from "../utils/calendar";

type Choice = "week" | "thirty" | "month";

const choices: { value: Choice; label: string }[] = [
  { value: "week", label: "7 days" },
  { value: "thirty", label: "30 days" },
  { value: "month", label: "This month" },
];

function periodOf(choice: Choice, today: string): InsightPeriod {
  if (choice === "month") return { from: `${today.slice(0, 7)}-01`, to: today };
  return { from: addDays(today, choice === "week" ? -6 : -29), to: today };
}

export function AdminInsightsPage({ session }: { session: AuthSession }) {
  const [choice, setChoice] = useState<Choice>("week");
  const period = periodOf(choice, bangkokToday());
  const insights = useInsights(session, period);
  const data = insights.data;

  return (
    <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8 lg:px-12 lg:py-14">
      <PageHeader
        action={
          <SegmentedControl
            hideLabel
            label="Period"
            onChange={setChoice}
            options={choices}
            value={choice}
          />
        }
        description="Fares taken, seats filled and refunds owed, by departure day."
        eyebrow={`${shortDay(period.from)} – ${shortDay(period.to)} · Bangkok`}
        title="Insights"
      />
      <div className="mt-8">
        {insights.isPending && (
          <div className="flex flex-col gap-4" role="status">
            <span className="sr-only">Loading insights…</span>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {[0, 1, 2, 3].map((tile) => (
                <Skeleton className="h-36 rounded-2xl" key={tile} />
              ))}
            </div>
            <Skeleton className="h-72 rounded-2xl" />
          </div>
        )}
        {insights.error && (
          <FailurePanel error={insights.error} title="Could not load insights">
            <Button onClick={() => void insights.refetch()} variant="secondary">
              Try again
            </Button>
          </FailurePanel>
        )}
        {data && (
          <div
            aria-busy={insights.isPlaceholderData}
            className={`flex flex-col gap-10 transition-opacity duration-150 ${insights.isPlaceholderData ? "opacity-60" : ""}`}
          >
            <InsightsFigures totals={data.totals} />
            {data.totals.trips === 0 ? (
              <div className="rounded-2xl border border-dashed border-line">
                <EmptyState
                  detail="Revenue and occupancy appear here once trips depart in this period."
                  title="No departures in this period"
                />
              </div>
            ) : (
              <>
                <RevenueChart days={data.byDay} />
                <RouteInsightsTable routes={data.byRoute} />
              </>
            )}
          </div>
        )}
      </div>
    </main>
  );
}
