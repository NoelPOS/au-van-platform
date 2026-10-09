import "@testing-library/jest-dom/vitest";

// jsdom has no modal dialogs; this opens one without the top layer or inertness.
HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
  this.open = true;
};
HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
  this.open = false;
  this.dispatchEvent(new Event("close"));
};

// jsdom has no layout: no media query matches and scrolling does nothing.
window.matchMedia ??= (query: string) => ({ matches: false, media: query }) as MediaQueryList;
Element.prototype.scrollIntoView ??= function () {};

// jsdom has no object URLs.
URL.createObjectURL ??= () => "blob:preview";
URL.revokeObjectURL ??= () => {};
