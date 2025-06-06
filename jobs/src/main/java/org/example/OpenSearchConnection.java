package org.example;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.example.utils.Variables;

public class OpenSearchConnection {

    // Embedded connection information
    private static final String HOST = Variables.OPENSEARCH_HOST;
    private static final int PORT = Variables.OPENSEARCH_PORT;
    private static final String USER_NAME = Variables.OPENSEARCH_USER_NAME;
    private static final String PASSWORD = Variables.OPENSEARCH_PASSWORD;

    public OpenSearchClient createClient() {
        // Create a CredentialsProvider to authenticate
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USER_NAME, PASSWORD));

        // Create the RestClient with the HttpHost and HttpClient
        RestClient restClient = RestClient.builder(new HttpHost(HOST, PORT, "http"))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                .build();

        // Create the transport with a Jackson mapper
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

        // Create and return the OpenSearchClient
        return new OpenSearchClient(transport);
    }
}

