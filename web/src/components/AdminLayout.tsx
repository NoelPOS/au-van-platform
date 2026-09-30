import { NavLink, Outlet } from "react-router";

const destinations = [
  { path: "/admin/inventory", label: "Transport inventory" },
  { path: "/admin/payments", label: "Payment review" },
  { path: "/admin/operations", label: "Operations" },
];

export function AdminLayout() {
  return (
    <>
      <nav
        aria-label="Administration"
        className="border-b border-line bg-white px-6 py-3"
      >
        <div className="mx-auto flex max-w-6xl gap-2">
          {destinations.map((entry) => (
            <NavLink
              className={({ isActive }) =>
                `rounded-lg border px-3 py-2 text-sm font-semibold transition-colors ${isActive ? "border-brand bg-brand-soft text-brand" : "border-line bg-white text-muted hover:border-muted"}`
              }
              key={entry.path}
              to={entry.path}
            >
              {entry.label}
            </NavLink>
          ))}
        </div>
      </nav>
      <Outlet />
    </>
  );
}
