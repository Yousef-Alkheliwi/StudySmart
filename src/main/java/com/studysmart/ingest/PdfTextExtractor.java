package com.studysmart.ingest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Extracts text page-by-page from a PDF so every chunk can cite an exact page number. */
@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String contentType, String filename) {
        return "application/pdf".equalsIgnoreCase(contentType)
                || (filename != null && filename.toLowerCase().endsWith(".pdf"));
    }

    @Override
    public List<ExtractedPage> extract(Path file) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                pages.add(new ExtractedPage(i, text));
            }
        }
        return pages;
    }
}
