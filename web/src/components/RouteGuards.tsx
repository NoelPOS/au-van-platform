import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import type { ApplicationRole, AuthSession } from "../types/auth";

const homeOf: Record<ApplicationRole, string> = {
  ADMIN: "/admin",
  STUDENT: "/",
};

export function RequireRole({
  session,
  role,
  children,
}: {
  session: AuthSession;
  role: ApplicationRole;
  children: ReactNode;
}) {
  if (session.user.role !== role) {
    return <Navigate replace to={homeOf[session.user.role]} />;
  }
  return children;
}

export function RedirectHome() {
  const { search } = useLocation();
  // LIFF returns with code, state and liff.state in the query, and
  // liff.init() must still see them.
  return <Navigate replace to={{ pathname: "/", search }} />;
}
