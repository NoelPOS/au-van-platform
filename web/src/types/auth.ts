export type ApplicationRole = "STUDENT" | "ADMIN";

export type AuthenticatedUser = {
  id: string;
  role: ApplicationRole;
  displayName: string | null;
};

export type AuthSession = {
  accessToken: string;
  expiresIn: number;
  user: AuthenticatedUser;
};
