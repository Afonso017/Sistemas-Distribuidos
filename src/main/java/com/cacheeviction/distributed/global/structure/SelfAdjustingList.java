package com.cacheeviction.distributed.global.structure;

import java.lang.reflect.Array;

public class SelfAdjustingList<T> {

    class Node {
        T data;
        Node next;

        Node(T data) {
            this.data = data;
            this.next = null;
        }
    }

    private Node head;
    private int size;

    public SelfAdjustingList() {
        head = null;
        size = 0;
    }

    public boolean add(T data) {
        Node newNode = new Node(data);
        newNode.next = head;
        head = newNode;
        size++;
        return true;
    }

    public T search(T data) {
        if (head == null) {
            return null;
        }

        if (head.data.equals(data)) {
            return head.data;
        }

        Node prev = head;
        Node current = head.next;

        while (current != null) {
            if (current.data.equals(data)) {
                prev.next = current.next; // move para frente
                current.next = head;
                head = current;
                return current.data;
            }
            prev = current;
            current = current.next;
        }

        return null;
    }

    public int size() {
        return size;
    }

    public boolean remove(Object data) {
        if (head == null) {
            return false;
        }

        if (head.data.equals(data)) {
            head = head.next;
            size--;
            return true;
        }

        Node prev = head;
        Node current = head.next;

        while (current != null) {
            if (current.data.equals(data)) {
                prev.next = current.next;
                size--;
                return true;
            }
            prev = current;
            current = current.next;
        }
        return false;
    }

    public void show() {
        Node current = head;

        while (current != null) {
            System.out.print(current.data + " ");
            current = current.next;
        }
        System.out.println();
    }

    @SuppressWarnings("unchecked")
    public T[] toArray(T[] a) {
        if (a.length < size) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
        }

        Node current = head;
        int i = 0;

        while (current != null) {
            a[i++] = current.data;
            current = current.next;
        }

        if (a.length < size) {
            return (T[]) Array.newInstance(a.getClass().getComponentType(), size);
        }        

        return a;
    }

    public Node getHead() {
        return head;
    }

}
