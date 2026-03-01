# How to Create a Google Sheet with a Public Google Apps Script Web App

This guide walks you through creating a Google Sheet and exposing it via a publicly accessible Google Apps Script web app.

---

## Prerequisites

- A Google account
- Access to [Google Drive](https://drive.google.com)

---

## Step 1: Create a Google Sheet

1. Go to [Google Sheets](https://sheets.google.com) and click **Blank** to create a new spreadsheet.
2. Rename it by clicking **Untitled spreadsheet** at the top and giving it a meaningful name (e.g., `Payment Processor`).
3. Create a sheet tab named **`Expenses`** — right-click the default `Sheet1` tab at the bottom and select **Rename**.
4. Add the following header row to the `Expenses` sheet:

   | importedAt | date | amount | currency | type | category | details |
   |------------|------|--------|----------|------|----------|---------|

5. Note the **Spreadsheet ID** from the URL:
   ```
   https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit
   ```

---

## Step 2: Open the Apps Script Editor

1. In your spreadsheet, click **Extensions** > **Apps Script**.
2. The Apps Script editor will open in a new tab.
3. Rename the project (optional) by clicking **Untitled project** at the top.

---

## Step 3: Add the Script

Replace the default content in `Code.gs` with the contents of [`scripts/Code.gs`](../scripts/Code.gs) from this repository.

**What the script does:**
- `doPost(e)` handles HTTP POST requests sent to the web app URL.
- It reads the `SPREADSHEET_ID` from **Script Properties** (never hardcoded).
- It parses the POST body as JSON, expecting a `transactions` array where each item has: `date`, `amount`, `currency`, `type`, `category`, and `details`.
- Each transaction is appended as a new row to the `Expenses` sheet, prefixed with an `importedAt` timestamp. The `category` field is automatically populated by the payment processor using keyword rules and an optional AI fallback before the upload.
- Returns a JSON response: `{"status":"success","rowsAdded": N}` on success, or an error message otherwise.

---

## Step 4: Set the Spreadsheet ID in Script Properties

The script reads the spreadsheet ID from Script Properties — **do not hardcode it**.

1. In the Apps Script editor, click **Project Settings** (gear icon on the left sidebar).
2. Scroll down to **Script Properties** and click **Add script property**.
3. Set:
   - **Property**: `SPREADSHEET_ID`
   - **Value**: the ID you copied from the spreadsheet URL in Step 1
4. Click **Save script properties**.

---

## Step 5: Deploy as a Web App

1. Click the **Deploy** button (top right) > **New deployment**.
2. Click the gear icon next to **Select type** and choose **Web app**.
3. Fill in the deployment settings:
   - **Description**: (optional) e.g., `Initial deployment`
   - **Execute as**: `Me` — the script runs with your Google account permissions.
   - **Who has access**: `Anyone` — makes the app publicly accessible without login.
4. Click **Deploy**.
5. If prompted, click **Authorize access** and follow the OAuth consent flow to grant the script access to your spreadsheet.
6. Copy the **Web app URL** provided after deployment.

---

## Step 6: Test the Web App

Send a POST request to the web app URL with a JSON payload. Using `curl`:

```bash
curl -L -X POST "<YOUR_WEB_APP_URL>" \
  -H "Content-Type: application/json" \
  -d '{
    "transactions": [
      {
        "date": "2026-03-01",
        "amount": 49.99,
        "currency": "USD",
        "type": "DBIT",
        "category": "Groceries",
        "details": "Albert Heijn supermarket"
      }
    ]
  }'
```

Expected response:

```json
{"status":"success","rowsAdded":1}
```

Open your Google Sheet and confirm a new row appeared in the `Expenses` tab.

---

## Step 7: Updating the App

When you modify the script, you must **create a new deployment** or **update an existing one** for changes to take effect:

1. Click **Deploy** > **Manage deployments**.
2. Click the pencil (edit) icon on your existing deployment.
3. Under **Version**, select **New version**.
4. Click **Deploy**.

> Note: The web app URL remains the same after an update.

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `SPREADSHEET_ID not configured` error | Add the `SPREADSHEET_ID` script property as described in Step 4. |
| `Sheet 'Expenses' not found` error | Ensure the sheet tab is named exactly `Expenses` (case-sensitive). |
| `No transactions in payload` error | Verify your POST body includes a non-empty `transactions` array. |
| `Authorization required` response | Re-check **Who has access** is set to `Anyone` in the deployment settings. |
| Old behavior after editing script | Redeploy with a new version — changes do not apply to existing deployments automatically. |
| Permission denied on spreadsheet | Make sure **Execute as** is set to `Me` and you completed the OAuth authorization. |
