// A number no bank account answers to, so the sample QR can never pay anyone.
const sampleId = "000-000-0000";

export type PromptPayAccount = { id: string; name: string | null; sample: boolean };

export function promptPayAccount(): PromptPayAccount {
  const id = import.meta.env.VITE_PROMPTPAY_ID?.trim();
  const name = import.meta.env.VITE_PROMPTPAY_NAME?.trim() || null;
  return id ? { id, name, sample: false } : { id: sampleId, name, sample: true };
}
