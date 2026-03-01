package com.bank2budget.service;

import com.bank2budget.exception.CamtParseException;
import com.bank2budget.model.Transaction;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CamtParserService {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("-?\\d{1,15}(\\.\\d{1,5})?");

    public List<Transaction> parse(InputStream xmlInput) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlInput);
            document.getDocumentElement().normalize();

            validateDocument(document);

            return extractTransactions(document);
        } catch (CamtParseException e) {
            throw e;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new CamtParseException("Failed to parse CAMT.053 file: " + e.getMessage(), e);
        }
    }

    private void validateDocument(Document document) {
        NodeList stmts = document.getElementsByTagName("BkToCstmrStmt");
        if (stmts.getLength() == 0) {
            throw new CamtParseException(
                    "Not a valid CAMT.053 file: missing BkToCstmrStmt element");
        }
    }

    private List<Transaction> extractTransactions(Document document) {
        List<Transaction> transactions = new ArrayList<>();
        NodeList entries = document.getElementsByTagName("Ntry");

        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            transactions.add(extractTransaction(entry));
        }

        return transactions;
    }

    private Transaction extractTransaction(Element entry) {
        // Amount and currency - direct child of Ntry
        Element amtEl = getDirectChild(entry, "Amt");
        BigDecimal amount = amtEl != null ? parseAmount(amtEl.getTextContent().trim()) : BigDecimal.ZERO;
        String currency = amtEl != null ? amtEl.getAttribute("Ccy") : "";

        // Credit/Debit indicator
        String cdtDbtInd = getDirectChildText(entry, "CdtDbtInd");
        String type = "CRDT".equals(cdtDbtInd) ? "CREDIT" : "DEBIT";

        // Additional Entry Info (the "details" field)
        String details = getDirectChildText(entry, "AddtlNtryInf");

        // Booking date
        LocalDate bookingDate = extractDate(entry, "BookgDt");

        // Value date
        LocalDate valueDate = extractDate(entry, "ValDt");

        // Status - simple text in v2, nested Cd element in v8+
        String status = extractStatus(entry);

        // Account servicer reference
        String acctSvcrRef = getDirectChildText(entry, "AcctSvcrRef");

        // Fields from transaction details (NtryDtls/TxDtls)
        String counterpartyName = "";
        String remittanceInfo = "";
        String endToEndId = "";

        Element ntryDtls = getDirectChild(entry, "NtryDtls");
        if (ntryDtls != null) {
            Element txDtls = getDirectChild(ntryDtls, "TxDtls");
            if (txDtls != null) {
                endToEndId = extractEndToEndId(txDtls);
                counterpartyName = extractCounterpartyName(txDtls, cdtDbtInd);
                remittanceInfo = extractRemittanceInfo(txDtls);
            }
        }

        return new Transaction(
                amount,
                orEmpty(currency),
                type,
                orEmpty(details),
                bookingDate,
                valueDate,
                orEmpty(status),
                orEmpty(acctSvcrRef),
                orEmpty(counterpartyName),
                orEmpty(remittanceInfo),
                orEmpty(endToEndId)
        );
    }

    private String extractStatus(Element entry) {
        Element stsEl = getDirectChild(entry, "Sts");
        if (stsEl == null) {
            return "";
        }
        // v8+: <Sts><Cd>BOOK</Cd></Sts>
        Element cdEl = getDirectChild(stsEl, "Cd");
        if (cdEl != null) {
            return cdEl.getTextContent().trim();
        }
        // v2: <Sts>BOOK</Sts>
        return stsEl.getTextContent().trim();
    }

    private LocalDate extractDate(Element parent, String wrapperTag) {
        Element wrapper = getDirectChild(parent, wrapperTag);
        if (wrapper == null) {
            return null;
        }
        Element dtEl = getDirectChild(wrapper, "Dt");
        if (dtEl != null) {
            String dateStr = dtEl.getTextContent().trim();
            if (!dateStr.isEmpty()) {
                return parseDate(dateStr);
            }
        }
        // Fallback: date directly in wrapper element
        String directDate = wrapper.getTextContent().trim();
        if (directDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return parseDate(directDate);
        }
        return null;
    }

    private BigDecimal parseAmount(String value) {
        if (!AMOUNT_PATTERN.matcher(value).matches()) {
            throw new CamtParseException("Invalid amount format: " + value.substring(0, Math.min(30, value.length())));
        }
        return new BigDecimal(value);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new CamtParseException("Invalid date format: " + value, e);
        }
    }

    private String extractEndToEndId(Element txDtls) {
        Element refs = getDirectChild(txDtls, "Refs");
        if (refs != null) {
            String id = getDirectChildText(refs, "EndToEndId");
            return id != null ? id : "";
        }
        return "";
    }

    private String extractCounterpartyName(Element txDtls, String cdtDbtInd) {
        Element rltdPties = getDirectChild(txDtls, "RltdPties");
        if (rltdPties == null) {
            return "";
        }

        // For credits, counterparty is the debtor (who sent money)
        // For debits, counterparty is the creditor (who received money)
        String primaryTag = "CRDT".equals(cdtDbtInd) ? "Dbtr" : "Cdtr";
        String fallbackTag = "CRDT".equals(cdtDbtInd) ? "Cdtr" : "Dbtr";

        String name = getPartyName(rltdPties, primaryTag);
        if (name.isEmpty()) {
            name = getPartyName(rltdPties, fallbackTag);
        }
        return name;
    }

    private String getPartyName(Element rltdPties, String partyTag) {
        Element party = getDirectChild(rltdPties, partyTag);
        if (party != null) {
            String nm = getDirectChildText(party, "Nm");
            return nm != null ? nm : "";
        }
        return "";
    }

    private String extractRemittanceInfo(Element txDtls) {
        Element rmtInf = getDirectChild(txDtls, "RmtInf");
        if (rmtInf != null) {
            String ustrd = getDirectChildText(rmtInf, "Ustrd");
            return ustrd != null ? ustrd : "";
        }
        return "";
    }

    /**
     * Gets the first direct child element with the given tag name.
     * Unlike getElementsByTagName, this does NOT search the entire subtree.
     */
    private Element getDirectChild(Element parent, String tagName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private String getDirectChildText(Element parent, String tagName) {
        Element child = getDirectChild(parent, tagName);
        return child != null ? child.getTextContent().trim() : null;
    }

    private String orEmpty(String value) {
        return value != null ? value : "";
    }
}
