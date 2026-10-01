import "@testing-library/jest-dom/vitest";

// jsdom has no modal dialogs; this opens one without the top layer or inertness.
HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
  this.open = true;
};
HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
  this.open = false;
  this.dispatchEvent(new Event("close"));
};
