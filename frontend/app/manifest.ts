import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "ApplyMate",
    short_name: "ApplyMate",
    description: "Find jobs, see how well you match, tailor your resume and track every application.",
    start_url: "/dashboard",
    scope: "/",
    display: "standalone",
    background_color: "#09090b",
    theme_color: "#6d4aff",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
      { src: "/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
    shortcuts: [
      { name: "Jobs", url: "/jobs" },
      { name: "Tracking board", url: "/tracking" },
    ],
  };
}
