# External Merge Sort with Offset Values

## Project Overview
This project aims to validate the findings of the paper ["Optimizing External Merge Sort with Offset Values"](https://openproceedings.org/2025/conf/edbt/paper-79.pdf), which suggests that modifying external merge sort with offset values improves efficiency and performance over the normal external merge sort. Our approach involves implementing a graphical user interface (GUI) to simulate both algorithms and testing their performance on various datasets.

## Objective
- To analyze whether using offset values in an external merge sort is more efficient than the normal external merge sort.
- To compare sorting times on datasets with varying levels of pre-sortedness (0%, 25%, 50%, and 75% sorted data).

## Methodology
1. **Implementation**: We developed a GUI-based simulation using **Java** and **SceneBuilder** for:
    - Normal External Merge Sort
    - External Merge Sort with Offset Values
2. **Dataset Preparation**: Four numerical datasets were used, each with different levels of sortedness:
    - **0% Sorted (Unsorted Data)**
    - **25% Sorted**
    - **50% Sorted**
    - **75% Sorted**
3. **Performance Measurement**: We measured and compared the execution time of both sorting algorithms on each dataset.

## Findings
- **Pre-sorted Datasets (25%, 50%, 75%)**: The normal external merge sort performed **faster** than the offset-enhanced version.
- **Fully Unsorted Dataset (0% sorted)**: The offset-based approach was **slower** compared to the normal external merge sort.

## Conclusion
Our results indicate that while the offset-based external merge sort may have advantages in certain scenarios, it does not consistently outperform the normal external merge sort. Specifically, for pre-sorted datasets, the standard external merge sort is more efficient, whereas on completely unsorted data, the offset-based version exhibits slower performance.

## Future Improvements
- Experimenting with different offset values to find an optimal configuration.
- Testing on larger datasets to analyze scalability.
- Evaluating the impact of different hardware and storage configurations on sorting performance.

## Contributors
- Gwndlyn Lnnah Peralta (Leader)
- Princess Charisse Mae Priego
- Jonathan James Sindo
- John Alphonce Jornadal
- Dranreb Jay Arzadon

## Acknowledgments
We would like to thank our professor and the authors of the referenced paper for their insights into external merge sort optimization. We would also like to express our gratitude to our classmates for their support and feedback throughout the project. Especially our team leader, Gwndlyn Lnnah Peralta, for coordinating the project and ensuring its successful completion. Without her guidance and leadership, this project would not have been possible. So in my outmost respect, I would like to thank her for her dedication and hard work.

## License
This project is for academic purposes only. If you wish to use or modify it, please provide appropriate credit.

