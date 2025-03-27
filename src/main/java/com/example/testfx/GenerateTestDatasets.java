package com.example.testfx;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

public class GenerateTestDatasets {

    public static void main(String[] args) throws IOException {
        GenerateTestDatasets generator = new GenerateTestDatasets();
        generator.generateTestDataset("dataset_0pct_sorted.txt", 100000, 0);  // 0% sorted
//        generator.generateTestDataset("dataset_50pct_sorted.txt", 100000, 0.5);  // 50% sorted
//        generator.generateTestDataset("dataset_75pct_sorted.txt", 100000, 0.75); // 75% sorted
//        generator.generateTestDataset("dataset_25pct_sorted.txt", 100000, 0.25); // 25% sorted

    }
        /**
     * Generates a test file with partially sorted data
     * @param filename Output filename
     * @param totalSize Total number of integers to generate
     * @param sortedPortion Portion of data that should be sorted (0.0-1.0)
     */
    public void generateTestDataset(String filename, int totalSize, double sortedPortion) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Calculate how many numbers should be sorted
            int sortedSize = (int)(totalSize * sortedPortion);

            // Generate sorted portion
            for (int i = 0; i < sortedSize; i++) {
                writer.println(i);
            }

            // Generate random portion
            Random random = new Random();
            for (int i = sortedSize; i < totalSize; i++) {
                writer.println(random.nextInt(1000000));  // Random value between 0-999999
            }
        }
        System.out.println("Generated test dataset: " + filename);
        System.out.println("Total size: " + totalSize + " integers");
        System.out.println("Sorted portion: " + (int)(totalSize * sortedPortion) + " integers (" + (sortedPortion * 100) + "%)");
    }
}
