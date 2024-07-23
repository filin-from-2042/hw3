package org.example;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.join;


public class MultiThreadCrawler {

    public static void main(String[] args) throws Exception {
        MultiThreadCrawler crawler = new MultiThreadCrawler();

        long startTime = System.nanoTime();
        String result = crawler.find("Java_(programming_language)", "TiVo_Inc.", 5, TimeUnit.MINUTES);
        long finishTime = TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

        System.out.println("Took " + finishTime + " seconds, result is: " + result);
    }

    private final VisitPriorityQueue searchQueue = new VisitPriorityQueue();

    private final WikiClient client = new WikiClient();

    public String find(String from, String target, long timeout, TimeUnit timeUnit) throws Exception {
        long deadline = System.nanoTime() + timeUnit.toNanos(timeout);
        searchQueue.enq(new WikiNode(from, null, 0));

        AtomicReference<WikiNode> result = new AtomicReference<>(null);

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
        WikiNode search = result.get();
        while (true) {
            resultList.add(search.getTitle());
            if (search.getNext() == null) {
                break;
            }
            search = search.getNext();
        }
        Collections.reverse(resultList);

        return join(" > ", resultList);
    }

    private class Task implements Runnable {
        private final long deadline;
        private final String target;
        private final AtomicReference<WikiNode> result;
        private final MultiThreadCrawler starter;

        public Task(long deadline, String target, AtomicReference<WikiNode> result, MultiThreadCrawler starter) {
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
                    WikiNode node = searchQueue.deq();

                    Set<String> links;
                    links = client.getByTitle(node.getTitle());
                    if (links.isEmpty()) {
                        //pageNotFound
                        return;
                    }
                    for (String link : links) {
                        WikiNode subNode = new WikiNode(link, node, node.getDepth() + 1);
                        if (target.equalsIgnoreCase(link)) {
                            result.set(subNode);
                            synchronized (starter) {
                                starter.notify();
                            }
                            return;
                        }

                        searchQueue.enq(subNode);
                    }
                }
            } catch (IOException | TimeoutException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
