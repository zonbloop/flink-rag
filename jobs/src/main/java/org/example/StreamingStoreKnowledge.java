package org.example;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.example.utils.EmbeddingConfig;
import org.example.utils.Variables;

import org.opensearch.client.opensearch.OpenSearchClient;

public class StreamingStoreKnowledge {

    public static void main(String[] args) throws Exception {

        // 1) Flink env
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> src = KafkaSource.<String>builder()
                .setBootstrapServers("kafka:29092")
                .setTopics("knowledge_updates")
                .setGroupId("embedding-ingestor")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(
                        new org.apache.flink.api.common.serialization.SimpleStringSchema())
                .build();

        // 2) Source
        DataStream<String> updates = env.fromSource( src, WatermarkStrategy.noWatermarks(), "Kafka-Knowledge")
                .name("Kafka Source")
                .uid("KafkaSource")
                .disableChaining();


        // 3) Transformation
        DataStream<Tuple2<Embedding, TextSegment>> embedded = updates
                .map(new Embedder())
                .name("Embedding")
                .uid("Embed")
                .disableChaining();

        // 4) Sink
        embedded.addSink(new RichSinkFunction<Tuple2<Embedding,TextSegment>>() {

            private transient EmbeddingStore<TextSegment> store;

            @Override
            public void open(
                    org.apache.flink.configuration.Configuration parameters) {
                OpenSearchClient client = new OpenSearchConnection().createClient();
                store = OpenSearchEmbeddingStore.builder()
                        .openSearchClient(client)
                        .indexName(EmbeddingConfig.INDEX_NAME)
                        .build();
            }

            @Override
            public void invoke(Tuple2<Embedding,TextSegment> value, Context ctx) {
                store.add(value.f0, value.f1);
            }
        })
        .name("Sink")
        .uid("Sink");

        env.execute("Kafka → Embedding → OpenSearch");
    }

    /** RichMapFunction that embeds each String with OpenAI once per task */
    public static class Embedder
            extends RichMapFunction<String, Tuple2<Embedding, TextSegment>> {

        private transient EmbeddingModel model;

        @Override
        public void open(
                org.apache.flink.configuration.Configuration parameters) {
            model = OpenAiEmbeddingModel.withApiKey(Variables.OPENAI_API_KEY);
        }

        @Override
        public Tuple2<Embedding, TextSegment> map(String value) {
            TextSegment segment = TextSegment.from(value);
            Embedding embedding = model.embed(segment).content();
            return Tuple2.of(embedding, segment);
        }
    }
}
