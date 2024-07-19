package org.example;

import lombok.AllArgsConstructor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.join;


public class SingleThreadCrawler {

    public static void main(String[] args) throws Exception {
        SingleThreadCrawler crawler = new SingleThreadCrawler();

        long startTime = System.nanoTime();
        String result = crawler.find("Java_(programming_language)", "Quantification_(science)", 5, TimeUnit.MINUTES);
        long finishTime = TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

        System.out.println("Took " + finishTime + " seconds, result is: " + result);
    }

    private final PriorityBlockingQueue<Node> searchQueue = new PriorityBlockingQueue<>();

    private final Set<String> visited = new HashSet<>();

    private final WikiClient client = new WikiClient();

    private final Lock lock = new ReentrantLock();

    public String find(String from, String target, long timeout, TimeUnit timeUnit) throws Exception {
        long deadline = System.nanoTime() + timeUnit.toNanos(timeout);
        searchQueue.add(new Node(from, null, 0));

        AtomicReference<Node> result = new AtomicReference<>(null);

        for (int index = 0; index < 10; index++) {
            Thread thread = new Thread(new Task(deadline, target, result, this));
            thread.start();
        }

        synchronized (this) {
            while(result.get() == null) {
                wait();
            }
        }

        List<String> resultList = new ArrayList<>();
        Node search = result.get();
        while (true) {
            resultList.add(search.title);
            if (search.next == null) {
                break;
            }
            search = search.next;
        }
        Collections.reverse(resultList);

        return join(" > ", resultList);
    }

    private class Task implements Runnable {
        private final long deadline;
        private final String target;
        private final AtomicReference<Node> result;
        private final SingleThreadCrawler starter;

        public Task(long deadline, String target, AtomicReference<Node> result, SingleThreadCrawler starter) {
            this.deadline = deadline;
            this.target = target;
            this.result = result;
            this.starter = starter;
        }

        @Override
        public void run() {
            try {
                while (result.get() == null) {
                    if (deadline < System.nanoTime()) {
                        throw new TimeoutException();
                    }
                    Node node = searchQueue.take();

                    Set<String> links;
                    links = client.getByTitle(node.title);
                    if (links.isEmpty()) {
                        //pageNotFound
                        return;
                    }
                    for (String link : links) {
                        Node subNode = new Node(link, node, node.depth + 1);
                        if (target.equalsIgnoreCase(link)) {
                            result.set(subNode);
                            synchronized (starter) {
                                starter.notify();
                            }
                            return;
                        }

                        String currentLink = link.toLowerCase();
                        try {
                            lock.lock();
                            if (visited.contains(currentLink)) {
                                continue;
                            }
                            visited.add(currentLink);

                        } finally {
                            lock.unlock();
                        }

                        searchQueue.add(subNode);
                    }
                }
            } catch (IOException | TimeoutException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @AllArgsConstructor
    private static class Node implements Comparable<Node> {
        String title;
        Node next;
        Integer depth;

        @Override
        public int compareTo(Node o) {
            return Integer.compare(this.depth, o != null ? o.depth : 0);
        }
    }
}
