package net.geoprism.geoai.explorer.core.service;

import java.net.http.HttpClient;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.amazonaws.neptune.client.jena.AwsSigningHttpClient;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

@Service
@Primary
@ConditionalOnProperty(name = "neptune.iam", havingValue = "true")
public class NeptuneQueryService extends GraphQueryService
{
  @Override
  public RDFConnection createConnection()
  {
    final AwsCredentialsProvider awsCredentialsProvider = properties.getCredentialsProvider();

    HttpClient signingClient = new AwsSigningHttpClient(
        "neptune-db",
        properties.getBedrockRegion(),
        awsCredentialsProvider,
        "SELECT * WHERE { ?s ?p ?o } LIMIT 1"
    );

    RDFConnectionRemoteBuilder builder = RDFConnectionRemote.create()
        .httpClient(signingClient)
        .destination(properties.getSparqlUrl())
        .queryEndpoint("sparql")
        .updateEndpoint("update");

    return builder.build();
  }
}