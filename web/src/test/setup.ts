import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";

// jsdom has no canvas, which the QR library draws PNGs on.
vi.mock("qrcode", () => ({
  default: { toDataURL: vi.fn(async () => "data:image/png;base64,cXI=") },
}));

// jsdom has no modal dialogs; this opens one without the top layer or inertness.
HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
  this.open = true;
};
HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
  this.open = false;
  this.dispatchEvent(new Event("close"));
};

window.matchMedia ??= (query: string) => ({ matches: false, media: query }) as MediaQueryList;
Element.prototype.scrollIntoView ??= function () {};

globalThis.EventSource ??= class {
  close() {}
} as unknown as typeof EventSource;

URL.createObjectURL ??= () => "blob:preview";
URL.revokeObjectURL ??= () => {};
