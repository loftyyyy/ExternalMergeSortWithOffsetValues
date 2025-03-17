package com.example.testfx.externalsort;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Run {
    private File file;
    private ExternalSort externalSort; // Add this field

    Run(File file, ExternalSort externalSort) {
        this.file = file;
        this.externalSort = externalSort;
    }

    Run(ExternalSort externalSort) throws IOException {
        this.file = File.createTempFile("externalsort", ".tmp");
        this.file.deleteOnExit();
        this.externalSort = externalSort;
    }
    // In Run.java, add another constructor variant
Run(File file, ExternalSortWithOffsetValues externalSort) {
    this.file = file;
    this.externalSort = externalSort; // This might need adjustment based on your code structure
}

// And add another constructor for no-args
Run(ExternalSortWithOffsetValues externalSort) throws IOException {
    this.file = File.createTempFile("externalsort", ".tmp");
    this.file.deleteOnExit();
    this.externalSort = externalSort; // This might need adjustment based on your code structure
}

    public void addField(Integer field) throws IOException {
        externalSort.getBufferPool().addToOutputBuffer(this.file, field);
    }

    public RunIterator iterator(int bufferIndex) {
        return new RunIterator(file, bufferIndex, externalSort);
    }

    public void flush() throws IOException {
        BufferPool pool = externalSort.getBufferPool();
        pool.flushPage(this.file, pool.getOutputBufferIndex());
    }
}

class RunIterator implements Iterator<Integer> {
    private int currentPageNumber;
    private Integer next;
    private Integer current;
    private File file;
    private Iterator<Integer> pageIterator;
    private int numPages;
    private int bufferIndex;
    private ExternalSort externalSort; // Add this field

    RunIterator(File file, int bufferIndex, ExternalSort externalSort) {
        this.file = file;
        this.bufferIndex = bufferIndex;
        this.currentPageNumber = 0;
        this.numPages = (int) Math.ceil((double)file.length() / Page.PAGE_SIZE);
        this.externalSort = externalSort;
    }

    public void open() throws IOException {
        this.currentPageNumber = 0;
        Page page = externalSort.getBufferPool().readPage(file, 0, bufferIndex);
        this.pageIterator = page.iterator();
    }

    public void close() {
        this.pageIterator = null;
    }

    public boolean hasNext() {
        if (next == null) next = readNextField();
        return next != null;
    }

    public Integer current() {
        return current;
    }

    public Integer next() throws NoSuchElementException {
        if (next == null) {
            next = readNextField();
            if (next == null) throw new NoSuchElementException();
        }

        Integer result = next;
        current = result;
        next = null;
        return result;
    }

    private Integer readNextField() {
        if (this.pageIterator.hasNext()) {
            return this.pageIterator.next();
        } else if (++this.currentPageNumber < this.numPages)  {
            Page page = this.readNextPage();
            this.pageIterator = page.iterator();
            return this.pageIterator.next();
        } else {
            return null;
        }
    }

    private Page readNextPage() {
        try {
            return externalSort.getBufferPool().readPage(this.file, this.currentPageNumber, bufferIndex);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        return null;
    }
}