package org.example;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.example.utils.EmbeddingConfig;
import org.example.utils.Variables;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class RAG {
    public static String getAnswer(String question) {
        // Embed segments (convert them into vectors that represent the meaning) using embedding model
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(Variables.OPENAI_API_KEY)
                .modelName(EmbeddingConfig.MODEL_NAME)
                .build();

        // Create OpenSearch connection
        OpenSearchConnection openSearchConnection = new OpenSearchConnection();

        // Get the OpenSearchClient from the connection class
        OpenSearchClient client = openSearchConnection.createClient();
        EmbeddingStore<TextSegment> embeddingStore = OpenSearchEmbeddingStore.builder()
                .openSearchClient(client)
                .indexName(EmbeddingConfig.INDEX_NAME)
                .build();

        // Embed the question
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // Find relevant embeddings in embedding store by semantic similarity
        // You can play with parameters below to find a sweet spot for your specific use case
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(10)                    // how many top neighbors to return
                .minScore(0.0)
                .build();

        // 3) Execute the search: this returns scores + TextSegments
        EmbeddingSearchResult<TextSegment> searchResult =
                embeddingStore.search(request);

        // 4) Extract the matches
        List<EmbeddingMatch<TextSegment>> relevantEmbeddings =
                searchResult.matches();


        // Create a prompt for the model that includes question and relevant embeddings
        PromptTemplate promptTemplate = PromptTemplate.from(
                "You are Blip-Blop, an expert home-care assistant. Strictly follow ALL provided context. If the context is insufficient, answer “I don’t know:\n"
                        + "\n"
                        + "Question:\n"
                        + "{{question}}\n"
                        + "\n"
                        + "Base your answer on the following information:\n"
                        + "{{information}}");

        String information = relevantEmbeddings.stream()
                .map(match -> match.embedded().text())
                .collect(joining("\n\n"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("question", question);
        variables.put("information", information);

        Prompt prompt = promptTemplate.apply(variables);

        // Send the prompt to the OpenAI chat model
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(Variables.OPENAI_API_KEY)
                .modelName("gpt-4.1-mini-2025-04-14")
                .temperature(0.25)
                .topP(0.9)
                .maxTokens(256)
                .timeout(Duration.ofSeconds(60))
                .build();
        AiMessage aiMessage = chatModel.generate(prompt.toUserMessage()).content();

        // See an answer from the model
        return aiMessage.text();
    }
}
