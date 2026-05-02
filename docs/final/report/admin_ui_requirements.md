# Admin Dashboard — UI/UX Requirements

**Feature:** Report Management  
**Audience:** This document is for Figma designers. No technical backend details are included. Focus on layouts, user flows, states, and copy.

---

## Who Uses This

**Admin users only.** A regular user will never see these screens. The admin signs in with their normal account — the system automatically shows the admin dashboard because their account has elevated access. There is no separate login page.

---

## Navigation

Add a new section called **"Reports"** to the admin sidebar or top navigation. It should show a badge counter with the number of pending (unreviewed) reports. The badge disappears when all reports are resolved.

---

## Screen 1 — Reports List

### Purpose

Give the admin a quick overview of all submitted reports and let them jump to any one.

### Layout

A full-width data table. Above the table, place a status filter with three tabs or a segmented control:

| Tab | Shows |
|---|---|
| **All** | Every report, newest first |
| **Pending** | Reports still waiting for admin review |
| **Resolved** | Reports that have been approved or rejected |

### Table Columns

| Column | Content | Notes |
|---|---|---|
| **Date** | When the report was filed | Format: `DD MMM YYYY, HH:MM` |
| **Reported User** | Name or ID of the person being accused | Clickable to view their profile (future scope) |
| **Reporter** | Name or ID of the person who filed it | |
| **Reason** | One of: Safety Concern · Misconduct · Other · Emergency | Display as a readable label, not a raw code |
| **Status** | Pending / Approved / Rejected | Displayed as a color-coded badge (see Status Badges below) |
| **Action** | A "Review" button | Only visible on Pending rows. Resolved rows have no action button — just the status badge. |

### Status Badges

| Status | Label | Color |
|---|---|---|
| Pending | `PENDING` | Orange / Amber |
| Approved | `APPROVED` | Green |
| Rejected | `REJECTED` | Red / Muted red |

### Empty State

If no reports match the selected filter, show a centered illustration with the message:
> "No reports found."

---

## Screen 2 — Report Detail

### How to Get Here

Admin clicks the **"Review"** button on a Pending row in the Reports List, or clicks any row to view a resolved report.

### Layout

A single-column detail page with two clear sections: **Evidence** (what the reporter submitted) and **Resolution** (admin's action panel).

---

### Section A — Evidence

This section is read-only. Show the following information:

| Label | Content |
|---|---|
| **Report ID** | A short reference number (for internal tracking) |
| **Filed On** | Date and time the report was submitted |
| **Reporter** | The name / ID of the user who filed the report |
| **Reported User** | The name / ID of the user being accused |
| **Reason** | Full readable label (Safety Concern / Partner Misconduct / Emergency / Other) |
| **Evidence Link** | If provided, display as a clickable blue hyperlink that opens in a new tab. If not provided, show "No evidence provided" in muted text. |
| **Trust Score Impact** | Show the trust points deducted from the reported user when the report was filed (e.g., "−30 points applied"). If no deduction was made (e.g., Emergency reason), show "No penalty applied." |

**Design note on Evidence Link:** Because the link leads to an external resource (photo, video, etc.), display a small external-link icon next to it. Do not embed or preview the content inline.

---

### Section B — Resolution Panel

This section changes depending on the report's current status.

#### When Status is PENDING (Admin has not acted yet)

Display a form with:

1. **Optional Note field** — a multi-line text area.
   - Label: "Resolution Note (optional)"
   - Placeholder: "Add context or reasoning for your decision..."
   - Max ~500 characters.

2. **Two action buttons side by side:**

   - **Approve** button — solid green or primary color
     - Label: `Approve Report`
   - **Reject** button — outlined or secondary style, red/muted
     - Label: `Reject Report`

Both buttons open a **confirmation dialog** before executing (see Screen 3).

#### When Status is APPROVED

Replace the form with a read-only summary:

| Label | Content |
|---|---|
| **Decision** | ✅ Approved |
| **Decided On** | Date and time of resolution |
| **Note** | Admin's note (or "No note added" in muted text if empty) |

Show an informational banner:
> "The trust penalty remains in effect. No further action is required."

#### When Status is REJECTED

Replace the form with a read-only summary:

| Label | Content |
|---|---|
| **Decision** | ❌ Rejected |
| **Decided On** | Date and time of resolution |
| **Note** | Admin's note (or "No note added" in muted text if empty) |

Show an informational banner:
> "The trust penalty has been reversed. The reported user's score has been restored."

If the original report had no penalty to reverse (Emergency reason), show instead:
> "No trust adjustment was needed for this report."

---

## Screen 3 — Confirmation Dialogs

Shown as a centered modal overlay before any irreversible action. The background is dimmed. There is no way to undo after confirming.

---

### 3A — Approve Confirmation

**Title:** `Confirm: Approve This Report`

**Body:**
> "You are marking this report as **valid**. The trust score deduction already applied to [Reported User's Name] will remain permanently.
>
> This action cannot be undone."

**Buttons:**
- `Confirm Approval` — solid green (primary action)
- `Cancel` — text link or secondary button

---

### 3B — Reject Confirmation

**Title:** `Confirm: Reject This Report`

**Body:**
> "You are dismissing this report as **invalid**. The trust score deducted from [Reported User's Name] will be **restored**.
>
> This action cannot be undone."

If the report had no deduction (Emergency), replace the body with:
> "You are dismissing this report. No trust score change was applied, so no reversal is needed.
>
> This action cannot be undone."

**Buttons:**
- `Confirm Rejection` — solid red (primary action)
- `Cancel` — text link or secondary button

---

## Feedback States

After a successful admin action, show a **toast notification** at the top-right corner:

| Action | Toast message |
|---|---|
| Approved | `"Report approved. Trust penalty stands."` |
| Rejected | `"Report rejected. Trust score restored."` |

The toast auto-dismisses after 3–4 seconds.

If the action fails (network error, etc.), show an **error toast**:
> `"Something went wrong. Please try again."`

After a successful action, the status badge on the detail page updates immediately, and the Resolution Panel switches from the form to the read-only summary view.

---

## Accessibility & Edge Cases

| Scenario | Expected behavior |
|---|---|
| Admin opens a report that was already resolved by another admin | Show the read-only resolution summary. Hide all action buttons and the form entirely. |
| Evidence URL is broken or leads to a 404 page | The admin sees the raw URL as text. Do not validate links in the UI. |
| Report note contains very long text | Truncate in the list view with an ellipsis. Show the full note in the detail view with scroll. |
| Admin double-clicks the Approve or Reject button | The confirmation dialog prevents double-submission. Buttons inside the dialog are disabled after the first click while the action is processing. Show a loading spinner on the confirm button. |
| No reports exist at all | Show the empty state on the Reports List screen (see Screen 1). |

---

## Summary of Screens

| Screen | Route (suggestion) | Key Interaction |
|---|---|---|
| Reports List | `/admin/reports` | Filter by status, click row to view |
| Report Detail — Pending | `/admin/reports/:id` | Approve or Reject (with dialog) |
| Report Detail — Resolved | `/admin/reports/:id` | Read-only view |
| Confirmation Dialog | Overlay on Detail screen | Confirm or Cancel action |
