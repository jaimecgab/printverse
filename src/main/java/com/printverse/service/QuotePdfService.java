package com.printverse.service;

import com.printverse.domain.Quote;
import com.printverse.domain.QuoteItem;
import com.printverse.domain.QuoteStatus;
import com.printverse.exception.ResourceNotFoundException;
import com.printverse.repository.QuoteRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.printverse.service.MoneyUtils.money;
import static com.printverse.service.MoneyUtils.percentage;

@Service
public class QuotePdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final QuoteRepository quoteRepository;

    public QuotePdfService(QuoteRepository quoteRepository) {
        this.quoteRepository = quoteRepository;
    }

    @Transactional(readOnly = true)
    public GeneratedPdf generate(Long quoteId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote " + quoteId + " was not found"));
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Cotizacion " + quote.getQuoteNumber());
            document.getDocumentInformation().setAuthor("PrintVerse");
            PdfWriter writer = new PdfWriter(document, quote);
            writer.write();
            writer.close();
            document.save(output);
            String safeFolio = quote.getQuoteNumber().replaceAll("[^A-Za-z0-9._-]", "_");
            return new GeneratedPdf(output.toByteArray(), "cotizacion-" + safeFolio + ".pdf");
        } catch (IOException exception) {
            throw new IllegalStateException("The quote PDF could not be generated", exception);
        }
    }

    public record GeneratedPdf(byte[] content, String fileName) {
    }

    private static final class PdfWriter implements AutoCloseable {
        private static final float LEFT = 45;
        private static final float RIGHT = 550;
        private static final float BOTTOM = 55;
        private static final float LINE_HEIGHT = 13;
        private static final float NORMAL_TEXT_COLOR = 95 / 255f;
        private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private static final DecimalFormat MONEY_FORMAT;

        static {
            DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
            MONEY_FORMAT = new DecimalFormat("$#,##0.00 'MXN'", symbols);
        }

        private final PDDocument document;
        private final Quote quote;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        private PdfWriter(PDDocument document, Quote quote) {
            this.document = document;
            this.quote = quote;
        }

        private void write() throws IOException {
            newPage(false);
            writeCommercialHeader();
            writeItems();
            writeTotals();
            writeTerms();
        }

        private void writeCommercialHeader() throws IOException {
            text("COTIZACIÓN", BOLD, 19, LEFT, y);
            y -= 25;
            String title = quote.getTitle() == null ? "Servicio de impresion 3D" : quote.getTitle();
            paragraph(title, BOLD, 13, RIGHT - LEFT, 16);
            y -= 4;

            float detailY = y;
            text("Folio", BOLD, 9, LEFT, detailY);
            text(quote.getQuoteNumber(), REGULAR, 10, LEFT, detailY - 14);
            text("Cliente", BOLD, 9, 210, detailY);
            List<String> customerLines = wrap(customerName(), REGULAR, 10, 195);
            float customerY = detailY - 14;
            for (String customerLine : customerLines) {
                text(customerLine, REGULAR, 10, 210, customerY);
                customerY -= 12;
            }
            text("Fecha", BOLD, 9, 420, detailY);
            text(createdDate(), REGULAR, 10, 420, detailY - 14);
            y -= Math.max(42, 25 + customerLines.size() * 12);

            if (customerContact() != null) {
                paragraph(customerContact(), REGULAR, 9, RIGHT - LEFT, 12);
                y -= 4;
            }
            line(LEFT, y, RIGHT, y);
            y -= 18;
        }

        private void writeItems() throws IOException {
            tableHeader();
            for (QuoteItem item : quote.getItems()) {
                List<String> description = wrap(item.getName(), REGULAR, 9, 242);
                float rowHeight = Math.max(28, description.size() * 12 + 10);
                if (y - rowHeight < BOTTOM) {
                    newPage(true);
                    tableHeader();
                }
                float top = y;
                strokeRectangle(LEFT, top - rowHeight, RIGHT - LEFT, rowHeight);
                line(300, top, 300, top - rowHeight);
                line(345, top, 345, top - rowHeight);
                line(445, top, 445, top - rowHeight);
                float textY = top - 16;
                for (String value : description) {
                    text(value, REGULAR, 9, LEFT + 5, textY);
                    textY -= 12;
                }
                textRight(Integer.toString(item.getQuantity()), REGULAR, 9, 340, top - 16);
                textRight(formatMoney(item.getFinalUnitPrice()), REGULAR, 9, 440, top - 16);
                BigDecimal itemSubtotal = money(item.getFinalUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())));
                textRight(formatMoney(itemSubtotal), REGULAR, 9, RIGHT - 5, top - 16);
                y -= rowHeight;
            }
            y -= 18;
        }

        private void tableHeader() throws IOException {
            ensure(26);
            stream.saveGraphicsState();
            stream.setNonStrokingColor(235 / 255f, 231 / 255f, 246 / 255f);
            stream.addRect(LEFT, y - 24, RIGHT - LEFT, 24);
            stream.fill();
            stream.restoreGraphicsState();
            text("Descripción", BOLD, 9, LEFT + 5, y - 16);
            text("Cant.", BOLD, 9, 306, y - 16);
            text("Precio unitario", BOLD, 8, 352, y - 16);
            text("Subtotal", BOLD, 9, 480, y - 16);
            strokeRectangle(LEFT, y - 24, RIGHT - LEFT, 24);
            y -= 24;
        }

        private void writeTotals() throws IOException {
            ensure(142);
            BigDecimal subtotalAfterDiscount = money(quote.getFinalSubtotal().subtract(quote.getDiscountAmount()));
            BigDecimal deposit = quote.getDepositPercentage() == null ? money(BigDecimal.ZERO)
                    : money(quote.getTotal().multiply(percentage(quote.getDepositPercentage())));
            BigDecimal balance = money(quote.getTotal().subtract(deposit));
            totalLine("Subtotal", quote.getFinalSubtotal(), false);
            String discountLabel = quote.getDiscountPercentage().signum() == 0
                    ? "Descuento" : "Descuento (" + plainPercentage(quote.getDiscountPercentage()) + "%)";
            totalLine(discountLabel, quote.getDiscountAmount().negate(), false);
            totalLine("Subtotal con descuento", subtotalAfterDiscount, false);
            String taxLabel = quote.isTaxEnabled()
                    ? "IVA (" + plainPercentage(quote.getTaxPercentage()) + "%)" : "IVA";
            totalLine(taxLabel, quote.getTaxAmount(), false);
            totalLine("TOTAL", quote.getTotal(), true);
            totalLine("Anticipo", deposit, false);
            totalLine("Saldo", balance, true);
            y -= 12;
        }

        private void writeTerms() throws IOException {
            ensure(55);
            text("CONDICIONES COMERCIALES", BOLD, 11, LEFT, y);
            y -= 18;
            fieldParagraph("Vigencia: ", formatDate(quote.getValidUntil()));
            fieldParagraph("Entrega estimada: ", quote.getEstimatedDeliveryDate() == null
                    ? "Por confirmar" : formatDate(quote.getEstimatedDeliveryDate()));
            if (quote.getDepositPercentage() != null) {
                fieldParagraph("Anticipo requerido: ", plainPercentage(quote.getDepositPercentage()) + "% del total");
            }
            if (quote.getNotes() != null) {
                y -= 5;
                ensure(30);
                text("NOTAS PARA EL CLIENTE", BOLD, 11, LEFT, y);
                y -= 18;
                for (String paragraph : quote.getNotes().split("\\R", -1)) {
                    paragraph(paragraph.isBlank() ? " " : paragraph, REGULAR, 10, RIGHT - LEFT, 14);
                }
            }
        }

        private void fieldParagraph(String label, String value) throws IOException {
            ensure(16);
            text(label + value, REGULAR, 10, LEFT, y);
            y -= 15;
        }

        private void totalLine(String label, BigDecimal amount, boolean emphasized) throws IOException {
            PDFont font = emphasized ? BOLD : REGULAR;
            float size = emphasized ? 11 : 9;
            textRight(label, font, size, 440, y);
            textRight(formatMoney(amount), font, size, RIGHT, y);
            y -= emphasized ? 19 : 16;
        }

        private void paragraph(String value, PDFont font, float size, float width, float leading) throws IOException {
            for (String line : wrap(value, font, size, width)) {
                ensure(leading);
                text(line, font, size, LEFT, y);
                y -= leading;
            }
        }

        private void ensure(float height) throws IOException {
            if (y - height < BOTTOM) {
                newPage(true);
            }
        }

        private void newPage(boolean continuation) throws IOException {
            closeCurrentStream();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            pageNumber++;
            stream.setNonStrokingColor(67 / 255f, 42 / 255f, 101 / 255f);
            stream.addRect(0, 805, PDRectangle.A4.getWidth(), 37);
            stream.fill();
            coloredText("PrintVerse", BOLD, 17, LEFT, 818, 1f, 1f, 1f);
            if (quote.getStatus() == QuoteStatus.DRAFT) {
                coloredText("BORRADOR", BOLD, 12, 470, 820, 1f, 220 / 255f, 220 / 255f);
            }
            text("PrintVerse | Impresión 3D a la medida", REGULAR, 8, LEFT, 30);
            textRight("Página " + pageNumber, REGULAR, 8, RIGHT, 30);
            y = 782;
            if (continuation) {
                text("Cotización " + quote.getQuoteNumber() + " (continuación)", BOLD, 10, LEFT, y);
                y -= 20;
            }
        }

        private void text(String value, PDFont font, float size, float x, float textY) throws IOException {
            coloredText(value, font, size, x, textY,
                    NORMAL_TEXT_COLOR, NORMAL_TEXT_COLOR, NORMAL_TEXT_COLOR);
        }

        private void coloredText(String value, PDFont font, float size, float x, float textY,
                                 float red, float green, float blue) throws IOException {
            stream.setNonStrokingColor(red, green, blue);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, textY);
            stream.showText(sanitize(value, font));
            stream.endText();
        }

        private void textRight(String value, PDFont font, float size, float right, float textY) throws IOException {
            String safe = sanitize(value, font);
            float width = font.getStringWidth(safe) / 1000 * size;
            text(safe, font, size, right - width, textY);
        }

        private void line(float x1, float y1, float x2, float y2) throws IOException {
            stream.setStrokingColor(180 / 255f, 180 / 255f, 180 / 255f);
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        }

        private void strokeRectangle(float x, float rectangleY, float width, float height) throws IOException {
            stream.setStrokingColor(190 / 255f, 190 / 255f, 190 / 255f);
            stream.addRect(x, rectangleY, width, height);
            stream.stroke();
        }

        private List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
            String safe = sanitize(value == null ? "" : value.replace('\n', ' ').replace('\r', ' '), font);
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : safe.trim().split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (stringWidth(candidate, font, size) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    splitLongWord(word, font, size, maxWidth, lines, current);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            if (lines.isEmpty()) {
                lines.add(" ");
            }
            return lines;
        }

        private void splitLongWord(String word, PDFont font, float size, float maxWidth,
                                   List<String> lines, StringBuilder remainder) throws IOException {
            StringBuilder part = new StringBuilder();
            for (int index = 0; index < word.length(); index++) {
                char character = word.charAt(index);
                if (!part.isEmpty() && stringWidth(part.toString() + character, font, size) > maxWidth) {
                    lines.add(part.toString());
                    part.setLength(0);
                }
                part.append(character);
            }
            remainder.append(part);
        }

        private float stringWidth(String value, PDFont font, float size) throws IOException {
            return font.getStringWidth(value) / 1000 * size;
        }

        private String customerName() {
            return quote.getCustomerNameSnapshot() != null
                    ? quote.getCustomerNameSnapshot() : quote.getCustomer().getName();
        }

        private String customerContact() {
            String phone = quote.getCustomerNameSnapshot() != null
                    ? quote.getCustomerPhoneSnapshot() : quote.getCustomer().getPhone();
            String email = quote.getCustomerNameSnapshot() != null
                    ? quote.getCustomerEmailSnapshot() : quote.getCustomer().getEmail();
            if (phone == null && email == null) {
                return null;
            }
            return "Contacto: " + (phone == null ? "" : phone)
                    + (phone != null && email != null ? " | " : "") + (email == null ? "" : email);
        }

        private String createdDate() {
            LocalDate date = quote.getCreatedAt() == null ? LocalDate.now()
                    : quote.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            return formatDate(date);
        }

        private static String formatDate(LocalDate date) {
            return DATE_FORMAT.format(date);
        }

        private static synchronized String formatMoney(BigDecimal value) {
            return MONEY_FORMAT.format(value);
        }

        private static String plainPercentage(BigDecimal value) {
            return value.stripTrailingZeros().toPlainString();
        }

        private static String sanitize(String value, PDFont font) {
            StringBuilder safe = new StringBuilder(value.length());
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                try {
                    font.encode(character);
                    safe.append(character);
                } catch (IOException | IllegalArgumentException exception) {
                    safe.append('?');
                }
                offset += Character.charCount(codePoint);
            }
            return safe.toString();
        }

        private void closeCurrentStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        @Override
        public void close() throws IOException {
            closeCurrentStream();
        }
    }
}
