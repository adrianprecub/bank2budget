# bank2budget

Parse CAMT.053 bank statements, auto-categorize transactions, and export to Excel or Google Sheets.

A Spring Boot 4 REST API that processes CAMT.053 bank statement files (ISO 20022), categorizes transactions using keyword matching (with optional OpenAI fallback), and exports results as JSON, Excel (.xlsx), or Google Sheets.

## Requirements

- Java 25
- Maven 3.9+

## Configuration

Set your Google Apps Script web app URL in `src/main/resources/application.yml`:

```yaml
app:
  google-sheets:
    url: "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec"
```

Or pass it at startup:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--app.google-sheets.url=https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec"
```

If the URL is left empty, the app still parses files and returns transactions -- it just skips the Google Sheets upload.

For instructions on setting up the Google Apps Script web app, see [docs/google-sheets-web-app-guide.md](docs/google-sheets-web-app-guide.md).

## Running

### With Maven

```bash
mvn spring-boot:run
```

### With Docker

```bash
# Build the image (compiles the app inside the container — no local JDK required)
docker build -t bank2budget .

# Run with Google Sheets and OpenAI configured
docker run -p 8080:8080 \
  -e GOOGLE_SHEETS_URL="https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec" \
  -e OPENAI_API_KEY="sk-..." \
  bank2budget

# Run without external integrations (parse only, no upload, no AI)
docker run -p 8080:8080 bank2budget
```

The server starts on port 8080 by default.

## API

### `POST /api/payments/upload`

Upload a CAMT.053 ZIP file (containing one or more XML files) for processing.

```bash
curl -F "file=@statements.zip" http://localhost:8080/api/payments/upload
```

### `POST /api/payments/upload-xml`

Upload a single CAMT.053 XML file for processing.

```bash
curl -F "file=@statement.xml" http://localhost:8080/api/payments/upload-xml
```

#### Response

Both endpoints return the same response shape:

```json
{
  "fileCount": 1,
  "transactionCount": 42,
  "uploadedToGoogleSheets": true,
  "message": "Parsed 42 transactions from 1 files and uploaded to Google Sheets"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `fileCount` | integer | Number of files processed |
| `transactionCount` | integer | Total transactions parsed across all files |
| `uploadedToGoogleSheets` | boolean | Whether transactions were successfully pushed to Google Sheets |
| `message` | string | Human-readable summary of the processing result |

## Extracted fields

| Field | CAMT.053 source | Description |
|---|---|---|
| `amount` | `Ntry/Amt` | Transaction amount |
| `currency` | `Ntry/Amt/@Ccy` | ISO currency code |
| `type` | `Ntry/CdtDbtInd` | `CREDIT` or `DEBIT` |
| `details` | `Ntry/AddtlNtryInf` | Free-text entry info from the bank |
| `bookingDate` | `Ntry/BookgDt/Dt` | Date the bank processed the transaction |
| `valueDate` | `Ntry/ValDt/Dt` | Date funds became available |
| `status` | `Ntry/Sts` | `BOOK` (booked) or `PDNG` (pending) |
| `accountServicerReference` | `Ntry/AcctSvcrRef` | Bank's internal reference |
| `counterpartyName` | `NtryDtls/TxDtls/RltdPties` | Name of the sender or receiver |
| `remittanceInfo` | `NtryDtls/TxDtls/RmtInf/Ustrd` | Payment description / reference |
| `endToEndId` | `NtryDtls/TxDtls/Refs/EndToEndId` | End-to-end tracing identifier |
| `category` | — | Assigned after parsing via keyword rules or AI fallback; empty string if uncategorized |

## Google Sheets integration

When configured, transactions are POSTed as JSON to your Apps Script URL after categorization has been applied:

```json
{
  "transactions": [
    {
      "date": "2024-01-15",
      "amount": 1500.00,
      "currency": "EUR",
      "type": "CREDIT",
      "category": "Income",
      "details": "Monthly salary payment"
    }
  ]
}
```

Your Apps Script `doPost(e)` function receives this in `e.postData.contents`. See [`docs/google-sheets-web-app-guide.md`](docs/google-sheets-web-app-guide.md) for setup instructions.

## CAMT.053 compatibility

The parser handles both older (v2 / `001.02`) and newer (v8+ / `001.08`) CAMT.053 formats, including differences like the nested status element (`<Sts><Cd>BOOK</Cd></Sts>`) in newer versions.

## Tests

```bash
mvn test
```
