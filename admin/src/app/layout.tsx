import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Toaster } from "react-hot-toast";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "ServProWare", template: "%s · ServProWare" },
  description: "ServProWare — appointment scheduling, route planning and customer notifications",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="h-full">
        {children}
        <Toaster
          position="bottom-right"
          toastOptions={{
            duration: 4200,
            style: {
              background: "#122333",
              color: "#fff",
              fontSize: "13px",
              borderRadius: "9px",
              maxWidth: "380px",
            },
            success: { iconTheme: { primary: "#0e7c6b", secondary: "#fff" } },
          }}
        />
      </body>
    </html>
  );
}
