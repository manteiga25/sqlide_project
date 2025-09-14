package com.example.sqlide.DataScience.Model;

import com.example.sqlide.DataScience.LabelEncoder;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.chart.XYChart;
import smile.classification.KNN;
import smile.classification.LogisticRegression;
import smile.data.*;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.data.vector.ValueVector;
import smile.regression.*;
import smile.util.Index;
import smile.validation.ClassificationMetrics;
import smile.validation.RegressionMetrics;
import smile.validation.metric.Accuracy;
import smile.validation.metric.MSE;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class ModelPipeline {

    private Object model = null;

    private Models model_type = null;

    private final LabelEncoder labelEncoder = new LabelEncoder();

    private int size;

    private double learningRate;
    private int knnTree;

    private List<String> schema;
    private String y;

    private final SimpleDoubleProperty accuracy = new SimpleDoubleProperty();

    public Models getModel_type() {
        return model_type;
    }

    public void setSchema(final Collection<String> schema, final String y) {
        this.schema = (List<String>) schema;
        this.y = y;
    }

    public String getY() {
        return y;
    }

    public List<String> getSchema() {
        return schema;
    }

    public ReadOnlyDoubleProperty getAccuracyProperty() {
        return accuracy;
    }

    public LabelEncoder getLabelEncoder() {
        return labelEncoder;
    }

    public void setTestSize(final int size) {
        this.size = size;
    }

    public void setLearningRate(final double learningRate)  {
        this.learningRate = learningRate;
    }

    public Object getModel() {
        return model;
    }

    public void setModel(Models model) {
        this.model_type = model;
        accuracy.set(0);
    }

    private DataFrame processData(ArrayList<HashMap<String, String>> raw_data) {
        ArrayList<Double> y_ = raw_data.parallelStream().map(m -> m.get(y))
                .map(Double::parseDouble)                // converte para Double
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        DataFrame data = new DataFrame(new DoubleVector(y, y_.stream()
                .mapToDouble(Double::doubleValue) // converte Double -> double
                .toArray()));

        schema.parallelStream().forEach(list_x->{
            ArrayList<Double> x = raw_data.parallelStream().map(m -> m.get(list_x))
                    .map(Double::parseDouble)                // converte para Double
                    .collect(Collectors.toCollection(ArrayList::new));
            synchronized (data) {
                data.add(new DoubleVector(list_x, x.stream()
                        .mapToDouble(Double::doubleValue) // converte Double -> double
                        .toArray()));
            }
        });

        return data;
    }

    private static DataFrame splitDataTest(int size, DataFrame data) {
        Index index = Index.range(size, data.size());
        return data.get(index);
    }

    private static DataFrame splitDataTrain(int size, DataFrame data) {
        Index index = Index.range(0, data.size() - size);
        return data.get(index);
    }

    public void Train(final ArrayList<HashMap<String, String>> raw_data) {
        switch (model_type) {
            case LINEAR_REGRESSION -> model = TrainLinear(raw_data);
            case RANDOM_FOREST_REGRESSION -> model = TrainForest(raw_data);
            case GRADIENT_REGRESSION -> model = TrainGradientLinear(raw_data);
            case TREE_REGRESSION -> model = TrainRegressionTree(raw_data);
            case LOGISTIC_REGRESSION -> model = TrainLogistic(raw_data);
            case LOGISTIC_BINOMIAL_REGRESSION -> model = TrainLogisticBinomial(raw_data);
            case LOGISTIC_MULTIMODAL_REGRESSION -> model = TrainLogisticMultinomial(raw_data);
            case RANDOM_FOREST_CLASSIFICATION -> model = TrainClassificationForest(raw_data);
            case GRADIENT_CLASSIFICATION -> model = TrainGradientClassifier(raw_data);
            case KNN -> model = TrainKNN(raw_data);
        }
    }

    private RandomForest TrainForest(final ArrayList<HashMap<String, String>> raw_data) {

        final double test = (double)size / 100;

      /*  final String command = getCommand(table, y, cols, buffer);

        ArrayList<HashMap<String, String>> raw_data = fetcher.fetchRawDataMap(String.format(command, 0)); */

        DataFrame data = processData(raw_data), data_train, data_test;

        int size = (int) (data.size() * test);

        data_test = splitDataTest(size, data);
        data_train = splitDataTrain(size, data);

        Formula formula = Formula.lhs(y);

        /*  while (data.column(y).size() == buffer) {
            offset += buffer;

            raw_data = fetcher.fetchRawDataMap(String.format(command, offset));

            data = processData(raw_data, y, cols);

            final int size_ = (int) (data.size() * test);

            data_test = splitDataTest(size_, data);
            data_train = splitDataTrain(size_, data);

            ArrayList<SampleInstance<Tuple, Double>> instance = new ArrayList<>();

            for (Row row : data_train) {
                instance.add(new SampleInstance<>(row.getStruct(y), row.getDouble(1)));
            }

            Dataset<Tuple, Double> dataset = new SimpleDataset<>(instance);

            model.update(dataset);
        } */


        // Define the formula: Target ~ Feature1 + Featur
        //  LinearModel model =  RidgeRegression.fit(formula, data, 0);

        RandomForest model = RandomForest.fit(formula, data_train);

        accuracy.set(Accuracy.of(Arrays.stream(data_test.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(data_test.drop(y))).mapToInt(n -> ((Number) n).intValue()).toArray()));


        return model;
    }

    private KNN<double[]> TrainKNN(final ArrayList<HashMap<String, String>> raw_data) {
        final double test = (double)size / 100;

        DataFrame data = processData(raw_data), data_train, data_test;

        int size = (int) (data.size() * test);

        data_test = splitDataTest(size, data);
        data_train = splitDataTrain(size, data);

        ArrayList<String> y_ = raw_data.parallelStream().map(m -> m.get(y))
                .collect(Collectors.toCollection(ArrayList::new));

        labelEncoder.updateEncoder(y_);
        int[] y_encoded = labelEncoder.encode(List.of(data_train.column(y).toStringArray()));

        KNN<double[]> model = KNN.fit(data_train.toArray(schema.toArray(new String[0])), y_encoded, knnTree);

        accuracy.set(Accuracy.of(labelEncoder.encode( List.of(data_test.column(y).toStringArray())), Arrays.stream(model.predict(data_test.drop(y).toArray(schema.toArray(new String[0])))).map(n -> ((Number) n).intValue()).toArray()));

        return model;

    }

    private GradientTreeBoost TrainGradientLinear(final ArrayList<HashMap<String, String>> raw_data) {
        final double test = (double)size / 100;

        DataFrame data = processData(raw_data), data_train, data_test;

        int size = (int) (data.size() * test);

        data_test = splitDataTest(size, data);
        data_train = splitDataTrain(size, data);

        Formula formula = Formula.lhs(y);

        GradientTreeBoost model = GradientTreeBoost.fit(formula, data_train);


        accuracy.set(Accuracy.of(Arrays.stream(data_test.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(data_test.drop(y))).mapToInt(n -> ((Number) n).intValue()).toArray()));

            System.out.println(MSE.of(data_test.column(y).toDoubleArray(), model.predict(data_test)));

            //taModelResults.appendText("Model Linear Regression (Step: " + offset/buffer +"): " + model.RSS() + " " + model.adjustedRSquared() + " " + model.error() + " " + model.ftest() + " " + model.intercept() + " " + model.pvalue()+"\n");
            //   System.out.println("Coefficients: " + Arrays.toString(model.coefficients()));

        //System.out.println("Coefficients: " + Arrays.toString(model.coefficients()));

        return model;
    }

    private RegressionTree TrainRegressionTree(ArrayList<HashMap<String, String>> raw_data) {
        final double test = (double)size / 100;

        DataFrame data = processData(raw_data), data_train, data_test;

        int size = (int) (data.size() * test);

        data_test = splitDataTest(size, data);
        data_train = splitDataTrain(size, data);

        Formula formula = Formula.lhs(y);

        RegressionTree model = RegressionTree.fit(formula, data_train);

        accuracy.set(Accuracy.of(Arrays.stream(data_test.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(data_test.drop(y))).mapToInt(n -> ((Number) n).intValue()).toArray()));

        System.out.println(MSE.of(data_test.column(y).toDoubleArray(), model.predict(data_test)));

        //taModelResults.appendText("Model Linear Regression (Step: " + offset/buffer +"): " + model.RSS() + " " + model.adjustedRSquared() + " " + model.error() + " " + model.ftest() + " " + model.intercept() + " " + model.pvalue()+"\n");
        //   System.out.println("Coefficients: " + Arrays.toString(model.coefficients()));

        //System.out.println("Coefficients: " + Arrays.toString(model.coefficients()));

        return model;
    }

    private LinearModel TrainLinear(final ArrayList<HashMap<String, String>> raw_data) {

        final double test = (double)size / 100;

        DataFrame data = processData(raw_data), data_train, data_test;

        int size = (int) (data.size() * test);

        data_test = splitDataTest(size, data);
        data_train = splitDataTrain(size, data);

        Formula formula = Formula.lhs(y);

        LinearModel model = OLS.fit(formula, data_train);

        System.out.println(MSE.of(data_test.column(y).toDoubleArray(), model.predict(data_test)));

        accuracy.set(Accuracy.of(Arrays.stream(data_test.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(data_test)).mapToInt(n -> ((Number) n).intValue()).toArray()));

        System.out.println("Coefficients: " + Arrays.toString(model.coefficients()));

        return model;

    }

    private LogisticRegression TrainLogistic(final ArrayList<HashMap<String, String>> raw_data) {
        final int buffer = 1000000;
        int offset = buffer;

        final double test = (double)size / 100;

        DataFrame data = processData(raw_data), trainData, testData;

        int size = (int) (data.size() * test);

        trainData = splitDataTrain(size, data);
        testData = splitDataTest(size, data);



        LogisticRegression model = LogisticRegression.fit(trainData.toArray(schema.toArray(new String[0])), Arrays.stream(trainData.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray());
        model.setLearningRate(learningRate);

        accuracy.set(Accuracy.of(Arrays.stream(testData.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), model.predict(testData.toArray(schema.toArray(new String[0])))));

        //smile.classification.RandomForest m2 = smile.classification.RandomForest.fit(formula, data);


        // Define the formula: Target ~ Feature1 + Featur
        //  LinearModel model =  RidgeRegression.fit(formula, data, 0);


        return model;
    }

    private LogisticRegression TrainLogisticBinomial(final ArrayList<HashMap<String, String>> raw_data) {
        final int buffer = 1000000;
        int offset = buffer;

        final double test = (double)size / 100;

        DataFrame data = processData(raw_data), trainData, testData;

        int size = (int) (data.size() * test);

        trainData = splitDataTrain(size, data);
        testData = splitDataTest(size, data);

        LogisticRegression model = LogisticRegression.binomial(data.toArray(schema.toArray(new String[0])), Arrays.stream(trainData.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray());
        model.setLearningRate(learningRate);

        accuracy.set(Accuracy.of(Arrays.stream(testData.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), model.predict(testData.toArray(schema.toArray(new String[0])))));

        //smile.classification.RandomForest m2 = smile.classification.RandomForest.fit(formula, data);


        // Define the formula: Target ~ Feature1 + Featur
        //  LinearModel model =  RidgeRegression.fit(formula, data, 0);


        return model;
    }

    private LogisticRegression TrainLogisticMultinomial(final ArrayList<HashMap<String, String>> raw_data) {
        final int buffer = 1000000;
        int offset = buffer;

        final double test = (double)size / 100;

        DataFrame data = processData(raw_data), trainData, testData;

      /*  ArrayList<Double> y_ = fetcher.fetchRawDataMap(String.format("SELECT %s FROM %s WHERE %s IS NOT NULL LIMIT 1000000;", y, table, y)).stream().map(m -> m.get(y))                  // pega o valor da chave
                .filter(Objects::nonNull)                // descarta null
                .map(Double::parseDouble)                // converte para Double
                .collect(Collectors.toCollection(ArrayList::new)); */


      /*  DataFrame data = new DataFrame(new DoubleVector(y, y_.stream()
                .mapToDouble(Double::doubleValue) // converte Double -> double
                .toArray())); */

        /*for (String col : cols) {
            ArrayList<Double> x_ = fetcher.fetchRawDataMap(String.format("SELECT %s FROM %s WHERE %s IS NOT NULL LIMIT 1000000;", col, table, col)).stream().map(m -> m.get(col))                  // pega o valor da chave
                    .filter(Objects::nonNull)                // descarta null
                    .map(Double::parseDouble)                // converte para Double
                    .collect(Collectors.toCollection(ArrayList::new));
            data.add(new DoubleVector(col, x_.stream()
                    .mapToDouble(Double::doubleValue) // converte Double -> double
                    .toArray()));
        } */



        int size = (int) (data.size() * test);

        trainData = splitDataTrain(size, data);
        testData = splitDataTest(size, data);

        LogisticRegression model = LogisticRegression.multinomial(data.toArray(schema.toArray(new String[0])), Arrays.stream(trainData.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray());
        model.setLearningRate(learningRate);

        accuracy.set(Accuracy.of(Arrays.stream(testData.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), model.predict(testData.toArray(schema.toArray(new String[0])))));

        //smile.classification.RandomForest m2 = smile.classification.RandomForest.fit(formula, data);


        // Define the formula: Target ~ Feature1 + Featur
        //  LinearModel model =  RidgeRegression.fit(formula, data, 0);


        return model;
    }

    private smile.classification.GradientTreeBoost TrainGradientClassifier(final ArrayList<HashMap<String, String>> raw_data) {
        final double test = (double)size / 100;

        final int buffer = 1000000;
        int offset = buffer;

        ArrayList<String> y_ = raw_data.parallelStream().map(m -> m.get(y))
                .collect(Collectors.toCollection(ArrayList::new));

        DataFrame data = processData(raw_data), data_train, data_test;

        labelEncoder.updateEncoder(y_);
        int[] y_encoded = labelEncoder.encode(y_);

        data = data.drop(y);
        data.add(new IntVector(y, y_encoded));

        int size = (int) (data.size() * test);

        data_test = splitDataTest(size, data);
        data_train = splitDataTrain(size, data);

        Formula formula = Formula.lhs(y);

        smile.classification.GradientTreeBoost model = smile.classification.GradientTreeBoost.fit(formula, data_train);

        accuracy.set(Accuracy.of(data_test.column(y).stream().mapToInt(n -> ((Number) n).intValue()).toArray(), model.predict(data_test.drop(y))));


        //   System.out.println(MSE.of(data_test.column(y).toDoubleArray(), model.predict(data_test)));

        //System.out.println("Coefficients: " + Arrays.toString(model.coefficients()));

        return model;
    }

    private smile.classification.RandomForest TrainClassificationForest(final ArrayList<HashMap<String, String>> raw_data) {
        final int buffer = 10000;
        int offset = buffer;

        final double test = (double)size / 100;

        ArrayList<String> y_ = raw_data.parallelStream().map(m -> m.get(y))
                .collect(Collectors.toCollection(ArrayList::new));

        DataFrame data = processData(raw_data), data_train, data_test;

        //fetcher.fetchRawDataMap(String.format("SELECT %s FROM %s WHERE %s IS NOT NULL LIMIT %d;", y, table, y, buffer)).parallelStream().map(m -> m.get(y))                  // pega o valor da chave
        // .collect(Collectors.toCollection(ArrayList::new));

       // LabelEncoder LabelEncoder = new LabelEncoder(y_);
        labelEncoder.updateEncoder(y_);
        int[] y_encoded = labelEncoder.encode(y_);

        //data = new DataFrame(new IntVector(y, y_encoded));
        data = data.drop(y);
        data.add(new IntVector(y, y_encoded));

        // ArrayList<HashMap<String, String>> x_ = fetcher.fetchRawDataMap(String.format(command, 0));

        Formula formula = Formula.lhs(y);

        int size = (int) (data.size() * test);

        data_test = splitDataTest(size, data);
        data_train = splitDataTrain(size, data);

        smile.classification.RandomForest model = smile.classification.RandomForest.fit(formula, data_train);

        accuracy.set(Accuracy.of(data_test.column(y).stream().mapToInt(n -> ((Number) n).intValue()).toArray(), model.predict(data_test.drop(y))));

        //smile.classification.RandomForest m2 = smile.classification.RandomForest.fit(formula, data);
        return model;
    }

    public void update(final ArrayList<HashMap<String, String>> raw_data) {
        switch (model_type) {
            case LINEAR_REGRESSION -> update_linear(raw_data);
            case RANDOM_FOREST_REGRESSION -> update_forest(raw_data);
            case GRADIENT_REGRESSION -> update_GradientLinear(raw_data);
            case TREE_REGRESSION -> update_RegressionTree(raw_data);
            case LOGISTIC_REGRESSION, LOGISTIC_BINOMIAL_REGRESSION, LOGISTIC_MULTIMODAL_REGRESSION -> updateLogistic(raw_data);
            case RANDOM_FOREST_CLASSIFICATION -> update_ForestClassification(raw_data);
            case GRADIENT_CLASSIFICATION -> update_GradientClassifier(raw_data);
        }
    }

    private void update_GradientLinear(final ArrayList<HashMap<String, String>> raw_data) {
        DataFrame data = processData(raw_data);

        final int size_ = (int) (data.size() * size);

        DataFrame data_test = splitDataTest(size_, data);
        DataFrame data_train = splitDataTrain(size_, data);

        ArrayList<SampleInstance<Tuple, Double>> instance = new ArrayList<>();

        for (Row row : data_train) {
            instance.add(new SampleInstance<>(row.getStruct(y), row.getDouble(1)));
        }

        Dataset<Tuple, Double> dataset = new SimpleDataset<>(instance);

        GradientTreeBoost model = (GradientTreeBoost) this.model;

        model.update(dataset);

        accuracy.set(Accuracy.of(Arrays.stream(data_test.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(data_test.drop(y))).mapToInt(n -> ((Number) n).intValue()).toArray()));
    }

    private void update_RegressionTree(final ArrayList<HashMap<String, String>> raw_data) {
        DataFrame data = processData(raw_data);

        final int size_ = (int) (data.size() * size);

        DataFrame data_test = splitDataTest(size_, data);
        DataFrame data_train = splitDataTrain(size_, data);

        ArrayList<SampleInstance<Tuple, Double>> instance = new ArrayList<>();

        for (Row row : data_train) {
            instance.add(new SampleInstance<>(row.getStruct(y), row.getDouble(1)));
        }

        Dataset<Tuple, Double> dataset = new SimpleDataset<>(instance);

        RegressionTree model = (RegressionTree) this.model;

        model.update(dataset);

        accuracy.set(Accuracy.of(Arrays.stream(data_test.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(data_test.drop(y))).mapToInt(n -> ((Number) n).intValue()).toArray()));
    }

    private void update_linear(final ArrayList<HashMap<String, String>> raw_data) {
        DataFrame data = processData(raw_data);

        final int size_ = (int) (data.size() * size);

        DataFrame data_test = splitDataTest(size_, data);
        DataFrame data_train = splitDataTrain(size_, data);

        LinearModel model = (LinearModel) this.model;

        model.update(data_train);

        accuracy.set(Accuracy.of(Arrays.stream(data_test.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(data_test.drop(y))).mapToInt(n -> ((Number) n).intValue()).toArray()));
    }

    private void updateLogistic(final ArrayList<HashMap<String, String>> raw_data) {
        DataFrame data = processData(raw_data);

        DataFrame trainData = splitDataTrain(size, data);
        DataFrame testData = splitDataTest(size, data);

        LogisticRegression model = (LogisticRegression) this.model;

        model.update(trainData.toArray(schema.toArray(new String[0])), Arrays.stream(trainData.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray());

        accuracy.set(Accuracy.of(Arrays.stream(testData.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(testData.drop(y).toArray(schema.toArray(new String[0])))).map(n -> ((Number) n).intValue()).toArray()));
    }

    private void update_GradientClassifier(final ArrayList<HashMap<String, String>> raw_data) {
        DataFrame data = processData(raw_data);

        final int size_ = (int) (data.size() * size);

        DataFrame data_test = splitDataTest(size_, data);
        DataFrame data_train = splitDataTrain(size_, data);

        ArrayList<SampleInstance<Tuple, Integer>> instance = new ArrayList<>();

        for (Row row : data_train) {
            instance.add(new SampleInstance<>( (Tuple) row, row.getInt(y)));
        }

        Dataset<Tuple, Integer> dataset = new SimpleDataset<>(instance);

        smile.classification.GradientTreeBoost model = (smile.classification.GradientTreeBoost) this.model;

        model.update(dataset);

        accuracy.set(Accuracy.of(Arrays.stream(data_test.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(data_test.drop(y))).map(n -> ((Number) n).intValue()).toArray()));
    }

    private void update_forest(final ArrayList<HashMap<String, String>> raw_data) {
        DataFrame data = processData(raw_data);

        final int size_ = (int) (data.size() * size);

        DataFrame data_test = splitDataTest(size_, data);
        DataFrame data_train = splitDataTrain(size_, data);

        ArrayList<SampleInstance<Tuple, Double>> instance = new ArrayList<>();

        for (Row row : data_train) {
            instance.add(new SampleInstance<>(row.getStruct(y), row.getDouble(1)));
        }

        Dataset<Tuple, Double> dataset = new SimpleDataset<>(instance);

        RandomForest model = (RandomForest) this.model;

        model.update(dataset);

        accuracy.set(Accuracy.of(Arrays.stream(data_test.column(y).toDoubleArray()).mapToInt(n -> ((Number) n).intValue()).toArray(), Arrays.stream(model.predict(data_test.drop(y))).mapToInt(n -> ((Number) n).intValue()).toArray()));
    }

    private void update_ForestClassification(final ArrayList<HashMap<String, String>> raw_data) {
        smile.classification.RandomForest model_ = TrainClassificationForest(raw_data);

        smile.classification.RandomForest model = (smile.classification.RandomForest) this.model;

        model.merge(model_);
    }

    public void setKNN(int knnTree) {
        this.knnTree = knnTree;
    }

    public Map<String, Object> getModelMetrics() {

        Map<String, Object> metrics = null;

        switch (model_type) {
            case LINEAR_REGRESSION -> metrics = getLinearMetrics();
            case RANDOM_FOREST_REGRESSION -> metrics = getRandomForestRegressionMetrics();
            case GRADIENT_REGRESSION -> metrics = getGradientRegressionMetrics();
            case TREE_REGRESSION -> metrics = getTreeRegressionMetrics();
            case LOGISTIC_REGRESSION, LOGISTIC_BINOMIAL_REGRESSION, LOGISTIC_MULTIMODAL_REGRESSION
                    -> metrics = getLogisticMetrics();
            case RANDOM_FOREST_CLASSIFICATION -> metrics = getRandomForestClassificationMetrics();
            case GRADIENT_CLASSIFICATION -> metrics = getGradientClassificationMetrics();
            case KNN -> metrics = getKnnMetrics();
        }

        return metrics;
    }

    private Map<String, Object> getLinearMetrics() {
        Map<String, Object> metric = new HashMap<>();
        LinearModel model = (LinearModel) this.model;
        metric.put("Intercept", model.intercept());
        metric.put("Coefficient", Arrays.toString(model.coefficients()));
        metric.put("Error", model.error());
        metric.put("F-test", model.ftest());
        metric.put("P-value", model.pvalue());
        metric.put("R-squared", model.RSquared());
        metric.put("R-adjusted-squared", model.adjustedRSquared());
        metric.put("RSS", model.RSS());
        return metric;
    }

    private Map<String, Object> getRandomForestRegressionMetrics() {
        Map<String, Object> metric = new HashMap<>();
        smile.regression.RandomForest rf = (smile.regression.RandomForest) this.model;
        // OOB aggregated regression metrics (se disponível)
        RegressionMetrics rm = rf.metrics(); // OOB regression metrics (RSS, MSE, RMSE, MAD, R2)
        metric.put("R2", rm.r2());
        metric.put("MSE", rm.mse());
        metric.put("RMSE", rm.rmse());
        metric.put("MAD", rm.mad());
        metric.put("RSS", rm.rss());
        metric.put("Importance", rf.importance()); // feature importance
        metric.put("NumTrees", rf.size());
        return metric;
    }

    private Map<String, Object> getRandomForestClassificationMetrics() {
        Map<String, Object> metric = new HashMap<>();
        smile.classification.RandomForest rf = (smile.classification.RandomForest) this.model;
        // OOB aggregated classification metrics (accuracy, f1, auc, logloss, ...)
        ClassificationMetrics cm = rf.metrics();
        metric.put("Accuracy", cm.accuracy());
        metric.put("Errors", cm.error());
        metric.put("Precision", cm.precision());
        metric.put("Recall (Sensitivity)", cm.sensitivity());
        metric.put("Specificity", cm.specificity());
        metric.put("F1", cm.f1());
        metric.put("MCC", cm.mcc());
        metric.put("AUC", cm.auc());
        metric.put("LogLoss", cm.logloss());
        metric.put("Importance", rf.importance());
        metric.put("NumTrees", rf.size());
        return metric;
    }

    private Map<String, Object> getGradientRegressionMetrics() {
        Map<String, Object> metric = new HashMap<>();
        // Para Gradient boosting de regressão o objecto costuma ser smile.regression.GradientTreeBoost
        if (this.model instanceof smile.regression.GradientTreeBoost gbr) {
            metric.put("Importance", gbr.importance());
            metric.put("NumTrees", gbr.size());
        } else {
            metric.put("info", "Gradient regression model not instance of GradientTreeBoost");
        }
        return metric;
    }

    private Map<String, Object> getGradientClassificationMetrics() {
        Map<String, Object> metric = new HashMap<>();
        smile.classification.GradientTreeBoost gtb = (smile.classification.GradientTreeBoost) this.model;
        metric.put("Importance", gtb.importance());
        metric.put("NumTrees", gtb.size());
        metric.put("IsSoftClassifier", gtb.soft());
        // gtb.test(data) pode ser usado para avaliar com um DataFrame de validação
        return metric;
    }

    private Map<String, Object> getTreeRegressionMetrics() {
        Map<String, Object> metric = new HashMap<>();
        if (this.model instanceof RegressionTree tree) {
            metric.put("Importance", tree.importance());
            metric.put("Size", tree.size());
            // não existe "metrics()" OOB por defeito, para métricas de validação usa o helper abaixo
        } else {
            metric.put("info", "Model is not RegressionTree");
        }
        return metric;
    }

    private Map<String, Object> getLogisticMetrics() {
        Map<String, Object> metric = new HashMap<>();
        // SMILE logistic tem classes Binomial / Multinomial com method coefficients()
        if (this.model instanceof smile.classification.LogisticRegression.Binomial bin) {
            metric.put("Coefficients", bin.coefficients()); // último elemento é bias
            metric.put("LogLikelihood", bin.loglikelihood());
            metric.put("AIC", bin.AIC());
        } else if (this.model instanceof LogisticRegression.Multinomial multin) {
            metric.put("CoefficientsMatrix", multin.coefficients());
            metric.put("LogLikelihood", multin.loglikelihood());
            metric.put("AIC", multin.AIC());
        } else {
            metric.put("info", "Model not recognized as LogisticRegression binomial/multinomial");
        }
        return metric;
    }

    private Map<String, Object> getKnnMetrics() {
        Map<String, Object> metric = new HashMap<>();
        // KNN não tem métricas internas — usa validação externa (see helpers below)
        metric.put("info", "KNN: use validateClassification/validateRegression with a test set to compute metrics");
        return metric;
    }

    public void exportModel(final String name) throws Exception {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(name+".ser"))) {
            oos.writeObject(model);
            oos.flush();
            System.out.println("Model exported successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e);
        }
    }

}
