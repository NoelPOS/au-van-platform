import { useState } from "react";
import { Navigate, Route, Routes } from "react-router";
import { AdminLayout } from "./components/AdminLayout";
import { RedirectHome, RequireRole } from "./components/RouteGuards";
import { AdminOperationsPage } from "./pages/AdminOperationsPage";
import { AdminOverviewPage } from "./pages/AdminOverviewPage";
import { AdminPaymentReviewPage } from "./pages/AdminPaymentReviewPage";
import { AdminRefundsPage } from "./pages/AdminRefundsPage";
import { AdminRoutesPage } from "./pages/AdminRoutesPage";
import { AdminSeatLayoutsPage } from "./pages/AdminSeatLayoutsPage";
import { AdminTripsPage } from "./pages/AdminTripsPage";
import { AdminVansPage } from "./pages/AdminVansPage";
import { SignInPage } from "./pages/SignInPage";
import { StudentBookTripPage } from "./pages/StudentBookTripPage";
import { StudentLayout } from "./pages/StudentLayout";
import { StudentTicketPage } from "./pages/StudentTicketPage";
import { StudentTicketsPage } from "./pages/StudentTicketsPage";
import { StudentTripsPage } from "./pages/StudentTripsPage";
import type { AuthSession } from "./types/auth";

function App() {
  const [session, setSession] = useState<AuthSession | null>(null);

  if (!session) {
    return (
      <Routes>
        {["/", "/trips/*", "/tickets/*", "/admin/*"].map((path) => (
          <Route
            element={<SignInPage onSignedIn={setSession} />}
            key={path}
            path={path}
          />
        ))}
        <Route path="*" element={<RedirectHome />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route
        path="/"
        element={
          <RequireRole role="STUDENT" session={session}>
            <StudentLayout session={session} />
          </RequireRole>
        }
      >
        <Route index element={<StudentTripsPage />} />
        <Route path="trips/:tripId" element={<StudentBookTripPage />} />
        <Route path="tickets" element={<StudentTicketsPage />} />
        <Route path="tickets/:bookingId" element={<StudentTicketPage />} />
      </Route>
      <Route
        path="/admin"
        element={
          <RequireRole role="ADMIN" session={session}>
            <AdminLayout session={session} />
          </RequireRole>
        }
      >
        <Route index element={<AdminOverviewPage session={session} />} />
        <Route path="trips" element={<AdminTripsPage session={session} />} />
        <Route path="routes" element={<AdminRoutesPage session={session} />} />
        <Route path="vans" element={<AdminVansPage session={session} />} />
        <Route
          path="seat-layouts"
          element={<AdminSeatLayoutsPage session={session} />}
        />
        <Route
          path="payments"
          element={<AdminPaymentReviewPage session={session} />}
        />
        <Route path="refunds" element={<AdminRefundsPage session={session} />} />
        <Route
          path="operations"
          element={<AdminOperationsPage session={session} />}
        />
        <Route
          path="inventory"
          element={<Navigate replace to="/admin/trips" />}
        />
      </Route>
      <Route path="*" element={<RedirectHome />} />
    </Routes>
  );
}

export default App;
