// EMVCo merchant-presented QR as Thai banks read it for PromptPay.
const promptPayAid = "A000000677010111";

function field(id: string, value: string): string {
  return `${id}${String(value.length).padStart(2, "0")}${value}`;
}

export function crc16(data: string): string {
  let crc = 0xffff;
  for (const char of data) {
    crc ^= char.charCodeAt(0) << 8;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = crc & 0x8000 ? (crc << 1) ^ 0x1021 : crc << 1;
      crc &= 0xffff;
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, "0");
}

function target(id: string): string {
  const digits = id.replace(/\D/g, "");
  if (digits.length === 10 && digits.startsWith("0"))
    return field("01", `0066${digits.slice(1)}`);
  if (digits.length === 13) return field("02", digits);
  if (digits.length === 15) return field("03", digits);
  throw new Error("A PromptPay ID is a 10-digit mobile number or a 13 or 15-digit ID.");
}

export function promptPayPayload(id: string, amount: number): string {
  const body = [
    field("00", "01"),
    field("01", "12"),
    field("29", field("00", promptPayAid) + target(id)),
    field("58", "TH"),
    field("53", "764"),
    field("54", amount.toFixed(2)),
  ].join("");
  return `${body}6304${crc16(`${body}6304`)}`;
}
