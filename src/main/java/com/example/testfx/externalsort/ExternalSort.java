package com.example.testfx.externalsort;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ExternalSort {
    private int numBuffers = 3;
    private BufferPool bufferPool;

    private int pageSize = Page.PAGE_SIZE;


    private static ExternalSort _instance = new ExternalSort();

    private String inputFilename;
    public ExternalSort(String inputFilename) {
        this.inputFilename = inputFilename;
        this.bufferPool = new BufferPool(numBuffers);

    }

    public BufferPool getBufferPool() {
        return this.bufferPool;
    }

    public ExternalSort() {
        this.inputFilename = "src/main/java/com/example/testfx/externalsort/small.txt";
        this.bufferPool = new BufferPool(numBuffers); // Add this line
    }

    public static void main(String args[]) throws IOException {

        Page.PAGE_SIZE = _instance.pageSize;

        _instance.sort();
    }

    public List<Integer> sort() throws IOException {
        // Remove the bufferPool initialization from here
        File binaryFile = convertToBinary(new File(this.inputFilename));

        List<Run> runList = splitIntoRuns(binaryFile);

        while (runList.size() > 1) {
            runList = doAMergeIteration(runList);
        }

        Run finalSortedRun = runList.get(0);
        RunIterator finalRun = finalSortedRun.iterator(0);
        finalRun.open();

        List<Integer> sortedResults = new ArrayList<>();
        while (finalRun.hasNext()) {
            int value = finalRun.next();
            sortedResults.add(value);
        }

        return sortedResults;
    }
    private File convertToBinary(File file) throws IOException {
        File binaryOutFile = bufferPool.createTempFile();
        DataOutputStream dos = new DataOutputStream(new FileOutputStream(binaryOutFile));
        BufferedReader br = new BufferedReader(new FileReader(file), Page.PAGE_SIZE);
        String line;

        while ((line = br.readLine()) != null) {
            dos.writeInt(Integer.parseInt(line));
        }

        dos.close();

        return binaryOutFile;
    }

    private List<Run> splitIntoRuns(File file) throws IOException {
        List<Run> runList = new ArrayList<>();
        int outputBufferIndex = bufferPool.getOutputBufferIndex();
        int numPages = (int) Math.ceil((double)file.length() / Page.PAGE_SIZE);

        for (int pageNo = 0; pageNo < numPages; pageNo++) {
            Page page = bufferPool.readPage(file, pageNo, outputBufferIndex);
            page.sort();
            File tmpFile = bufferPool.flushPage(bufferPool.createTempFile(), outputBufferIndex);
            Run sortedRun = new Run(tmpFile,this);
            runList.add(sortedRun);
        }

        return runList;
    }

    private List<Run> doAMergeIteration(List<Run> runList) throws IOException {
        int k = bufferPool.getSize() - 1;
        List<Run> mergedRunList = new ArrayList<>();

        while (runList.size() > 0) {
            int toIndex = Math.min(k, runList.size());
            List<Run> runsToMerge = new ArrayList<>(runList.subList(0, toIndex));
            runList.subList(0, toIndex).clear();

            Run mergedRun = mergeRuns(runsToMerge);
            mergedRunList.add(mergedRun);
        }

        return mergedRunList;
    }

    private Run mergeRuns(List<Run> runsToMerge) throws IOException {
        List<RunIterator> runIterators = new ArrayList<>();
        Run mergedRun = new Run(this);
        int bufferIndex = 0;

        for (Run run: runsToMerge) {
            RunIterator runIterator = run.iterator(bufferIndex);
            runIterator.open();
            runIterator.next(); // get initial value from each iterator ready
            runIterators.add(runIterator);
            bufferIndex++;
        }

        while (runIterators.size() > 0) {
            RunIterator currentMinIterator = null;
            int currentMin = Integer.MAX_VALUE;
            for (RunIterator iterator: runIterators) {
                if (iterator.current() < currentMin) {
                    currentMin = iterator.current();
                    currentMinIterator = iterator;
                }
            }

            mergedRun.addField(currentMin);

            if (currentMinIterator != null && currentMinIterator.hasNext()) {
                currentMinIterator.next();
            } else {
                runIterators.remove(currentMinIterator);
            }
        }

        mergedRun.flush();

        return mergedRun;
    }
}
