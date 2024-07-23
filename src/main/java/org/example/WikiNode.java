package org.example;

public class WikiNode {
    private final String title;
    private final WikiNode next;
    private final Integer depth;

    public WikiNode(String title, WikiNode next, Integer depth) {
        this.title = title;
        this.next = next;
        this.depth = depth;
    }

    public String getTitle() {
        return title;
    }

    public WikiNode getNext() {
        return next;
    }

    public Integer getDepth() {
        return depth;
    }
}
