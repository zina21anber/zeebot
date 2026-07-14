package com.zeebot.zeebot.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileService {

    private final ChunkingService chunkingService;
    private final PineconeService pineconeService;

    private final Map<String, String> uploadedFileTexts = new HashMap<>();

    public FileService(ChunkingService chunkingService, PineconeService pineconeService) {
        this.chunkingService = chunkingService;
        this.pineconeService = pineconeService;
    }

    public boolean fileExists(String filePath) {
        Path path = Paths.get(filePath);
        return Files.exists(path) && Files.isRegularFile(path);
    }

    public String fileType(String filePath) {
        String lowerPath = filePath.toLowerCase();

        if (lowerPath.endsWith(".pdf")) return "pdf";
        if (lowerPath.endsWith(".txt")) return "txt";
        if (lowerPath.endsWith(".docx")) return "docx";

        return "unsupported";
    }

    public boolean isSupportedFileType(String filePath) {
        return !fileType(filePath).equals("unsupported");
    }

    public String extractTextFromPdf(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper pdfTextStripper = new PDFTextStripper();
            return pdfTextStripper.getText(document);
        }
    }

    public String extractTextFromTxt(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readString(path);
    }

    public String extractTextFromDocx(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            return extractor.getText();
        }
    }

    public String extractTextByFileType(String filePath) throws IOException {
        String type = fileType(filePath);

        switch (type) {
            case "pdf":
                return extractTextFromPdf(filePath);
            case "txt":
                return extractTextFromTxt(filePath);
            case "docx":
                return extractTextFromDocx(filePath);
            default:
                throw new IllegalArgumentException("Unsupported file type.");
        }
    }

    public void saveExtractedText(String filePath, String extractedText) {
        uploadedFileTexts.put(filePath, extractedText);
    }

    public String uploadFile(String filePath) {
        try {
            if (!fileExists(filePath)) {
                return "File does not exist.";
            }

            if (!isSupportedFileType(filePath)) {
                return "Unsupported file type.";
            }

            String extractedText = extractTextByFileType(filePath);

            if (extractedText == null || extractedText.trim().isEmpty()) {
                return "File is empty or text extraction failed.";
            }

            saveExtractedText(filePath, extractedText);

            List<String> chunks = chunkingService.chunkText(extractedText, 800, 100);
            String fileName = Paths.get(filePath).getFileName().toString();

            pineconeService.upsertChunks(fileName, filePath, chunks);

            return "File uploaded and indexed successfully: " + fileName;

        } catch (IOException e) {
            return "Error processing file: " + e.getMessage();
        } catch (Exception e) {
            return "Unexpected error: " + e.getMessage();
        }
    }

    public boolean hasUploadedFiles() {
        return !uploadedFileTexts.isEmpty();
    }

    public String searchInUploadedTexts(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        StringBuilder results = new StringBuilder();

        for (Map.Entry<String, String> entry : uploadedFileTexts.entrySet()) {
            String filePath = entry.getKey();
            String content = entry.getValue();

            if (content != null && content.toLowerCase().contains(lowerKeyword)) {
                String fileName = Paths.get(filePath).getFileName().toString();
                results.append("Found in file: ").append(fileName).append("\n");
            }
        }

        if (results.length() == 0) {
            return "No match found in uploaded files.";
        }

        return results.toString();
    }
}