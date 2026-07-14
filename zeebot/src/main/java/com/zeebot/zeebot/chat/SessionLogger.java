package com.zeebot.zeebot.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SessionLogger {

    private final StringBuilder content = new StringBuilder();
    private final LocalDateTime startTime;

    public SessionLogger() {
        this.startTime = LocalDateTime.now();
    }

    public void logUserMessage(String message) {
        content.append("User: ").append(message).append("\n");
    }

    public void logBotMessage(String answer, String sourceFile) {
        content.append("ZeeBot: ").append(answer).append("\n");

        if (sourceFile != null && !sourceFile.isBlank()) {
            content.append("Source File: ").append(sourceFile).append("\n");
        }

        content.append("\n");
    }

    public void saveToFile() {
        try {
            Path sessionsDir = Paths.get("sessions");
            Files.createDirectories(sessionsDir);

            LocalDateTime endTime = LocalDateTime.now();

            DateTimeFormatter fileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

            String fileName = "zeebot-session-" + endTime.format(fileFormatter) + ".txt";
            Path filePath = sessionsDir.resolve(fileName);

            String finalContent =
                    "ZeeBot Session\n" +
                    "Date: " + LocalDate.now() + "\n" +
                    "Start Time: " + startTime.format(timeFormatter) + "\n" +
                    "End Time: " + endTime.format(timeFormatter) + "\n\n" +
                    content;

            Files.writeString(
                    filePath,
                    finalContent,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            System.out.println("Session saved to: " + filePath.toAbsolutePath());

        } catch (IOException e) {
            System.out.println("Failed to save session file.");
            System.out.println(e.getMessage());
        }
    }
}