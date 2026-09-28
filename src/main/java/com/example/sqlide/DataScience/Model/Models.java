package com.example.sqlide.DataScience.Model;

/**
 * Algoritmos disponíveis para treino.
 *
 * <p>Cada um declara agora se é regressão ou classificação. Antes essa distinção não
 * existia e o pipeline avaliava tudo com {@code Accuracy}, o que para um modelo de
 * regressão dava sempre um número sem significado — os valores contínuos eram truncados
 * para inteiro antes de comparar.</p>
 */
public enum Models {

    LINEAR_REGRESSION("Linear regression (OLS)", Task.REGRESSION),
    TREE_REGRESSION("Regression tree", Task.REGRESSION),
    RANDOM_FOREST_REGRESSION("Random forest (regression)", Task.REGRESSION),
    GRADIENT_REGRESSION("Gradient boosting (regression)", Task.REGRESSION),

    LOGISTIC_REGRESSION("Logistic regression", Task.CLASSIFICATION),
    LOGISTIC_BINOMIAL_REGRESSION("Logistic regression (binomial)", Task.CLASSIFICATION),
    LOGISTIC_MULTIMODAL_REGRESSION("Logistic regression (multinomial)", Task.CLASSIFICATION),
    KNN("K nearest neighbours", Task.CLASSIFICATION),
    RANDOM_FOREST_CLASSIFICATION("Random forest (classification)", Task.CLASSIFICATION),
    GRADIENT_CLASSIFICATION("Gradient boosting (classification)", Task.CLASSIFICATION);

    public enum Task {
        REGRESSION, CLASSIFICATION
    }

    private final String label;
    private final Task task;

    Models(String label, Task task) {
        this.label = label;
        this.task = task;
    }

    public Task getTask() {
        return task;
    }

    public boolean isClassification() {
        return task == Task.CLASSIFICATION;
    }

    public boolean isRegression() {
        return task == Task.REGRESSION;
    }

    /** True para os modelos que precisam do alvo como número real, não como categoria. */
    public boolean requiresNumericTarget() {
        return isRegression();
    }

    /** True para os que se treinam sobre uma matriz de doubles em vez de um DataFrame. */
    public boolean usesMatrixInput() {
        return this == LOGISTIC_REGRESSION
                || this == LOGISTIC_BINOMIAL_REGRESSION
                || this == LOGISTIC_MULTIMODAL_REGRESSION
                || this == KNN;
    }

    @Override
    public String toString() {
        return label;
    }

}
