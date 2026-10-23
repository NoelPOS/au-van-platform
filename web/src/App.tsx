import { useState } from "react";
import {
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router";
import { AdminLayout } from "./components/AdminLayout";
import { DemoRenewal } from "./components/DemoRenewal";
import { DemoStaffSignIn } from "./components/DemoStaffSignIn";
import { RedirectHome, RequireRole } from "./components/RouteGuards";
import { AdminInsightsPage } from "./pages/AdminInsightsPage";
import { AdminOverviewPage } from "./pages/AdminOverviewPage";
import { AdminPaymentReviewPage } from "./pages/AdminPaymentReviewPage";
import { AdminRefundsPage } from "./pages/AdminRefundsPage";
import { AdminRoutesPage } from "./pages/AdminRoutesPage";
import { AdminSeatLayoutsPage } from "./pages/AdminSeatLayoutsPage";
import { AdminTripsPage } from "./pages/AdminTripsPage";
import { AdminVansPage } from "./pages/AdminVansPage";
import { LandingPage } from "./pages/LandingPage";
import { SignInPage } from "./pages/SignInPage";
import { StudentBookTripPage } from "./pages/StudentBookTripPage";
import { StudentLayout } from "./pages/StudentLayout";
import { StudentTicketPage } from "./pages/StudentTicketPage";
import { StudentTicketsPage } from "./pages/StudentTicketsPage";
import { StudentTripsPage } from "./pages/StudentTripsPage";
import {
  canResumeDemoStudent,
  savedDemoSession,
  startDemoStudent,
} from "./services/demoSession";
import {
  demoLoginEnabled,
  e2eAuthEnabled,
  insideLineApp,
  returningFromLine,
} from "./services/liffConfig";
import type { AuthSession } from "./types/auth";

function App() {
  const [session, setSession] = useState<AuthSession | null>(savedDemoSession);
  const [resuming, setResuming] = useState(canResumeDemoStudent);
  const { search } = useLocation();
  const navigate = useNavigate();

  async function resumeDemoStudent() {
    const resumed = await startDemoStudent().catch(() => null);
    setSession(resumed);
    setResuming(false);
    if (!resumed) navigate("/", { replace: true });
  }

  if (resuming) {
    return (
      <DemoRenewal
        onLeave={() => setResuming(false)}
        renew={resumeDemoStudent}
      />
    );
  }

  if (!session) {
    const signIn = <SignInPage onSignedIn={setSession} />;
    const landing =
      !e2eAuthEnabled && !insideLineApp() && !returningFromLine(search);
    return (
      <Routes>
        <Route
          element={landing ? <LandingPage onSignedIn={setSession} /> : signIn}
          path="/"
        />
        <Route element={signIn} path="/trips/*" />
        <Route element={signIn} path="/tickets/*" />
        <Route
          element={
            <SignInPage onSignedIn={setSession}>
              {demoLoginEnabled && <DemoStaffSignIn onSignedIn={setSession} />}
            </SignInPage>
          }
          path="/admin/*"
        />
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
            <StudentLayout
              key={session.accessToken}
              onSessionChange={setSession}
              session={session}
            />
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
            <AdminLayout onSessionChange={setSession} session={session} />
          </RequireRole>
        }
      >
        <Route index element={<AdminOverviewPage session={session} />} />
        <Route
          path="insights"
          element={<AdminInsightsPage session={session} />}
        />
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
          element={<Navigate replace to="/admin/trips" />}
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
