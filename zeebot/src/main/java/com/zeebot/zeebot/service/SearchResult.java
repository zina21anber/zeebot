package com.zeebot.zeebot.service;

public class SearchResult {

    private final String fileName;
    private final String chunkText;
    private final double score;
    private final Integer chunkIndex;

    public SearchResult(String fileName, String chunkText, double score, Integer chunkIndex) {
        this.fileName = fileName;
        this.chunkText = chunkText;
        this.score = score;
        this.chunkIndex = chunkIndex;
    }

    public String getFileName() {
        return fileName;
    }

    public String getChunkText() {
        return chunkText;
    }

    public double getScore() {
        return score;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }
}