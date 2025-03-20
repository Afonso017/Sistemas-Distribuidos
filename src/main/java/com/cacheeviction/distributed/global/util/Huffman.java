package com.cacheeviction.distributed.global.util;

import com.cacheeviction.distributed.global.structure.HashMap;
import com.cacheeviction.distributed.global.structure.PriorityQueue;

public class Huffman {

    private static class Node {
        int freq;
        char c;
        Node left, right;

        Node(int freq, char c) {
            this.freq = freq;
            this.c = c;
            left = right = null;
        }
    }

    private Node root;
    private final HashMap<Character, String> huffmanCodes;

    public Huffman() {
        root = null;
        huffmanCodes = new HashMap<>();
    }

    public void buildHuffmanTree(String s) {
        int[] freq = new int[256];
        for (int i = 0; i < s.length(); i++) {
            freq[s.charAt(i)]++;
        }

        PriorityQueue<Node> pq = new PriorityQueue<>();

        for (char ch : s.toCharArray()) {
            if (freq[ch] > 0) {
                pq.add(new Node(freq[ch], ch), freq[ch]);
            }
        }

        while (pq.size() > 1) {
            Node left = pq.poll();
            Node right = pq.poll();
            Node parent = new Node(left.freq + right.freq, '-');
            parent.left = left;
            parent.right = right;
            pq.add(parent, parent.freq);
        }

        root = pq.poll();

        generateHuffmanCodes(root, "");
    }

    private void generateHuffmanCodes(Node node, String code) {
        if (node == null) {
            return;
        }

        if (node.left == null && node.right == null) {
            huffmanCodes.put(node.c, code);
        }

        generateHuffmanCodes(node.left, code + "0");
        generateHuffmanCodes(node.right, code + "1");
    }

    public String encodeMessage(String message) {
        StringBuilder encodedMessage = new StringBuilder();

        for (char c : message.toCharArray()) {
            encodedMessage.append(huffmanCodes.get(c));
        }

        return encodedMessage.toString();
    }

    public String decode(String encodedMessage) {
        StringBuilder decodedMessage = new StringBuilder();
        Node current = root;

        for (int i = 0; i < encodedMessage.length(); i++) {
            char bit = encodedMessage.charAt(i);

            if (current != null) {
                if (bit == '0') {
                    current = current.left;
                } else {
                    current = current.right;
                }
            }

            if (current != null && current.left == null && current.right == null) {
                decodedMessage.append(current.c);
                current = root;
            }
        }

        return decodedMessage.toString();
    }

    public HashMap<Character, String> getHuffmanCodes() {
        return huffmanCodes;
    }
}
