package com.example.testfx;

import com.example.testfx.externalsort.ExternalSortWithOffsetValues;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import com.example.testfx.externalsort.ExternalSort;


public class GUIController implements Initializable {

    @FXML
    private ChoiceBox<String> choiceBox;

    @FXML
    private Button clearBTN;

    @FXML
    private Label datasetLabel;

    @FXML
    private Button importBTN;

    @FXML
    private TextArea originalSortTextArea;

    @FXML
    private Label sizeLabel;

    @FXML
    private Button sortBTN;

    @FXML
    private TextArea sortedTextArea;

    @FXML
    private Label spaceComplexityLabel;

    @FXML
    private Label timeComplexityLabel;

    @FXML
    private Label executionTimeLabel;

    @FXML
    private Label sortingMethod;

    @FXML
    private Label totalLines;

    //Time Complexity

    @FXML
    private Label TCBestCaseLabel;

    @FXML
    private Label TCAverageCaseLabel;

    @FXML
    private Label TCWorstCaseLabel;

    //Space Complexity
    @FXML
    private Label SCBestCaseLabel;

    @FXML
    private Label SCAverageCaseLabel;

    @FXML
    private Label SCWorstCaseLabel;

    private File selectedFile;

    public void clear() {
        selectedFile = null;
        originalSortTextArea.clear();
        sortedTextArea.clear();
        sizeLabel.setText("Size: ");
        datasetLabel.setText("No dataset loaded");
        totalLines.setText("Total Lines: ");

        TCBestCaseLabel.setText("TCBestCase: ");
        TCAverageCaseLabel.setText("TCAverageCase: ");
        TCWorstCaseLabel.setText("TCWorstCase: ");
        SCBestCaseLabel.setText("SCBestCase: ");
        SCAverageCaseLabel.setText("SCAverageCase: ");
        SCWorstCaseLabel.setText("SCWorstCase: ");


        executionTimeLabel.setText("Execution Time: ");
    }

