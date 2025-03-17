package com.example.testfx.externalsort;

import java.io.*;
import java.util.*;

public class ExternalSortWithOffsetValues extends ExternalSort {
    private int numBuffers = 3;
    private int pageSize = Page.PAGE_SIZE;
    private int offset = 0;
    private String inputFilename = "./data/small.txt";
    private BufferPool bufferPool;

    public static void main(String args[]) throws IOException {
        ExternalSortWithOffsetValues sorter = new ExternalSortWithOffsetValues();
        sorter.sort();
    }

    public void setInputFilename(String inputFilename) {
        this.inputFilename = inputFilename;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * Automatically detects the maximum offset (number of already-sorted pages)
     * @param file The input file
     * @return The number of pages that are already sorted
     */
    private int detectOffset(File file) throws IOException {
        int detectedOffset = 0;
        int outputBufferIndex = bufferPool.getOutputBufferIndex();
        int numPages = numPages(file);

        Integer lastValue = null;
        boolean stillSorted = true;

        // Check each page to see if it's sorted and if it continues the sort order
        for (int pageNo = 0; pageNo < numPages && stillSorted; pageNo++) {
            Page page = bufferPool.readPage(file, pageNo, outputBufferIndex);
            Iterator<Integer> it = page.iterator();

            // Check if this page is internally sorted and continues from previous page
            Integer prevValue = lastValue;
            while (it.hasNext() && stillSorted) {
                Integer current = it.next();
                if (prevValue != null && current < prevValue) {
                    stillSorted = false;
                    break;
                }
                prevValue = current;
            }

            if (stillSorted) {
                detectedOffset++;
                lastValue = prevValue;  // Save the last value for comparison with next page
            }
        }

        System.out.println("Detected offset: " + detectedOffset + " pages");
        return detectedOffset;
    }

    @Override
    public List<Integer> sort() throws IOException {
        bufferPool = new BufferPool(numBuffers);
        File binaryFile = convertToBinary(new File(this.inputFilename));

        // Auto-detect offset if not manually set
        if (offset == 0) {
            offset = detectOffset(binaryFile);
        }

        // If entire file is sorted (offset covers all pages)
        int totalPages = numPages(binaryFile);
        if (offset >= totalPages) {
            // Just read the file and return it - it's already sorted
            return readSortedFile(binaryFile);
        }

        List<Run> runList = splitIntoRuns(binaryFile);

        // Perform the merge phase
        while (runList.size() > 1) {
            runList = doAMergeIteration(runList);
        }

        // Get the final sorted run
        if (runList.isEmpty()) {
            return new ArrayList<>();
        }

        Run finalSortedRun = runList.get(0);
        RunIterator finalRun = finalSortedRun.iterator(0);
        finalRun.open();

        List<Integer> sortedResults = new ArrayList<>();
        while (finalRun.hasNext()) {
            sortedResults.add(finalRun.next());
        }

        return sortedResults;
    }

    // Helper method to read an already sorted file
    private List<Integer> readSortedFile(File file) throws IOException {
        List<Integer> results = new ArrayList<>();
        int numPages = numPages(file);
        int outputBufferIndex = bufferPool.getOutputBufferIndex();

        for (int pageNo = 0; pageNo < numPages; pageNo++) {
            Page page = bufferPool.readPage(file, pageNo, outputBufferIndex);
            Iterator<Integer> it = page.iterator();
            while (it.hasNext()) {
                results.add(it.next());
            }
        }

        return results;
    }

    private File convertToBinary(File file) throws IOException {
        File binaryOutFile = bufferPool.createTempFile();
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(binaryOutFile));
             BufferedReader br = new BufferedReader(new FileReader(file), Page.PAGE_SIZE)) {

            String line;

            // For all lines in the file
            while ((line = br.readLine()) != null) {
                try {
                    dos.writeInt(Integer.parseInt(line.trim()));
                } catch (NumberFormatException e) {
                    System.err.println("Warning: Skipping non-numeric line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error converting to binary: " + e.getMessage());
            throw e;
        }

        return binaryOutFile;
    }

    private List<Run> splitIntoRuns(File file) throws IOException {
        List<Run> runList = new ArrayList<>();
        int outputBufferIndex = bufferPool.getOutputBufferIndex();
        int numPages = numPages(file);

        // Process all pages
        for (int pageNo = 0; pageNo < numPages; pageNo++) {
            try {
                // Read the page
                Page page = bufferPool.readPage(file, pageNo, outputBufferIndex);

                // If using offset and this is within the offset range, don't resort
                if (pageNo < offset) {
                    // Skip sorting - these pages are already sorted
                    System.out.println("Page " + pageNo + " is within offset range - skipping sort");
                } else {
                    // For pages beyond offset, always sort
                    System.out.println("Page " + pageNo + " is beyond offset - sorting");
                    page.sort();
                }

                // Flush the page to a temp file
                File tmpFile = bufferPool.flushPage(bufferPool.createTempFile(), outputBufferIndex);
                Run sortedRun = new Run(tmpFile, this);
                runList.add(sortedRun);
            } catch (IOException e) {
                System.err.println("Error processing page " + pageNo + ": " + e.getMessage());
                // Skip this page rather than failing entirely
            }
        }

        return runList;
    }

    private List<Run> doAMergeIteration(List<Run> runList) throws IOException {
        int k = numBuffers - 1;
        List<Run> mergedRunList = new ArrayList<>();

        // If we have offset and this is the first iteration (original number of runs equals number of pages)
        if (offset > 0 && runList.size() == numPages(new File(this.inputFilename))) {
            // Take advantage of already sorted runs
            System.out.println("First merge iteration with offset = " + offset);

            // The offset pages form a single already-sorted sequence
            // We can either concatenate them or merge them as one logical unit
            if (offset > 1) {
                List<Run> offsetRuns = new ArrayList<>(runList.subList(0, Math.min(offset, runList.size())));
                List<Run> otherRuns = new ArrayList<>(runList.subList(Math.min(offset, runList.size()), runList.size()));

                // Merge offset runs first (they should be sequential)
                Run mergedOffsetRun = null;
                if (!offsetRuns.isEmpty()) {
                    mergedOffsetRun = mergeRuns(offsetRuns);
                    mergedRunList.add(mergedOffsetRun);
                }

                // Merge other runs normally
                while (!otherRuns.isEmpty()) {
                    int toIndex = Math.min(k, otherRuns.size());
                    List<Run> runsToMerge = new ArrayList<>(otherRuns.subList(0, toIndex));
                    otherRuns.subList(0, toIndex).clear();

                    Run mergedRun = mergeRuns(runsToMerge);
                    mergedRunList.add(mergedRun);
                }
            } else {
                // Standard merge approach if offset is just 1 page
                while (!runList.isEmpty()) {
                    int toIndex = Math.min(k, runList.size());
                    if (toIndex <= 0) break;

                    List<Run> runsToMerge = new ArrayList<>(runList.subList(0, toIndex));
                    runList.subList(0, toIndex).clear();

                    Run mergedRun = mergeRuns(runsToMerge);
                    mergedRunList.add(mergedRun);
                }
            }
        } else {
            // Standard merge approach for later iterations
            System.out.println("Standard merge iteration");
            while (!runList.isEmpty()) {
                int toIndex = Math.min(k, runList.size());
                if (toIndex <= 0) break;

                List<Run> runsToMerge = new ArrayList<>(runList.subList(0, toIndex));
                runList.subList(0, toIndex).clear();

                Run mergedRun = mergeRuns(runsToMerge);
                mergedRunList.add(mergedRun);
            }
        }

        return mergedRunList;
    }

    // Helper method to calculate number of pages in a file
//    private int numPages(File file) {
//        return (int) Math.ceil((double) file.length() / Page.PAGE_SIZE);
//    }

    private Run mergeRuns(List<Run> runsToMerge) throws IOException {
        List<RunIterator> runIterators = new ArrayList<>();
        Run mergedRun = new Run(this);
        int bufferIndex = 0;

        // Ensure we don't exceed available buffers
        int maxBuffers = numBuffers - 1; // Reserve one for output
        if (runsToMerge.size() > maxBuffers) {
            runsToMerge = runsToMerge.subList(0, maxBuffers);
        }

        for (Run run : runsToMerge) {
            if (bufferIndex >= maxBuffers) {
                System.err.println("Buffer index out of bounds. Skipping run.");
                continue;
            }

            try {
                RunIterator runIterator = run.iterator(bufferIndex);
                runIterator.open();

                // Only add iterators that have values
                if (runIterator.hasNext()) {
                    runIterator.next(); // Initialize current
                    runIterators.add(runIterator);
                }
                bufferIndex++;
            } catch (Exception e) {
                System.err.println("Error initializing run iterator: " + e.getMessage());
            }
        }

        while (!runIterators.isEmpty()) {
            RunIterator currentMinIterator = null;
            int currentMin = Integer.MAX_VALUE;

            for (RunIterator iterator : runIterators) {
                if (iterator.current() != null && iterator.current() < currentMin) {
                    currentMin = iterator.current();
                    currentMinIterator = iterator;
                }
            }

            if (currentMinIterator == null) {
                break; // No valid current values found
            }

            try {
                mergedRun.addField(currentMin);

                if (currentMinIterator.hasNext()) {
                    currentMinIterator.next();
                } else {
                    runIterators.remove(currentMinIterator);
                }
            } catch (Exception e) {
                System.err.println("Error during merge: " + e.getMessage());
                runIterators.remove(currentMinIterator); // Remove problematic iterator
            }
        }

        mergedRun.flush();
        return mergedRun;
    }

    // Helper method to calculate number of pages in a file
    private int numPages(File file) {
        return (int) Math.ceil((double) file.length() / Page.PAGE_SIZE);
    }
}
//package com.example.testfx.externalsort;
//
//import java.io.*;
//import java.util.*;
//
//public class ExternalSortWithOffsetValues extends ExternalSort {
//    private int numBuffers = 3;
//    private int pageSize = Page.PAGE_SIZE;
//    private int offset = 0;
//    private String inputFilename = "./data/small.txt";
//    private BufferPool bufferPool;
//
//    public static void main(String args[]) throws IOException {
//        ExternalSortWithOffsetValues sorter = new ExternalSortWithOffsetValues();
//        sorter.sort();
//    }
//
//
//    public void setInputFilename(String inputFilename) {
//        this.inputFilename = inputFilename;
//    }
//
//    public void setOffset(int offset) {
//        this.offset = offset;
//    }
//
//    @Override
//    public List<Integer> sort() throws IOException {
//        bufferPool = new BufferPool(numBuffers);
//        File binaryFile = convertToBinary(new File(this.inputFilename));
//
//        // If entire file is sorted (offset covers all pages)
//        int totalPages = numPages(binaryFile);
//        if (offset >= totalPages) {
//            // Just read the file and return it - it's already sorted
//            return readSortedFile(binaryFile);
//        }
//
//        List<Run> runList = splitIntoRuns(binaryFile);
//
//        // Merge phase
//        while (runList.size() > 1) {
//            runList = doAMergeIteration(runList);
//        }
//
//        // Get the final sorted run
//        if (runList.isEmpty()) {
//            return new ArrayList<>();
//        }
//
//        Run finalSortedRun = runList.get(0);
//        RunIterator finalRun = finalSortedRun.iterator(0);
//        finalRun.open();
//
//        List<Integer> sortedResults = new ArrayList<>();
//        while (finalRun.hasNext()) {
//            sortedResults.add(finalRun.next());
//        }
//
//        return sortedResults;
//    }
//
//    // Helper method to read an already sorted file
//    private List<Integer> readSortedFile(File file) throws IOException {
//        List<Integer> results = new ArrayList<>();
//        int numPages = numPages(file);
//        int outputBufferIndex = bufferPool.getOutputBufferIndex();
//
//        for (int pageNo = 0; pageNo < numPages; pageNo++) {
//            Page page = bufferPool.readPage(file, pageNo, outputBufferIndex);
//            Iterator<Integer> it = page.iterator();
//            while (it.hasNext()) {
//                results.add(it.next());
//            }
//        }
//
//        return results;
//    }
//
//    private File convertToBinary(File file) throws IOException {
//        File binaryOutFile = bufferPool.createTempFile();
//        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(binaryOutFile));
//             BufferedReader br = new BufferedReader(new FileReader(file), Page.PAGE_SIZE)) {
//
//            String line;
//
//            // For all lines in the file
//            while ((line = br.readLine()) != null) {
//                try {
//                    dos.writeInt(Integer.parseInt(line.trim()));
//                } catch (NumberFormatException e) {
//                    System.err.println("Warning: Skipping non-numeric line: " + line);
//                }
//            }
//        } catch (IOException e) {
//            System.err.println("Error converting to binary: " + e.getMessage());
//            throw e;
//        }
//
//        return binaryOutFile;
//    }
//
//    private List<Run> splitIntoRuns(File file) throws IOException {
//        List<Run> runList = new ArrayList<>();
//        int outputBufferIndex = bufferPool.getOutputBufferIndex();
//        int numPages = (int) Math.ceil((double) file.length() / Page.PAGE_SIZE);
//
//        // Process all pages
//        for (int pageNo = 0; pageNo < numPages; pageNo++) {
//            try {
//                // Read the page
//                Page page = bufferPool.readPage(file, pageNo, outputBufferIndex);
//
//                // If using offset and this is within the offset range, we'll consider it
//                // as "pre-sorted" but still need to include it in our sorting process
//                if (pageNo < offset) {
//                    // Still sort the page - we'll use offset later to optimize merges
//                    page.sort();
//                } else {
//                    // For pages beyond offset, always sort
//                    page.sort();
//                }
//
//                // Flush the page to a temp file
//                File tmpFile = bufferPool.flushPage(bufferPool.createTempFile(), outputBufferIndex);
//                Run sortedRun = new Run(tmpFile, this);
//                runList.add(sortedRun);
//            } catch (IOException e) {
//                System.err.println("Error processing page " + pageNo + ": " + e.getMessage());
//                // Skip this page rather than failing entirely
//            }
//        }
//
//        return runList;
//    }
//
//    private List<Run> doAMergeIteration(List<Run> runList) throws IOException {
//        int k = numBuffers - 1;
//        List<Run> mergedRunList = new ArrayList<>();
//
//        // If we have offset and this is the first iteration
//        if (offset > 0 && runList.size() == numPages(new File(this.inputFilename))) {
//            // Take advantage of already sorted runs
//            // First, directly transfer offset runs to output
//            List<Run> offsetRuns = new ArrayList<>(runList.subList(0, Math.min(offset, runList.size())));
//            List<Run> otherRuns = new ArrayList<>(runList.subList(Math.min(offset, runList.size()), runList.size()));
//
//            // Add offset runs directly to merged list - these are already sorted
//            mergedRunList.addAll(offsetRuns);
//
//            // Merge other runs normally
//            while (!otherRuns.isEmpty()) {
//                int toIndex = Math.min(k, otherRuns.size());
//                List<Run> runsToMerge = new ArrayList<>(otherRuns.subList(0, toIndex));
//                otherRuns.subList(0, toIndex).clear();
//
//                Run mergedRun = mergeRuns(runsToMerge);
//                mergedRunList.add(mergedRun);
//            }
//        } else {
//            // After first iteration, or if no offset, use standard merge
//            while (!runList.isEmpty()) {
//                int toIndex = Math.min(k, runList.size());
//                if (toIndex <= 0) break;
//
//                List<Run> runsToMerge = new ArrayList<>(runList.subList(0, toIndex));
//                runList.subList(0, toIndex).clear();
//
//                Run mergedRun = mergeRuns(runsToMerge);
//                mergedRunList.add(mergedRun);
//            }
//        }
//
//        return mergedRunList;
//    }
//
//    // Helper method to calculate number of pages in a file
//    private int numPages(File file) {
//        return (int) Math.ceil((double) file.length() / Page.PAGE_SIZE);
//    }
//
//    private Run mergeRuns(List<Run> runsToMerge) throws IOException {
//        List<RunIterator> runIterators = new ArrayList<>();
//        Run mergedRun = new Run(this);
//        int bufferIndex = 0;
//
//        // Ensure we don't exceed available buffers
//        int maxBuffers = numBuffers - 1; // Reserve one for output
//        if (runsToMerge.size() > maxBuffers) {
//            runsToMerge = runsToMerge.subList(0, maxBuffers);
//        }
//
//        for (Run run : runsToMerge) {
//            if (bufferIndex >= maxBuffers) {
//                System.err.println("Buffer index out of bounds. Skipping run.");
//                continue;
//            }
//
//            try {
//                RunIterator runIterator = run.iterator(bufferIndex);
//                runIterator.open();
//
//                // Only add iterators that have values
//                if (runIterator.hasNext()) {
//                    runIterator.next(); // Initialize current
//                    runIterators.add(runIterator);
//                }
//                bufferIndex++;
//            } catch (Exception e) {
//                System.err.println("Error initializing run iterator: " + e.getMessage());
//            }
//        }
//
//        while (!runIterators.isEmpty()) {
//            RunIterator currentMinIterator = null;
//            int currentMin = Integer.MAX_VALUE;
//
//            for (RunIterator iterator : runIterators) {
//                if (iterator.current() != null && iterator.current() < currentMin) {
//                    currentMin = iterator.current();
//                    currentMinIterator = iterator;
//                }
//            }
//
//            if (currentMinIterator == null) {
//                break; // No valid current values found
//            }
//
//            try {
//                mergedRun.addField(currentMin);
//
//                if (currentMinIterator.hasNext()) {
//                    currentMinIterator.next();
//                } else {
//                    runIterators.remove(currentMinIterator);
//                }
//            } catch (Exception e) {
//                System.err.println("Error during merge: " + e.getMessage());
//                runIterators.remove(currentMinIterator); // Remove problematic iterator
//            }
//        }
//
//        mergedRun.flush();
//        return mergedRun;
//    }
//}
