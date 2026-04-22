package com.otectus.runicskills.client.core;

import com.otectus.runicskills.registry.title.Title;

import java.util.LinkedList;

public class TitleQueue {
    LinkedList<Title> list = new LinkedList<>();

    public void enqueue(Title title) {
        this.list.addLast(title);
    }

    public void dequeue() {
        if (!this.list.isEmpty()) this.list.removeFirst();
    }

    public Title peek() {
        return this.list.isEmpty() ? null : this.list.getFirst();
    }

    public int count() {
        return this.list.size();
    }

    public void setList(LinkedList<Title> newList) {
        this.list = newList;
    }
}


