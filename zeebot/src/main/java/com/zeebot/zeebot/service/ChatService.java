package com.zeebot.zeebot.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateAnswer(String userQuestion, List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "No relevant results found.";
        }

        String context = buildContext(results);

        String prompt = """
                You are ZeeBot, a terminal-based RAG assistant.

                Your task:
                Answer the user's question using ONLY the retrieved context below.

                Rules:
                - Use only the retrieved context.
                - Do not use outside knowledge.
                - If the answer is not clearly available in the context, send an appropriate message. 
                - Keep the answer short, direct, and natural.
                - Do not quote large chunks unless necessary.
                - Prefer one or two sentences maximum.
                - If the question is a follow-up, answer based only on the retrieved context provided now.

                User question:
                %s

                Retrieved context:
                %s
                """.formatted(userQuestion, context);

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        if (response == null || response.isBlank()) {
            return "No relevant results found.";
        }

        return response.trim();
    }

    private String buildContext(List<SearchResult> results) {
        StringBuilder builder = new StringBuilder();

        int limit = Math.min(results.size(), 3);

        for (int i = 0; i < limit; i++) {
            SearchResult result = results.get(i);

            builder.append("Source file: ")
                    .append(result.getFileName());

            if (result.getChunkIndex() != null) {
                builder.append(" | Chunk: ").append(result.getChunkIndex());
            }

            builder.append("\n");
            builder.append(result.getChunkText()).append("\n\n");
        }

        return builder.toString().trim();
    }
}