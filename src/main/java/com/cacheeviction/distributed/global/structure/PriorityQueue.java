package com.cacheeviction.distributed.global.structure;

import java.lang.reflect.Array;
import java.util.NoSuchElementException;

public class PriorityQueue<T> {
    private class Node {
        int priority;
        T data;

        Node(int priority, T data) {
            this.priority = priority;
            this.data = data;
        }
    }

    private Node[] heap;
    private int size;
    private int capacity;

    @SuppressWarnings("unchecked")
    public PriorityQueue() {
        capacity = 16;
        heap = (Node[]) Array.newInstance(Node.class, capacity);
        size = 0;
    }

    public void add(T elem, int priority) {
        if (size == capacity) {
            resize();
        }

        heap[size] = new Node(priority, elem);
        int current = size;
        size++;

        while (current > 0 && compare(heap[current], heap[parent(current)]) < 0) {
            swap(current, parent(current));
            current = parent(current);
        }
    }

    public T poll() {
        if (isEmpty()) {
            throw new NoSuchElementException("Fila vazia");
        }
    
        T elem = heap[0].data;
        heap[0] = heap[size - 1];
        size--;
    
        if (size > 0) {
            heapify(0);
        }
    
        return elem;
    }

    public boolean remove(T elem) {
        int idx = findElementIndex(elem);
    
        if (idx == -1) {
            return false;
        }
    
        heap[idx] = heap[size - 1];
        size--;
    
        if (idx < size) {
            heapify(idx);
        }
    
        return true;
    }

    public T search(T element) {
        for (int i = 0; i < size; i++) {
            if (heap[i].data.equals(element)) {
                return heap[i].data;
            }
        }
        return null;
    }

    public T search(int key) {
        for (int i = 0; i < size; i++) {
            if (heap[i].data.equals(key)) {
                return heap[i].data;
            }
        }
        return null;
    }
    
    public int size() {
        return size;
    }

    public int getPriority(T elem) {
        int idx = findElementIndex(elem);

        if (idx == -1) {
            throw new NoSuchElementException("Elemento não encontrado");
        }

        return heap[idx].priority;
    }

    public int increasePriority(T elem) {
        int idx = findElementIndex(elem);
    
        if (idx == -1) {
            throw new NoSuchElementException("Elemento não encontrado");
        }
    
        int oldPriority = heap[idx].priority;

        remove(elem);
        add(elem, oldPriority + 1);

        return oldPriority + 1;
    }

    public void decreasePriority(T elem, int newPriority) {
        int idx = findElementIndex(elem);

        if (idx == -1) {
            throw new NoSuchElementException("Elemento não encontrado");
        }

        int oldPriority = heap[idx].priority;

        if (newPriority < oldPriority) {
            heap[idx].priority = newPriority;

            while (idx > 0 && compare(heap[idx], heap[parent(idx)]) < 0) {
                swap(idx, parent(idx));
                idx = parent(idx);
            }
        } else {
            throw new IllegalArgumentException("A nova prioridade deve ser menor");
        }
    }

    public T peek() {
        if (size == 0) {
            throw new NoSuchElementException("Fila vazia");
        }
        return heap[0].data;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @SuppressWarnings("unchecked")
    public T[] toArray() {
        if (isEmpty()) {
            return null;
        }

        T[] arr = (T[]) Array.newInstance(heap[0].data.getClass(), size);
        
        for (int i = 0; i < size; i++) {
            arr[i] = heap[i].data;
        }

        return arr;
    }

    private int compare(Node a, Node b) {
        return a.priority - b.priority;
    }

    private int findElementIndex(T elem) {
        for (int i = 0; i < size; i++) {
            if (heap[i].data.equals(elem)) {
                return i;
            }
        }
        return -1;
    }

    private void swap(int i, int j) {
        Node temp = heap[i];
        heap[i] = heap[j];
        heap[j] = temp;
    }

    private void heapify(int idx) {
        int left = left(idx);
        int right = right(idx);
        int smallest = idx;

        if (left < size && compare(heap[left], heap[smallest]) < 0) {
            smallest = left;
        }

        if (right < size && compare(heap[right], heap[smallest]) < 0) {
            smallest = right;
        }

        if (smallest != idx) {
            swap(idx, smallest);
            heapify(smallest);
        }
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        capacity *= 2;
        Node[] newHeap = (Node[]) Array.newInstance(Node.class, capacity);

        if (size >= 0) System.arraycopy(heap, 0, newHeap, 0, size);

        heap = newHeap;
    }

    private int parent(int i) {
        return (i - 1) / 2;
    }

    private int left(int i) {
        return 2 * i + 1;
    }

    private int right(int i) {
        return 2 * i + 2;
    }

}
