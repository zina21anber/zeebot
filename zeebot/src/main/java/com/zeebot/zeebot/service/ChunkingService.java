package com.zeebot.zeebot.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    public List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String cleaned = text.replaceAll("\\s+", " ").trim();
        int start = 0;

        while (start < cleaned.length()) {
            int end = Math.min(cleaned.length(), start + chunkSize);
            chunks.add(cleaned.substring(start, end));

            if (end == cleaned.length()) {
                break;
            }

            start = end - overlap;
        }

        return chunks;
    }
}