    private void analyzeDataset(String filename){
         int numericCount = 0, textCount = 0, mixedCount = 0, totalLines = 0;
        boolean isTabular = false;

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                totalLines++;

                if (line.contains(",")) { // Check for tabular format (CSV-like)
                    isTabular = true;
                }

                if (isNumeric(line)) {
                    numericCount++;
                } else if (isAlphabetic(line)) {
                    textCount++;
                } else {
                    mixedCount++;
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            return;
        }

        // Determine dataset type
        System.out.println("Total lines analyzed: " + totalLines);
        this.totalLines.setText("Total Lines: " + totalLines);
        if (isTabular) {
            datasetLabel.setText("Dataset Type: Tabular");
        } else if (numericCount == totalLines) {
            datasetLabel.setText("Dataset Type: Numerical");
        } else if (textCount == totalLines) {
            datasetLabel.setText("Dataset Type: Text-Based");
        } else if (mixedCount > 0) {
            datasetLabel.setText("Dataset Type: Mixed (Numbers & Text)");
        } else {
            datasetLabel.setText("Dataset Type: Unknown");
        }
    }
    private static boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?"); // Matches integers & decimals
    }

    private static boolean isAlphabetic(String str) {
        return str.matches("[a-zA-Z ]+"); // Matches only letters & spaces
    }

    public void sort() {
        if(selectedFile == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No Dataset Loaded");
            alert.setContentText("Please import a dataset before sorting.");
            alert.showAndWait();
            return;
        }

        String method = sortingMethod.getText();
        long startTime = System.nanoTime();

        try {
            if(method.equals("External Merge Sort with Offset Values")) {

                TCBestCaseLabel.setText("BestCase: O(N log N)");
                TCAverageCaseLabel.setText("AverageCase: O(N log N)");
                TCWorstCaseLabel.setText("WorstCase: O(N log N)");

                SCBestCaseLabel.setText("BestCase: O(N)");
                SCAverageCaseLabel.setText("AverageCase: O(N)");
                SCWorstCaseLabel.setText("WorstCase: O(N)");

                // Check if it's the first sort or a subsequent sort
                File metadataFile = new File(selectedFile.getAbsolutePath() + ".metadata");
                int offset = 0;
                boolean isFirstSort = !metadataFile.exists();

                if (!isFirstSort) {
                    try (BufferedReader br = new BufferedReader(new FileReader(metadataFile))) {
                        String line = br.readLine();
                        if (line != null) {
                            offset = Integer.parseInt(line.trim());
                        }
                    } catch (Exception e) {
                        System.err.println("Error reading metadata: " + e.getMessage());
                        offset = 0;
                    }
                }

                // Ask user about offset for demonstration purposes
                if (!isFirstSort) {
                    TextInputDialog dialog = new TextInputDialog(String.valueOf(offset));
                    dialog.setTitle("Offset Value");
                    dialog.setHeaderText("Enter the offset value (0 for full sort)");
                    dialog.setContentText("Offset:");

                    Optional<String> result = dialog.showAndWait();
                    if (result.isPresent()) {
                        try {
                            offset = Integer.parseInt(result.get().trim());
                        } catch (NumberFormatException e) {
                            offset = 0;
                        }
                    }
                }

                // Perform the sort
                ExternalSortWithOffsetValues externalSort = new ExternalSortWithOffsetValues();
                externalSort.setInputFilename(selectedFile.getAbsolutePath());
                externalSort.setOffset(offset);
                List<Integer> sortedData = externalSort.sort();

                // Save metadata for next time
                try (PrintWriter writer = new PrintWriter(metadataFile)) {
                    writer.println(sortedData.size());
                }

                // Display results
                StringBuilder sb = new StringBuilder();
                for (Integer value : sortedData) {
                    sb.append(value).append("\n");
                }
                sortedTextArea.setText(sb.toString());

                // Show timing with offset info
                long endTime = System.nanoTime();
                double elapsedTime = (endTime - startTime) / 1_000_000.0;
                String timeInfo = String.format("Execution Time: %.2f ms", elapsedTime);
                if (offset > 0) {
                    timeInfo += String.format(" (Used offset: %d)", offset);
                } else {
                    timeInfo += " (Full sort)";
                }
                executionTimeLabel.setText(timeInfo);

            } else {
                TCBestCaseLabel.setText("BestCase: O(N logk(N/M)");
                TCAverageCaseLabel.setText("AverageCase: O(N logk(N/M)");
                TCWorstCaseLabel.setText("WorstCase: O(N log N)");

                SCBestCaseLabel.setText("BestCase: O(M)");
                SCAverageCaseLabel.setText("AverageCase: O(N)");
                SCWorstCaseLabel.setText("WorstCase: O(N log N)");

                // Standard external merge sort
                ExternalSort externalSort = new ExternalSort(selectedFile.getAbsolutePath());
                List<Integer> sortedData = externalSort.sort();

                StringBuilder sb = new StringBuilder();
                for (Integer value : sortedData) {
                    sb.append(value).append("\n");
                }
                sortedTextArea.setText(sb.toString());

                long endTime = System.nanoTime();
                double elapsedTime = (endTime - startTime) / 1_000_000.0;
                executionTimeLabel.setText(String.format("Execution Time: %.2f ms", elapsedTime));
            }

            // Set complexity labels

        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Sorting Error");
            alert.setContentText("Error during sorting: " + e.getMessage());
            alert.showAndWait();
            e.printStackTrace();
        }
    }
    public void importDataset() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Dataset");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        selectedFile = fileChooser.showOpenDialog(importBTN.getScene().getWindow());

        if (selectedFile != null) {
            try {
                // Read the content of the file
                String content = new String(Files.readAllBytes(selectedFile.toPath()));
                // Display the content in the originalSortTextArea
                originalSortTextArea.setText(content);
                // Update dataset label with file name
                analyzeDataset(selectedFile.getAbsolutePath());
                // Update size label with file size
                double fileSize = (selectedFile.length() / 1024);
                sizeLabel.setText(String.format("Size: %.2f KB", fileSize));
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Error Reading File");
                alert.setContentText("Could not read the file: " + e.getMessage());
                alert.showAndWait();
            }
        }

    }

    public void sortingMethod(String method) {
        System.out.println("Sorting method: " + method);
        sortingMethod.setText(method);

    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        choiceBox.getItems().addAll("External Merge Sort", "External Merge Sort with Offset Values");
        choiceBox.setValue("External Merge Sort");
        choiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {;
            sortingMethod(newValue);
        });

    }
}
