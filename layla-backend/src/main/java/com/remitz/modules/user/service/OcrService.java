package com.remitz.modules.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Free OCR for the KYC verification workspace, using the Tesseract CLI (with
 * poppler's pdftoppm to rasterize PDFs and ImageMagick to pre-process images).
 * Shells out to the binaries installed in the runtime image — no paid API.
 *
 * Detection on phone photos of IDs is weak with vanilla Tesseract, so each image
 * is first cleaned up (auto-orient, grayscale, upscale, contrast-normalize) and
 * then OCR'd with two page-segmentation modes; the longer result wins. Returns
 * the raw text plus candidate dates and document-number-like tokens.
 */
@Service
@Slf4j
public class OcrService {

    private static final long PROCESS_TIMEOUT_SECONDS = 120;
    private static final int MAX_PDF_PAGES = 3;
    private static final int PDF_DPI = 300;

    private static final Pattern DATE_ISO  = Pattern.compile("\\b\\d{4}[-/]\\d{2}[-/]\\d{2}\\b");
    private static final Pattern DATE_DMY   = Pattern.compile("\\b\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}\\b");
    private static final Pattern DATE_TEXT  = Pattern.compile("\\b\\d{1,2}\\s?[A-Za-z]{3,9}\\s?\\d{2,4}\\b");
    private static final Pattern TOKEN      = Pattern.compile("\\b(?=[A-Z0-9]*\\d)[A-Z0-9]{6,}\\b");

    /** OCR a stored document file. Returns { text, dates[], tokens[] }. */
    public Map<String, Object> extract(String filePath, String fileName) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("text", "");
        out.put("dates", List.of());
        out.put("tokens", List.of());

        if (filePath == null || filePath.isBlank()) return out;
        Path source = Paths.get(filePath);
        if (!Files.exists(source)) {
            log.warn("OCR: file does not exist: {}", filePath);
            return out;
        }

        boolean isPdf = (fileName != null && fileName.toLowerCase().endsWith(".pdf"))
                || filePath.toLowerCase().endsWith(".pdf");

        String text;
        try {
            text = isPdf ? ocrPdf(source) : ocrImage(source);
        } catch (Exception e) {
            log.error("OCR failed for {}: {}", filePath, e.getMessage());
            return out;
        }

        out.put("text", text.trim());
        out.put("dates", findAll(text, DATE_ISO, DATE_DMY, DATE_TEXT));
        out.put("tokens", findAll(text.toUpperCase(), TOKEN));
        log.info("OCR extracted {} chars from {}", text.trim().length(), filePath);
        return out;
    }

    /** Preprocess an image then OCR it, keeping the best of two segmentation modes. */
    private String ocrImage(Path image) throws Exception {
        Path prepped = null;
        try {
            prepped = preprocess(image);
            Path target = prepped != null ? prepped : image;
            // PSM 3 = fully automatic page segmentation; PSM 6 = assume a uniform block
            // (works better on cards). Keep whichever returns more text.
            String a = runTesseract(target, "3");
            String b = runTesseract(target, "6");
            return (b.trim().length() > a.trim().length()) ? b : a;
        } finally {
            if (prepped != null) deleteQuietly(prepped);
        }
    }

    /**
     * ImageMagick clean-up: respect EXIF orientation, drop to grayscale, upscale small
     * scans, and normalize contrast — this is what makes Tesseract actually read photos.
     * Returns the cleaned temp PNG, or null to fall back to the original on failure.
     */
    private Path preprocess(Path image) {
        try {
            Path out = Files.createTempFile("kyc-ocr-prep-", ".png");
            runProcess(List.of("convert", image.toString(),
                    "-auto-orient",
                    "-colorspace", "Gray",
                    "-resize", "x1600<",   // upscale only if shorter than 1600px tall
                    "-normalize",
                    "-sharpen", "0x0.7",
                    out.toString()));
            if (Files.exists(out) && Files.size(out) > 0) return out;
            deleteQuietly(out);
        } catch (Exception e) {
            log.warn("OCR preprocess failed ({}), using original image", e.getMessage());
        }
        return null;
    }

    private String ocrPdf(Path pdf) throws Exception {
        Path tmpDir = Files.createTempDirectory("kyc-ocr-");
        try {
            runProcess(List.of("pdftoppm", "-png", "-r", String.valueOf(PDF_DPI),
                    "-f", "1", "-l", String.valueOf(MAX_PDF_PAGES),
                    pdf.toString(), tmpDir.resolve("page").toString()));
            StringBuilder sb = new StringBuilder();
            try (Stream<Path> pages = Files.list(tmpDir)) {
                List<Path> pngs = pages.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                        .sorted().collect(Collectors.toList());
                for (Path png : pngs) sb.append(ocrImage(png)).append('\n');
            }
            return sb.toString();
        } finally {
            deleteQuietly(tmpDir);
        }
    }

    private String runTesseract(Path image, String psm) throws Exception {
        // --oem 1 = LSTM engine (best quality); stdout prints recognised text.
        return runProcess(List.of("tesseract", image.toString(), "stdout",
                "-l", "eng", "--oem", "1", "--psm", psm));
    }

    private String runProcess(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }
        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("OCR process timed out: " + command.get(0));
        }
        if (process.exitValue() != 0) {
            log.warn("OCR process '{}' exited with code {}", command.get(0), process.exitValue());
        }
        return output.toString();
    }

    @SafeVarargs
    private final List<String> findAll(String text, Pattern... patterns) {
        Set<String> found = new LinkedHashSet<>();
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            while (m.find() && found.size() < 25) found.add(m.group().trim());
        }
        return new ArrayList<>(found);
    }

    private void deleteQuietly(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
