import { useState } from "react";
import type { AuthSession } from "../auth/session";
import { AdminInventoryPage } from "../inventory/AdminInventoryPage";
import { AdminPaymentReviewPage } from "../payments/AdminPaymentReviewPage";

type Destination = "inventory" | "payments";

const destinations: { id: Destination; label: string }[] = [
  { id: "inventory", label: "Transport inventory" },
  { id: "payments", label: "Payment review" },
];

/**
 * Everything an administrator can reach. Local state rather than a router:
 * there are two destinations and no routing dependency in this application,
 * the same way `InventoryTabs` already switches between its four sections.
 */
export function AdminPage({ session }: { session: AuthSession }) {
  const [destination, setDestination] = useState<Destination>("inventory");

  return (
    <>
      <nav
        aria-label="Administration"
        className="border-b border-line bg-white px-6 py-3"
      >
        <div className="mx-auto flex max-w-6xl gap-2">
          {destinations.map((entry) => (
            <button
              className={`rounded-lg border px-3 py-2 text-sm font-semibold transition-colors ${destination === entry.id ? "border-brand bg-brand-soft text-brand" : "border-line bg-white text-muted hover:border-muted"}`}
              key={entry.id}
              onClick={() => setDestination(entry.id)}
            >
              {entry.label}
            </button>
          ))}
        </div>
      </nav>
      {destination === "inventory" ? (
        <AdminInventoryPage session={session} />
      ) : (
        <AdminPaymentReviewPage session={session} />
      )}
    </>
  );
}
