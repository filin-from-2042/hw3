package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WikiClient {

    public static final String WIKI = "/wiki/";
    public static final String EN_WIKI_URL = "https://en.wikipedia.org" + WIKI;
    private long lastRequest;
    private final long RATE_LIMITER = TimeUnit.NANOSECONDS.convert(200, TimeUnit.MILLISECONDS);
    private final Lock lock = new ReentrantLock();

    public Set<String> getByTitle(String title) throws IOException, InterruptedException {
        checkRateLimit();

        System.out.println("Get page: " + title);
        Set<String> links = new HashSet<>();
        String url = EN_WIKI_URL + title;
        try {
            Document page = Jsoup.connect(url).timeout(10_000).followRedirects(true).get();
            for (Element element : page.body().select("a")) {

                if (element.hasAttr("href")) {
                    String href = element.attr("href");
                    if (href.startsWith(WIKI) && !element.text().isEmpty() && !href.contains(":")) {
                        links.add(href.substring(WIKI.length()));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(title + " got error: " + e.getMessage());
        }
        return links;
    }

    private void checkRateLimit(){
        while(true){
            lock.lock();
            try {
                if(lastRequest == 0L){
                    lastRequest = System.nanoTime();
                }
                long leftAfterRequest = System.nanoTime() - lastRequest;
                if (leftAfterRequest < RATE_LIMITER) {
                    lock.unlock();
                    continue;
                }
                lastRequest = System.nanoTime();
                break;
            } catch (Exception exception){
                lock.unlock();
            }
        }
    }
}

