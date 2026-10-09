import { describe, expect, it } from "vitest";
import { crc16, promptPayPayload } from "./promptPay";

describe("promptPayPayload", () => {
  it("uses CRC-16/CCITT-FALSE, the checksum EMVCo QR codes carry", () => {
    expect(crc16("123456789")).toBe("29B1");
  });

  it("matches the reference payload of the promptpay-qr library", () => {
    expect(promptPayPayload("000-000-0000", 4.22)).toBe(
      "00020101021229370016A000000677010111011300660000000005802TH530376454044.226304E469",
    );
  });

  it("encodes a mobile number as 0066 plus its last nine digits", () => {
    expect(promptPayPayload("081-234-5678", 70)).toContain("01130066812345678");
  });

  it("encodes a 13-digit national or tax ID as is", () => {
    expect(promptPayPayload("1234567890123", 35)).toContain(
      "0213" + "1234567890123",
    );
  });

  it("refuses an ID PromptPay would not accept", () => {
    expect(() => promptPayPayload("12345", 35)).toThrow(/PromptPay ID/);
  });
});
