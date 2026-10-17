import { Logo } from "./ui/Logo";

export const githubUrl = "https://github.com/NoelPOS/au-van-platform";

const links = [
  { href: "#how-it-works", label: "How it works" },
  { href: "#staff", label: "Staff portal" },
  { href: githubUrl, label: "GitHub" },
];

export function LandingHeader() {
  return (
    <header className="border-b border-line">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-5 py-4 sm:px-8 sm:py-5">
        <p className="font-display text-xl tracking-tight text-brand-900 sm:text-2xl">
          <Logo />
        </p>
        <nav aria-label="Landing">
          <ul className="flex items-center gap-3 text-[13px] font-medium text-ink sm:gap-8 sm:text-sm">
            {links.map((link) => (
              <li key={link.href}>
                <a
                  className="inline-flex min-h-11 items-center underline-offset-4 hover:text-brand-600 hover:underline"
                  href={link.href}
                >
                  {link.label}
                </a>
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </header>
  );
}
