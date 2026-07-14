package com.zeebot.zeebot.service;

import com.zeebot.zeebot.chat.SessionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PineconeService {

    @Value("${pinecone.api-key}")
    private String apiKey;

    @Value("${pinecone.index-host}")
    private String indexHost;

    @Value("${pinecone.namespace}")
    private String namespace;

    private static final double MIN_SCORE = 0.75;

    public void upsertChunks(String fileName, String filePath, List<String> chunks) throws IOException {
        URL url = java.net.URI.create(indexHost + "/records/namespaces/" + namespace + "/upsert").toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Api-Key", apiKey);
        connection.setRequestProperty("Content-Type", "application/x-ndjson");
        connection.setDoOutput(true);

        String requestBody = buildNdjsonBody(fileName, filePath, chunks);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();

        if (responseCode != 200 && responseCode != 201) {
            String errorResponse = readResponse(connection.getErrorStream());
            throw new RuntimeException("Failed to upsert chunks to Pinecone. Response code: "
                    + responseCode + " Response: " + errorResponse);
        }
    }

    public List<SearchResult> searchChunks(String query) throws IOException {
        URL url = java.net.URI.create(indexHost + "/records/namespaces/" + namespace + "/search").toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Api-Key", apiKey);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String requestBody = "{"
                + "\"query\": {"
                + "\"top_k\": 5,"
                + "\"inputs\": {\"text\": \"" + escapeJson(query) + "\"}"
                + "},"
                + "\"fields\": [\"chunk_text\", \"fileName\", \"filePath\", \"chunkIndex\"]"
                + "}";

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        String response;

        if (responseCode == 200) {
            response = readResponse(connection.getInputStream());
        } else {
            response = readResponse(connection.getErrorStream());
            throw new RuntimeException("Pinecone search failed. Response code: "
                    + responseCode + " Response: " + response);
        }

        return extractMatchesAsObjects(response);
    }

    public String buildSearchQuery(String userMessage, SessionContext context) {
        String cleaned = userMessage == null ? "" : userMessage.trim();

        if (cleaned.isEmpty()) {
            return "";
        }

        if (!isFollowUp(cleaned) || context == null || !context.hasContext()) {
            return cleaned;
        }

        return context.getLastQuery() + " " + cleaned;
    }

    public String buildFinalAnswer(List<SearchResult> results, String originalQuery) {
        if (results == null || results.isEmpty()) {
            return "No relevant results found.";
        }

        SearchResult best = results.get(0);
        String text = cleanText(best.getChunkText());

        if (!looksRelevant(originalQuery, text)) {
            return "No relevant results found.";
        }

        String extracted = extractBestAnswer(originalQuery, text);

        if (extracted == null || extracted.isBlank()) {
            return makePreview(text, 220);
        }

        return makePreview(extracted, 220);
    }

    private String extractBestAnswer(String query, String text) {
        String normalizedText = text.replaceAll("\\s+", " ").trim();

        String[] candidates = normalizedText.split("(?<=[.!?])\\s+|\\s+-\\s+");
        String bestCandidate = "";
        int bestScore = -1;

        Set<String> queryWords = tokenize(query);

        for (String candidate : candidates) {
            String cleanedCandidate = candidate.trim();

            if (cleanedCandidate.length() < 8) {
                continue;
            }

            int score = scoreCandidate(queryWords, cleanedCandidate);

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = cleanedCandidate;
            }
        }

        if (bestScore <= 0) {
            return normalizedText;
        }

        return bestCandidate;
    }

    private int scoreCandidate(Set<String> queryWords, String candidate) {
        Set<String> candidateWords = tokenize(candidate);
        int score = 0;

        for (String word : queryWords) {
            if (candidateWords.contains(word)) {
                score += 2;
            }
        }

        String lowerCandidate = candidate.toLowerCase();

        if (lowerCandidate.contains("king saud university")) {
            score += 1;
        }

        if (lowerCandidate.contains("department")
                || lowerCandidate.contains("tools")
                || lowerCandidate.contains("training")
                || lowerCandidate.contains("report")) {
            score += 1;
        }

        return score;
    }

    private boolean isFollowUp(String text) {
        String lower = text.toLowerCase().trim();

        return lower.startsWith("and ")
                || lower.startsWith("what about")
                || lower.startsWith("how about")
                || lower.startsWith("what else")
                || lower.startsWith("tell me more")
                || lower.startsWith("then")
                || lower.startsWith("also")
                || lower.startsWith("about that")
                || lower.startsWith("that one")
                || lower.startsWith("this one")
                || lower.startsWith("the department")
                || lower.startsWith("the company");
    }

    private boolean looksRelevant(String query, String text) {
        if (query == null || query.isBlank() || text == null || text.isBlank()) {
            return false;
        }

        Set<String> queryWords = tokenize(query);
        Set<String> textWords = tokenize(text);

        int matches = 0;
        for (String word : queryWords) {
            if (textWords.contains(word)) {
                matches++;
            }
        }

        return matches >= 1;
    }

    private Set<String> tokenize(String input) {
        String[] raw = input.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .split("\\s+");

        Set<String> words = new HashSet<>();
        for (String word : raw) {
            if (word.length() >= 3 && !isStopWord(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private boolean isStopWord(String word) {
        return word.equals("the") || word.equals("and") || word.equals("for")
                || word.equals("are") || word.equals("with") || word.equals("that")
                || word.equals("this") || word.equals("what") || word.equals("about")
                || word.equals("have") || word.equals("from") || word.equals("your")
                || word.equals("into") || word.equals("than") || word.equals("then")
                || word.equals("name") || word.equals("tell");
    }

    private List<SearchResult> extractMatchesAsObjects(String response) {
        List<SearchResult> results = new ArrayList<>();

        String[] hits = response.split("\\{\"_id\":\"");

        for (int i = 1; i < hits.length; i++) {
            String hit = hits[i];

            String scoreText = extractNumericField(hit, "\"_score\":");
            String fileName = extractFieldFromFields(hit, "\"fileName\":\"");
            String chunkText = extractFieldFromFields(hit, "\"chunk_text\":\"");
            String chunkIndexText = extractNumericField(hit, "\"chunkIndex\":");

            if (fileName == null || chunkText == null || scoreText == null) {
                continue;
            }

            double score;
            try {
                score = Double.parseDouble(scoreText);
            } catch (NumberFormatException e) {
                continue;
            }

            if (score < MIN_SCORE) {
                continue;
            }

            String cleanedText = cleanText(chunkText);

            Integer chunkIndex = null;
            if (chunkIndexText != null) {
                try {
                    chunkIndex = Integer.parseInt(chunkIndexText);
                } catch (NumberFormatException ignored) {
                }
            }

            results.add(new SearchResult(fileName, cleanedText, score, chunkIndex));
        }

        results.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
        return results;
    }

    private String makePreview(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = text.replaceAll("\\s+", " ").trim();

        if (cleaned.length() <= maxLength) {
            return cleaned;
        }

        int cutIndex = cleaned.lastIndexOf(' ', maxLength);
        if (cutIndex < 0) {
            cutIndex = maxLength;
        }

        return cleaned.substring(0, cutIndex).trim() + "...";
    }

    private String extractFieldFromFields(String text, String token) {
        int start = text.indexOf(token);
        if (start == -1) return null;
        start += token.length();

        StringBuilder value = new StringBuilder();
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                value.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                break;
            }

            value.append(c);
        }

        return value.toString();
    }

    private String extractNumericField(String text, String token) {
        int start = text.indexOf(token);
        if (start == -1) return null;
        start += token.length();

        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (!(Character.isDigit(c) || c == '.' || c == '-')) {
                break;
            }
            end++;
        }

        if (start == end) return null;
        return text.substring(start, end);
    }

    private String cleanText(String text) {
        return text.replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildNdjsonBody(String fileName, String filePath, List<String> chunks) {
        StringBuilder body = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            body.append("{")
                    .append("\"_id\":\"").append(fileName.replaceAll("[^a-zA-Z0-9]", "_")).append("_").append(i).append("\",")
                    .append("\"chunk_text\":\"").append(escapeJson(chunks.get(i))).append("\",")
                    .append("\"fileName\":\"").append(escapeJson(fileName)).append("\",")
                    .append("\"filePath\":\"").append(escapeJson(filePath)).append("\",")
                    .append("\"chunkIndex\":").append(i)
                    .append("}")
                    .append("\n");
        }

        return body.toString();
    }

    private String readResponse(InputStream stream) throws IOException {
        if (stream == null) return "";

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}