import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { forgetDemo, startDemoStudent } from "../services/demoSession";
import type { AuthSession } from "../types/auth";

export type SessionChange = (session: AuthSession | null) => void;

export function useDemoSession(onSessionChange: SessionChange) {
  const client = useQueryClient();
  const navigate = useNavigate();

  function leave() {
    forgetDemo();
    client.clear();
    onSessionChange(null);
    navigate("/");
  }

  async function renew() {
    onSessionChange(await startDemoStudent());
  }

  return { leave, renew };
}
