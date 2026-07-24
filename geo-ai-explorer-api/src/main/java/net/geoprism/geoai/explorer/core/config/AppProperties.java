package net.geoprism.geoai.explorer.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

@Service
public class AppProperties
{
  @Autowired
  private Environment env;

  public String getSparqlGraph()
  {
    return "https://localhost:4200/lpg/graph_801104/0#";
  }
  
  public String getLpgPrefix()
  {
    return "https://localhost:4200/lpg/rdfs#";
  }
  
  public String getChatAgentHarnessArn()
  {
    return env.getProperty("bedrock.chat.agent.harnessArn");
  }

  public String getChatAgentHarnessEndpoint()
  {
    return env.getProperty("bedrock.chat.agent.harnessEndpoint");
  }

  public String getSparqlAgentHarnessArn()
  {
    return env.getProperty("bedrock.sparql.agent.harnessArn");
  }

  public String getSparqlAgentHarnessEndpoint()
  {
    return env.getProperty("bedrock.sparql.agent.harnessEndpoint");
  }

  public Region getBedrockRegion()
  {
    String configured = getFirstNonBlankProperty(
		"aws.region",
        "bedrock.region"
    );

    if (configured != null && !configured.isBlank())
    {
      return Region.of(configured);
    }

    return DefaultAwsRegionProviderChain.builder().build().getRegion();
  }

  public Region getNeptuneRegion()
  {
    String configured = getFirstNonBlankProperty(
		"aws.region",
        "neptune.region"
    );

    if (configured != null && !configured.isBlank())
    {
      return Region.of(configured);
    }

    return DefaultAwsRegionProviderChain.builder().build().getRegion();
  }

  public String getSparqlUrl()
  {
    return env.getProperty("sparql.url");
  }
  
  public String getOpenSearchScheme()
  {
    return env.getProperty("opensearch.scheme", "https");
  }

  public String getOpenSearchHost()
  {
    return env.getProperty("opensearch.host");
  }

  public int getOpenSearchPort()
  {
    return Integer.parseInt(env.getProperty("opensearch.port", "443"));
  }

  public String getOpenSearchIndex()
  {
    return env.getProperty("opensearch.index");
  }
  
  public boolean isOpenSearchIam()
  {
    return Boolean.parseBoolean(env.getProperty("opensearch.iam", "false"));
  }
  
  public Region getOpenSearchRegion()
  {
    String configured = getFirstNonBlankProperty(
		"aws.region",
        "opensearch.region"
    );

    if (configured != null && !configured.isBlank())
    {
      return Region.of(configured);
    }

    return DefaultAwsRegionProviderChain.builder().build().getRegion();
  }
  
  public AwsCredentialsProvider getCredentialsProvider()
  {
    String accessKeyId = getFirstNonBlankProperty(
        "aws.access-key-id",
        "aws.accessKeyId",
        "aws.access_key_id"
    );

    String secretAccessKey = getFirstNonBlankProperty(
        "aws.secret-access-key",
        "aws.secretAccessKey",
        "aws.secret_access_key"
    );

    String sessionToken = getFirstNonBlankProperty(
        "aws.session-token",
        "aws.sessionToken",
        "aws.session_token"
    );

    if (isNotBlank(accessKeyId) && isNotBlank(secretAccessKey))
    {
      if (isNotBlank(sessionToken))
      {
        return StaticCredentialsProvider.create(
            AwsSessionCredentials.create(
                accessKeyId.trim(),
                secretAccessKey.trim(),
                sessionToken.trim()
            )
        );
      }

      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(
              accessKeyId.trim(),
              secretAccessKey.trim()
          )
      );
    }

    return DefaultCredentialsProvider.create();
  }

  private String getFirstNonBlankProperty(String... propertyNames)
  {
    for (String propertyName : propertyNames)
    {
      String value = env.getProperty(propertyName);

      if (isNotBlank(value))
      {
        return value;
      }
    }

    return null;
  }

  private boolean isNotBlank(String value)
  {
    return value != null && !value.isBlank();
  }

}
