package com.cacheeviction.distributed.global.server;

import com.cacheeviction.distributed.global.structure.PriorityQueue;

public class Cache<T> {

    private final PriorityQueue<T> cache;
    private final Logger log;
    private final int maxSize;
    private int hit;
    private int miss;

    public Cache(String location, int maxSize) {
        cache = new PriorityQueue<>();
        this.maxSize = maxSize;
        log = new Logger("cacheLog" + location + ".txt", true);
    }

    public void add(T element) {
        // verifica se o elemento já existe e aumenta a prioridade
        if (cache.search(element) != null) {
            int priority = cache.increasePriority(element);
            log("Prioridade do elemento " + element + " aumentada para " + priority + ".");
        } else {
            if (cache.size() == maxSize) {
                T e = cache.poll();  // remove o elemento com menor prioridade
                log("Cache cheia. Elemento " + e + " removido.");
            }

            cache.add(element, 0);
            log("Elemento " + element + " adicionado à cache com prioridade 0.");
        }

        listAll();
    }

    public T search(T element) {
        T foundElement = cache.search(element);
        
        if (foundElement != null) {
            hit++;
            log("Elemento " + element + " encontrado na cache.");
            listAll();
            return foundElement;
        } else {
            miss++;
            log("Elemento " + element + " não encontrado na cache.");
            listAll();
            return null;
        }
    }

    public T search(int key) {
        T foundElement = cache.search(key);

        if (foundElement != null) {
            hit++;
            log("Elemento com chave " + key + " encontrado na cache.");
            listAll();
            return foundElement;
        } else {
            miss++;
            log("Elemento com chave " + key + " não encontrado na cache.");
            listAll();
            return null;
        }
    }

    public void remove(T element) {
        if (cache.remove(element)) {
            log("Elemento " + element + " removido da cache.");
        } else {
            log("Elemento " + element + " não encontrado na cache para remoção.");
        }
        listAll();
    }

    public void listAll() {
        StringBuilder sb = new StringBuilder("Cache:\n");
        T[] elements = cache.toArray();
        
        if (elements == null) {
            sb.append("Cache está vazia.\n");
        } else {
            int i = 1;
            for (T e : elements) {
                sb.append("[").append(i++).append("] ").append(e.toString())
                    .append(" (Prioridade: ").append(cache.getPriority(e)).append(")\n");
            }
            log.append(sb.toString());
        }
    }

    public void increasePriority(T element) {
        log("Prioridade do elemento " + element + " aumentada para " + cache.increasePriority(element) + ".");
        listAll();
    }

    public int hits() {
        return hit;
    }

    public int misses() {
        return miss;
    }

    public int size() {
        return cache.size();
    }

    private void log(String message) {
        log.append(message);
        log.append("Cache hits = " + hit);
        log.append("Cache misses = " + miss + "\n");
    }
}
