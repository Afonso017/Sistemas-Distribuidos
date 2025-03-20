package com.cacheeviction.distributed.global.structure;

import java.lang.reflect.Array;

public class HashMap<K, V> {

    private class Node {
        K key;
        V value;
        Node next;

        Node(K key, V value, Node next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Node[] table;
    private int size;
    private int elements = 0;

    public HashMap() {
        this(16);
    }

    @SuppressWarnings("unchecked")
    public HashMap(int size) {
        if (size < 2) {
            throw new IllegalArgumentException("Tamanho inválido para tabela hash");
        }

        int power = Integer.highestOneBit(size - 1) << 1;
        this.size = largestNearestPrime(power);

        table = (Node[]) Array.newInstance(Node.class, this.size);
    }

    public V get(K key) {
        int idx = hash(key);
        Node current = table[idx];

        while (current != null) {
            if (current.key.equals(key)) {
                return current.value;
            }
            current = current.next;
        }

        return null;
    }

    public void put(K key, V value) {
        checkLoad();

        int idx = hash(key);
        Node node = new Node(key, value, null);

        if (table[idx] == null) {
            table[idx] = node;
        } else {
            Node current = table[idx];
            while (current.next != null) {
                current = current.next;
            }
            current.next = node;
        }

        elements++;
    }

    public V remove(K key) {
        int idx = hash(key);
        Node current = table[idx];
        Node prev = null;

        while (current != null) {
            if (current.key.equals(key)) {
                if (prev == null) {
                    table[idx] = current.next;
                } else {
                    prev.next = current.next;
                }
                elements--;
                return current.value;
            }
            prev = current;
            current = current.next;
        }

        return null;
    }

    private int hash(K key) {
        return (key.hashCode() & Integer.MAX_VALUE) % size;
    }

    private void checkLoad() {
        if (elements >= size) {
            resize();
        }
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Node[] oldTable = table;
        size *= 2;
        table = (Node[]) Array.newInstance(Node.class, size);

        for (Node node : oldTable) {
            while (node != null) {
                Node next = node.next;
                int idx = hash(node.key);
                node.next = table[idx];
                table[idx] = node;
                node = next;
            }
        }
    }

    private int largestNearestPrime(int n) {
        while (!isPrime(n)) {
            n++;
        }
        return n;
    }

    private boolean isPrime(int n) {
        if (n < 2) {
            return false;
        }
        for (int i = 2; i <= Math.sqrt(n); i++) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }
}
