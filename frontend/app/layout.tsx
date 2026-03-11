import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "ApplyMate — Job Application Tracker",
  description: "Track and manage your job applications",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
