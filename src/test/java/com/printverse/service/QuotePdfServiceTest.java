package com.printverse.service;

import com.printverse.domain.AdditionalCharge;
import com.printverse.domain.Customer;
import com.printverse.domain.Material;
import com.printverse.domain.Printer;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteItem;
import com.printverse.repository.QuoteRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuotePdfServiceTest {

    private static final int DARK_TEXT = 0x5F5F5F;
    private static final int HEADER_WHITE = 0xFFFFFF;
    private static final int HEADER_DRAFT = 0xFFDCDC;
    private static final int TABLE_BACKGROUND = 0xEBE7F6;

    @Test
    void createsMultipageCommercialPdfWithoutSensitiveData() throws Exception {
        QuoteRepository repository = mock(QuoteRepository.class);
        Quote quote = quote();
        when(repository.findById(10L)).thenReturn(Optional.of(quote));

        QuotePdfService.GeneratedPdf generated = new QuotePdfService(repository).generate(10L);

        assertThat(new String(generated.content(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(generated.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(text).contains("PrintVerse", "PV-2026-SEGURA", "Ana Núñez", "Pieza española",
                     "Precio unitario", "Subtotal", "Descuento", "IVA", "TOTAL", "Anticipo", "Saldo",
                    "BORRADOR", "Proyecto de piezas para exhibicion", "555-0100", "ana@example.com",
                    "Notas visibles al cliente", "$125.50 MXN", "$251.00 MXN", "$13,805.00 MXN",
                    "-$690.25 MXN", "$13,114.75 MXN", "$2,098.36 MXN", "$15,213.11 MXN",
                    "$4,563.93 MXN", "$10,649.18 MXN", "30% del total");
            assertThat(text).doesNotContain("SECRETO-COTIZACION", "SECRETO-CLIENTE",
                    "CARGO-INTERNO-UNICO", "MATERIAL-INTERNO-UNICO", "IMPRESORA-INTERNA-UNICA",
                    "MODELO-INTERNO-UNICO", "987,654.32", "765,432.10", "876,543.21",
                    "654,321.09", "54.321", "markup", "riesgo", "suggestedSubtotal",
                    "estimatedProfit", "realMargin", "materialCost", "machineCost");
        }
    }

    @Test
    void rendersNormalTextDarkAndUsesExplicitContrastingHeaderColors() throws Exception {
        QuoteRepository repository = mock(QuoteRepository.class);
        when(repository.findById(10L)).thenReturn(Optional.of(quote()));

        QuotePdfService.GeneratedPdf generated = new QuotePdfService(repository).generate(10L);

        try (PDDocument document = Loader.loadPDF(generated.content())) {
            ColorAwareTextStripper stripper = new ColorAwareTextStripper();
            stripper.getText(document);

            List<RenderedGlyph> headerGlyphs = stripper.glyphs().stream()
                    .filter(glyph -> glyph.y() < 42)
                    .toList();
            List<RenderedGlyph> normalGlyphs = stripper.glyphs().stream()
                    .filter(glyph -> glyph.y() >= 42)
                    .toList();

            assertThat(headerGlyphs).isNotEmpty();
            assertThat(normalGlyphs).isNotEmpty();
            assertThat(normalGlyphs).allMatch(glyph -> glyph.rgb() == DARK_TEXT);
            assertThat(normalGlyphs).noneMatch(glyph -> glyph.rgb() == TABLE_BACKGROUND);
            assertThat(textWithColor(headerGlyphs, HEADER_WHITE)).contains("PrintVerse");
            assertThat(textWithColor(headerGlyphs, HEADER_DRAFT)).contains("BORRADOR");
            assertThat(headerGlyphs).allMatch(glyph -> glyph.rgb() == HEADER_WHITE || glyph.rgb() == HEADER_DRAFT);
            assertThat(contrastRatio(DARK_TEXT, 0xFFFFFF)).isGreaterThanOrEqualTo(4.5);
            assertThat(contrastRatio(DARK_TEXT, TABLE_BACKGROUND)).isGreaterThanOrEqualTo(4.5);
            assertThat(contrastRatio(HEADER_WHITE, 0x432A65)).isGreaterThanOrEqualTo(4.5);
            assertThat(contrastRatio(HEADER_DRAFT, 0x432A65)).isGreaterThanOrEqualTo(4.5);
        }
    }

    private static Quote quote() {
        Customer customer = new Customer("Ana Núñez", "555-0100", "ana@example.com", "SECRETO-CLIENTE");
        Quote quote = new Quote("PV-2026-SEGURA", customer, LocalDate.now().plusDays(15),
                LocalDate.now().plusDays(7), new BigDecimal("30"), "Notas visibles al cliente",
                new BigDecimal("40"), new BigDecimal("5"), true, new BigDecimal("16"));
        quote.setTitle("Proyecto de piezas para exhibicion");
        quote.setInternalNotes("SECRETO-COTIZACION");
        Material material = new Material("MATERIAL-INTERNO-UNICO", new BigDecimal("987654.32"), true);
        Printer printer = new Printer("IMPRESORA-INTERNA-UNICA", "MODELO-INTERNO-UNICO",
                new BigDecimal("765432.10"), true);
        for (int index = 0; index < 55; index++) {
            QuoteItem item = new QuoteItem("Pieza española de descripción larga número " + index
                    + " para validar el ajuste de texto", 2, material, printer, new BigDecimal("10"),
                    30, new BigDecimal("8"), new BigDecimal("125.50"));
            item.addAdditionalCharge(new AdditionalCharge("CARGO-INTERNO-UNICO", new BigDecimal("4321.09")));
            quote.addItem(item);
        }
        new QuoteCalculationService().recalculate(quote);
        quote.setInternalCost(new BigDecimal("987654.32"));
        quote.setSuggestedSubtotal(new BigDecimal("876543.21"));
        quote.setEstimatedProfit(new BigDecimal("654321.09"));
        quote.setRealMarginPercentage(new BigDecimal("54.3210"));
        return quote;
    }

    private static String textWithColor(List<RenderedGlyph> glyphs, int color) {
        return glyphs.stream()
                .filter(glyph -> glyph.rgb() == color)
                .map(RenderedGlyph::text)
                .reduce("", String::concat);
    }

    private static double contrastRatio(int first, int second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05)
                / (Math.min(firstLuminance, secondLuminance) + 0.05);
    }

    private static double relativeLuminance(int rgb) {
        double red = linearChannel((rgb >> 16) & 0xFF);
        double green = linearChannel((rgb >> 8) & 0xFF);
        double blue = linearChannel(rgb & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearChannel(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private record RenderedGlyph(String text, float y, int rgb) {
    }

    private static final class ColorAwareTextStripper extends PDFTextStripper {
        private final List<RenderedGlyph> glyphs = new ArrayList<>();

        private ColorAwareTextStripper() {
            addOperator(new SetNonStrokingDeviceRGBColor(this));
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            float[] components = getGraphicsState().getNonStrokingColor().getComponents();
            int rgb = Math.round(components[0] * 255) << 16
                    | Math.round(components[1] * 255) << 8
                    | Math.round(components[2] * 255);
            glyphs.add(new RenderedGlyph(text.getUnicode(), text.getYDirAdj(), rgb));
            super.processTextPosition(text);
        }

        private List<RenderedGlyph> glyphs() {
            return glyphs;
        }
    }
}
