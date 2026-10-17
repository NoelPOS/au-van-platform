import type { ReactNode } from "react";
import { Img, staticFile } from "remotion";
import { color, font } from "../theme";

export const screen = { width: 436, height: 945 };

export function PhoneFrame({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        width: "fit-content",
        padding: 12,
        borderRadius: 64,
        background: color.ink,
        boxShadow: "0 50px 90px -50px rgba(20,23,43,0.65)",
      }}
    >
      <div
        style={{
          position: "relative",
          ...screen,
          overflow: "hidden",
          borderRadius: 52,
          background: color.paper,
        }}
      >
        <span
          style={{
            position: "absolute",
            top: 14,
            left: "50%",
            zIndex: 10,
            width: 110,
            height: 30,
            marginLeft: -55,
            borderRadius: 15,
            background: color.ink,
          }}
        />
        {children}
      </div>
    </div>
  );
}

export function BrowserFrame({
  address,
  width,
  children,
}: {
  address: string;
  width: number;
  children: ReactNode;
}) {
  return (
    <div
      style={{
        width,
        overflow: "hidden",
        borderRadius: 18,
        border: `1px solid ${color.line}`,
        background: color.card,
        boxShadow: "0 50px 90px -60px rgba(20,23,43,0.55)",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 10,
          height: 56,
          padding: "0 22px",
          borderBottom: `1px solid ${color.line}`,
        }}
      >
        {[0, 1, 2].map((dot) => (
          <span
            key={dot}
            style={{ width: 12, height: 12, borderRadius: 6, background: color.line }}
          />
        ))}
        <span
          style={{
            marginLeft: 16,
            padding: "6px 14px",
            borderRadius: 8,
            background: color.paper,
            fontFamily: font.mono,
            fontSize: 15,
            color: color.muted,
          }}
        >
          {address}
        </span>
      </div>
      <div style={{ position: "relative", aspectRatio: "16 / 10" }}>{children}</div>
    </div>
  );
}

export function Screenshot({ file }: { file: string }) {
  return (
    <Img
      src={staticFile(file)}
      style={{
        width: "100%",
        height: "100%",
        objectFit: "cover",
        objectPosition: "top",
      }}
    />
  );
}
