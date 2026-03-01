function doPost(e) {
  try {
    var spreadsheetId = PropertiesService.getScriptProperties().getProperty("SPREADSHEET_ID");
    if (!spreadsheetId) {
      Logger.log("SPREADSHEET_ID not set in script properties.");
      return ContentService.createTextOutput(JSON.stringify({"status":"error", "message":"SPREADSHEET_ID not configured in script properties."}))
                           .setMimeType(ContentService.MimeType.JSON);
    }

    var spreadsheet = SpreadsheetApp.openById(spreadsheetId);
    var sheet = spreadsheet.getSheetByName("Expenses");

    // Log the request data
    Logger.log("Received POST request with data: " + e.postData.contents);
    var data = JSON.parse(e.postData.contents);

    // Log sheet access
    if (sheet) {
      Logger.log("Successfully accessed 'Expenses' sheet.");
    } else {
      Logger.log("Could not access 'Expenses' sheet. Please check the sheet name.");
      // Stop execution if the sheet is not found
      return ContentService.createTextOutput(JSON.stringify({"status":"error", "message":"Sheet 'Expenses' not found."}))
                           .setMimeType(ContentService.MimeType.JSON);
    }

    Logger.log("Parsed data: " + JSON.stringify(data));

    var transactions = data.transactions;
    if (!transactions || !Array.isArray(transactions) || transactions.length === 0) {
      Logger.log("No transactions found in payload.");
      return ContentService.createTextOutput(JSON.stringify({"status":"error", "message":"No transactions in payload."}))
                           .setMimeType(ContentService.MimeType.JSON);
    }

    Logger.log("Processing " + transactions.length + " transactions.");

    var importedAt = new Date();
    for (var i = 0; i < transactions.length; i++) {
      var tx = transactions[i];
      var row = [
        importedAt,
        tx.date || "",
        tx.amount || 0,
        tx.currency || "",
        tx.type || "",
        tx.category || "",
        tx.details || ""
      ];
      Logger.log("Appending row " + (i + 1) + ": " + row);
      sheet.appendRow(row);
    }

    Logger.log(transactions.length + " rows appended successfully.");

    return ContentService.createTextOutput(JSON.stringify({"status":"success", "rowsAdded": transactions.length}))
                         .setMimeType(ContentService.MimeType.JSON);
  } catch (exception) {
    Logger.log("Error: " + exception.message);
    return ContentService.createTextOutput(JSON.stringify({"status":"exception","message":exception.message}))
                         .setMimeType(ContentService.MimeType.JSON);
  }
}
