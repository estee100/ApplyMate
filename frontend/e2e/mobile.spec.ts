import { expect, test } from "@playwright/test";
import { registerAndSkipTour } from "./helpers";

test("phone layout uses the tab bar and has no horizontal scroll", async ({ page }) => {
  await registerAndSkipTour(page);
  const tabs = page.getByRole("navigation", { name: "Tabs" });
  await expect(tabs).toBeVisible();
  await tabs.getByRole("link", { name: "Tracking" }).click();
  await expect(page).toHaveURL(/\/tracking/);
  await tabs.getByRole("link", { name: "Profile" }).click();
  await expect(page).toHaveURL(/\/profile/);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
});
