package com.zeebot.zeebot.chat;

import com.zeebot.zeebot.service.ChatService;
import com.zeebot.zeebot.service.FileService;
import com.zeebot.zeebot.service.PineconeService;
import com.zeebot.zeebot.service.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

@Component
public class TerminalRunner implements CommandLineRunner {

    @Autowired
    private FileService fileService;

    @Autowired
    private PineconeService pineconeService;

    @Autowired
    private ChatService chatService;

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        SessionContext sessionContext = new SessionContext();
        SessionLogger sessionLogger = new SessionLogger();

        System.out.println("Welcome to ZeeBot!");
        System.out.println("Talk to ZeeBot naturally.");
        System.out.println("Commands:");
        System.out.println("- upload /path/file1.pdf,/path/file2.txt,/path/file3.docx");
        System.out.println("- exit");

        while (true) {
            System.out.print("> ");
            String userInput = scanner.nextLine().trim();

            if (userInput.isEmpty()) {
                System.out.println("Please type a message.");
                continue;
            }

            if (userInput.length() > 500) {
                System.out.println("Your message is too long. Please keep it shorter.");
                continue;
            }

            sessionLogger.logUserMessage(userInput);

            if (userInput.equalsIgnoreCase("exit")) {
                System.out.println("ZeeBot session ended. Goodbye!");
                sessionLogger.saveToFile();
                break;
            }

            if (userInput.startsWith("upload ")) {
                String allPaths = userInput.substring(7).trim();

                if (allPaths.isEmpty()) {
                    System.out.println("Please provide at least one file path.");
                    continue;
                }

                String[] filePaths = allPaths.split(",");
                int successCount = 0;

                for (String rawPath : filePaths) {
                    String filePath = rawPath.trim();

                    if (filePath.isEmpty()) {
                        continue;
                    }

                    try {
                        String result = fileService.uploadFile(filePath);
                        System.out.println(result);

                        if (result.toLowerCase().contains("successfully")) {
                            successCount++;
                        }
                    } catch (Exception e) {
                        System.out.println("Error uploading file: " + filePath);
                        System.out.println(e.getMessage());
                    }
                }

                System.out.println(successCount + " file(s) uploaded and ready for search.");
                continue;
            }

            if (isSmallTalk(userInput)) {
                String reply = "I'm here to help with your uploaded files. Ask me about their content.";
                System.out.println("ZeeBot:");
                System.out.println(reply);
                sessionLogger.logBotMessage(reply, null);
                continue;
            }

            try {
                String finalQuery = pineconeService.buildSearchQuery(userInput, sessionContext);
                List<SearchResult> results = pineconeService.searchChunks(finalQuery);
                String finalAnswer = chatService.generateAnswer(userInput, results);

                System.out.println("ZeeBot:");
                System.out.println(finalAnswer);

                String sourceFile = null;

                boolean hasRealAnswer =
        !results.isEmpty()
        && !"No relevant results found.".equalsIgnoreCase(finalAnswer)
        && !finalAnswer.toLowerCase().contains("does not provide")
        && !finalAnswer.toLowerCase().contains("not available")
        && !finalAnswer.toLowerCase().contains("don't have enough information")
        && !finalAnswer.toLowerCase().contains("not clearly available");

            if (hasRealAnswer) {
                SearchResult best = results.get(0);

                System.out.println();
                System.out.println("Source: " + best.getFileName());

                sourceFile = best.getFileName();

                sessionContext.setLastQuery(userInput);
                sessionContext.setLastAnswer(finalAnswer);
                sessionContext.setLastSourceFile(best.getFileName());
            }

                sessionLogger.logBotMessage(finalAnswer, sourceFile);

            } catch (Exception e) {
                System.out.println("Something went wrong while processing your message.");
                System.out.println(e.getMessage());
            }
        }

        scanner.close();
    }

    private boolean isSmallTalk(String input) {
        String text = input.toLowerCase().trim();

        return text.equals("hi")
                || text.equals("hello")
                || text.equals("hey")
                || text.equals("how are you")
                || text.equals("how are you?")
                || text.equals("thanks")
                || text.equals("thank you")
                || text.equals("good morning")
                || text.equals("good evening");
    }
}