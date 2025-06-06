package org.example;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.base.DeliveryGuarantee;

public class AiJob {

    public static void main(String[] args) throws Exception {
        // 1) Flink env
        final StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("kafka:29092")
                .setTopics("questions")
                .setGroupId("rag-question-consumer")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers("kafka:29092")
                .setRecordSerializer(
                    KafkaRecordSerializationSchema.builder()
                        .setTopic("answers")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();

        // 2) Source
        DataStream<String> questions =
                env.fromSource(kafkaSource,
                               WatermarkStrategy.noWatermarks(),
                               "Kafka Questions")
                .name("Kafka Source")
                .uid("KafkaSource")
                .disableChaining();


        // 3) Transformation
        DataStream<String> answers = questions
                .map(RAG::getAnswer)
                .name("RAG Transformation")
                .uid("RAG")
                .disableChaining();

        // 4) Kafka sink  (topic auto-created: answers)
        answers.sinkTo(kafkaSink)
                .name("Kafka Sink")
                .uid("KafkaSink");

        env.execute("Kafka → AiJob → Kafka");
    }
}