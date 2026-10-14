import { expect, type Page } from "@playwright/test";

/** Registers a brand-new user through the UI and dismisses the first-run tour. */
export async function registerAndSkipTour(page: Page, name = "E2E Tester") {
  const email = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
  await page.goto("/register");
  await page.getByLabel("Name").fill(name);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("password1234");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/dashboard/);
  // The onboarding tour starts automatically for a new account.
  const tour = page.getByRole("dialog", { name: "Welcome to ApplyMate" });
  await expect(tour).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(tour).toBeHidden();
  return email;
}
