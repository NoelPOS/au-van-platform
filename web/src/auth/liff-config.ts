export function configuredLiffId(): string | null {
  const liffId = import.meta.env.VITE_LIFF_ID?.trim()
  return liffId ? liffId : null
}
