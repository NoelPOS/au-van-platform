import { useState } from "react";
import { Navigate, Route, Routes } from "react-router";
import { AdminLayout } from "./components/AdminLayout";
import { RedirectHome, RequireRole } from "./components/RouteGuards";
import { AdminInventoryPage } from "./pages/AdminInventoryPage";
import { AdminOperationsPage } from "./pages/AdminOperationsPage";
import { AdminPaymentReviewPage } from "./pages/AdminPaymentReviewPage";
import { SignInPage } from "./pages/SignInPage";
import { StudentBookingPage } from "./pages/StudentBookingPage";
import type { AuthSession } from "./types/auth";

function App() {
  const [session, setSession] = useState<AuthSession | null>(null);

  if (!session) {
    return (
      <Routes>
        <Route path="/" element={<SignInPage onSignedIn={setSession} />} />
        <Route
          path="/admin/*"
          element={<SignInPage onSignedIn={setSession} />}
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
            <StudentBookingPage session={session} />
          </RequireRole>
        }
      />
      <Route
        path="/admin"
        element={
          <RequireRole role="ADMIN" session={session}>
            <AdminLayout />
          </RequireRole>
        }
      >
        <Route index element={<Navigate replace to="inventory" />} />
        <Route
          path="inventory"
          element={<AdminInventoryPage session={session} />}
        />
        <Route
          path="payments"
          element={<AdminPaymentReviewPage session={session} />}
        />
        <Route
          path="operations"
          element={<AdminOperationsPage session={session} />}
        />
      </Route>
      <Route path="*" element={<RedirectHome />} />
    </Routes>
  );
}

export default App;
