import { expect, test } from "@playwright/test";
import { registerAndSkipTour } from "./helpers";

test.describe("ApplyMate", () => {
  test("signed-out visitors are sent to sign in, then back where they were going", async ({ page }) => {
    await page.goto("/tracking");
    await expect(page).toHaveURL(/\/login\?next=%2Ftracking/);
  });

  test("log an application, move it through the pipeline, and find it with search", async ({ page }) => {
    await registerAndSkipTour(page);
    const company = `Initech ${Date.now()}`;

    // Log an application from the dashboard.
    await page.getByRole("button", { name: "Log an application" }).first().click();
    const dialog = page.getByRole("dialog", { name: "Log an application" });
    await dialog.getByLabel("Company").fill(company);
    await dialog.getByLabel("Position").fill("Backend Developer");
    await dialog.getByRole("button", { name: "Save" }).click();
    await expect(page).toHaveURL(/\/applications\/\d+/);
    await expect(page.getByRole("heading", { name: "Backend Developer" })).toBeVisible();

    // Move it to Interviewing with a note; the timeline records it.
    await page.getByLabel("Stage").selectOption("INTERVIEWING");
    await page.getByPlaceholder(/What happened/).fill("Recruiter booked a call");
    await page.getByRole("button", { name: "Update" }).click();
    await expect(page.getByText("Moved to Interviewing").first()).toBeVisible();
    await expect(page.getByText("Recruiter booked a call")).toBeVisible();

    // On the board, use the keyboard-accessible menu to move it to Offer.
    await page.goto("/tracking");
    await page.getByRole("button", { name: `Move ${company}` }).click();
    await page.getByRole("menuitem", { name: "Offer" }).click();
    await expect(page.getByText(`${company} moved to Offer`)).toBeVisible();
    await expect(page.getByRole("region", { name: "Offer" }).getByText(company)).toBeVisible();

    // The command palette finds it by full-text search and opens it.
    await page.keyboard.press("Control+k");
    await page.getByPlaceholder(/Search applications and jobs/).fill(company.split(" ")[1]);
    const result = page.getByRole("option", { name: new RegExp(company) });
    await expect(result).toBeVisible();
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/\/applications\/\d+/);
  });

  test("add a job by pasting its text, save it, and see it in Saved", async ({ page }) => {
    await registerAndSkipTour(page);
    await page.goto("/jobs");
    await page.getByRole("button", { name: "Add a job" }).click();
    const dialog = page.getByRole("dialog", { name: "Add a job" });
    await dialog.getByRole("button", { name: "Paste the text" }).click();
    await dialog.getByLabel("Job title").fill("Platform Engineer");
    await dialog.getByLabel("Company").fill("Globex");
    await dialog.getByLabel("Job description").fill(
      "We are hiring a platform engineer to run Kubernetes, Terraform and PostgreSQL in production. On-call rotation.",
    );
    await dialog.getByRole("button", { name: "Add job" }).click();

    const card = page.getByRole("button", { name: "Platform Engineer at Globex" });
    await expect(card).toBeVisible();
    await card.click();
    const details = page.getByRole("dialog", { name: "Job details" });
    await details.getByRole("button", { name: "Save", exact: true }).click();
    await expect(details.getByRole("button", { name: "Saved" })).toBeVisible();
    await page.keyboard.press("Escape");

    await page.goto("/saved");
    await expect(page.getByRole("button", { name: "Platform Engineer at Globex" })).toBeVisible();
  });

  test("dark mode persists across reloads and sign-out ends the session", async ({ page }) => {
    await registerAndSkipTour(page);
    await page.getByRole("button", { name: /Switch to (dark|light) theme/ }).first().click();
    const dark = await page.evaluate(() => document.documentElement.classList.contains("dark"));
    await page.reload();
    expect(await page.evaluate(() => document.documentElement.classList.contains("dark"))).toBe(dark);

    await page.getByRole("button", { name: "Sign out" }).first().click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login/);
  });
});
