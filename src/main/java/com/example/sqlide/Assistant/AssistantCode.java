package com.example.sqlide.Assistant;

import ai.djl.Application;
import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.engine.Engine;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.generate.TextGenerator;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.*;
import org.apache.poi.ss.formula.functions.Mode;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AssistantCode {

    private ZooModel<String, String> model;

    private HuggingFaceTokenizer tokenizer;

    Translator<String, String> translator = new Translator<String, String>() {


        @Override
        public NDList processInput(TranslatorContext ctx, String input) throws Exception {
            NDManager manager = ctx.getNDManager();
            Encoding enc = tokenizer.encode(input);

            NDArray inputIds = manager.create(enc.getIds()).expandDims(0);
            NDArray attentionMask = manager.create(enc.getAttentionMask()).expandDims(0);
            NDArray decoderInputIds = manager.create(new long[]{0}).expandDims(0).toType(DataType.INT64, false);

            return new NDList(inputIds, attentionMask, decoderInputIds);
          //  return enc.toNDList(manager, false, false);


        }

        @Override
        public String processOutput(TranslatorContext ctx, NDList list) throws Exception {
            System.out.println("hihi");
            NDArray outputArray = list.singletonOrThrow();
            System.out.println("Raw Output Shape: " + outputArray.getShape());

            // Simplificação para decodificação (pode precisar de ajuste para geração real)
            long[] outputTokenIds = outputArray.argMax(-1).toLongArray(); // (1, seq_len_out)
            System.out.println("Output Token IDs (argmax): " + java.util.Arrays.toString(outputTokenIds));

            String generatedText = tokenizer.decode(outputTokenIds);
            return generatedText;
        }

        @Override
        public Batchifier getBatchifier() {
            // Para modelos que exigem entrada em lote específicas, o padrão STACK pode não ser suficiente.
            // Se você passar múltiplos NDArrays (input_ids, attention_mask), o batchifier precisa saber como empilhá-los.
            // STACK tenta empilhar cada NDArray na lista separadamente.
            // Isso *pode* funcionar, mas às vezes um Batchifier personalizado é necessário.
            // Por enquanto, vamos tentar com STACK.
            return Batchifier.STACK;
            // Se ainda tiver problemas, pode ser necessário implementar um Batchifier personalizado.
        }
    };

    public AssistantCode() {

        final Engine engine = Engine.getInstance();

        final Device dev = engine.getGpuCount() >= 0 ? Device.gpu().getDevices().getFirst() : Device.cpu().getDevices().getFirst();

        System.out.println(dev.toString());

        //model = Model.newInstance(Paths.get("model/sql-codet5").toString(), dev, engine.getEngineName());

       // System.out.println(model.getProperties().isEmpty());

        System.out.println(Paths.get("model/sql-codet5").toString());

       // System.out.println(model.getBlock());





      /*  Criteria<String, String> criteria = Criteria.builder()
                .setTypes(String.class, String.class) // Entrada e saída como String
                .optModelPath(Paths.get("model/sql-codet5"))          // Caminho para o diretório do modelo Hugging Face local
                .optEngine("PyTorch")                // Especificar a engine
                // Usar o Translator padrão do Hugging Face para modelos Text2Text (como Codet5)
                .optTranslator(translator)
                .build();
        System.out.println("Criteria setup complete."); */



        try {

            Path modelDirPath = Paths.get("model/sql-codet5"); // "sqlidefx/model/sql-codet5"
            Path tokenizerJsonPath = modelDirPath.resolve("tokenizer.json");
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(modelDirPath)
                    .build();



            Criteria<String, String> criteria = Criteria.builder()
                    .setTypes(String.class, String.class) // Entrada e saída como String
                    .optApplication(Application.NLP.TEXT_GENERATION) // Especifica a aplicação
                    .optModelPath(Paths.get("model/sql-codet5"))          // Caminho para o diretório do modelo Hugging Face local
              //      .optModelPath(Paths.get("models/codet5-base"))
                //    .optModelName("Salesforce/codet5-base")
                  //  .optModelUrls("SalesForce/codet5-base")
                    .optEngine("OnnxRuntime")                // Especificar a engine
                    // .optGroupId("ai.djl.localmodelzoo")
                    .optTranslator(translator)
                    // Usar o Translator padrão do Hugging Face para modelos Text2Text (como Codet5)
                    .optProgress(new ProgressBar())
                   // .optTokenizer(this.tokenizer) // O translator tentará carregar o tokenizer associado ao modelo
                    // Você pode precisar configurar opções específicas de geração aqui

                    .build();
            System.out.println("Criteria setup complete.");

            model = criteria.loadModel();

            System.out.println(model.getBlock());

            Predictor<String, String> predictor = model
                    .newPredictor(translator);
            System.out.println(predictor.predict("SELECT * FROM "));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String predict(final String prompt) throws TranslateException {
        try {
            Predictor<String, String> predictor = model
                    .newPredictor(translator);
            System.out.println(predictor.predict(prompt));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "predictor.predict(prompt)";
    }

}
