package com.zeebot.zeebot.chat;

public class SessionContext {

    private String lastQuery;
    private String lastAnswer;
    private String lastSourceFile;

    public String getLastQuery() {
        return lastQuery;
    }

    public void setLastQuery(String lastQuery) {
        this.lastQuery = lastQuery;
    }

    public String getLastAnswer() {
        return lastAnswer;
    }

    public void setLastAnswer(String lastAnswer) {
        this.lastAnswer = lastAnswer;
    }

    public String getLastSourceFile() {
        return lastSourceFile;
    }

    public void setLastSourceFile(String lastSourceFile) {
        this.lastSourceFile = lastSourceFile;
    }

    public void clear() {
        this.lastQuery = null;
        this.lastAnswer = null;
        this.lastSourceFile = null;
    }

    public boolean hasContext() {
        return lastQuery != null && !lastQuery.isBlank();
    }
}