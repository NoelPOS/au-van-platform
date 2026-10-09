import { useOutletContext } from "react-router";
import type { AuthSession } from "../types/auth";
import type { Notice } from "../types/booking";

export type StudentContext = {
  session: AuthSession;
  setNotice: (notice: Notice | null) => void;
};

export function useStudent(): StudentContext {
  return useOutletContext<StudentContext>();
}
