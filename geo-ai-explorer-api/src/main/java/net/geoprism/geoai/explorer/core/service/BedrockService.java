package net.geoprism.geoai.explorer.core.service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.config.AppProperties;
import net.geoprism.geoai.explorer.core.model.GenericRestException;
import net.geoprism.geoai.explorer.core.model.History;
import net.geoprism.geoai.explorer.core.model.Message;
import net.geoprism.geoai.explorer.core.service.prompt.ChatPromptService;
import net.geoprism.geoai.explorer.core.service.prompt.MapItPromptService;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessContentBlockDelta;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessConversationRole;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessMessage;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessMessageStartEvent;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessMessageStopEvent;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessSystemContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeHarnessRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeHarnessResponseHandler;

@Service
public class BedrockService
{
  private static final int MAX_TIMEOUT_MINUTES = 5;

  private static final Logger log =
      LoggerFactory.getLogger(BedrockService.class);

  private static final Pattern LOCATION_NAME_PATTERN =
      Pattern.compile(
          ".*<name>(.*?)<\\/name>.*",
          Pattern.DOTALL
      );

  @Autowired
  private AppProperties properties;
  
  @Autowired
  private ChatPromptService chatPromptService;
  
  @Autowired
  private MapItPromptService mapItPromptService;

  public Message prompt(
      String sessionId,
      String inputText
  ) throws InterruptedException, ExecutionException, TimeoutException
  {
    String harnessSessionId = normalizeSessionId(sessionId);

    String value = invokeHarness(
        properties.getChatAgentHarnessArn(),
        properties.getChatAgentHarnessEndpoint(),
        harnessSessionId,
        chatPromptService.getPrompt(),
        inputText
    );

    Matcher matcher =
        LOCATION_NAME_PATTERN.matcher(value);

    boolean locationFound = matcher.find();

    boolean mappable =
        value.contains("#mapit");

    boolean ambiguous =
        !mappable &&
        (
            (
                locationFound &&
                value.toLowerCase().contains("#ambiguous")
            ) ||
            value.toLowerCase().contains("i found multiple")
        );

    Message message = new Message();

    message.setContent(
        value
            .replace("#mapit", "")
            .replace("#ambiguous", "")
            .replaceFirst("<name>(.*?)<\\/name>", "")
            .trim()
    );

    /*
     * Preserve your application's original session ID rather than exposing
     * the normalized AgentCore session ID.
     */
    message.setSessionId(sessionId);
    message.setMappable(mappable);
    message.setAmbiguous(ambiguous);

    if (locationFound)
    {
      message.setLocation(matcher.group(1));
    }

    return message;
  }

  public String getLocationSparql(
      History history
  ) throws InterruptedException, ExecutionException, TimeoutException
  {
    String text = history.toText();

    log.info(
        "Invoking SPARQL AgentCore harness {} with text: {}",
        properties.getSparqlAgentHarnessArn(),
        text
    );

    String response = invokeHarness(
        properties.getSparqlAgentHarnessArn(),
        properties.getSparqlAgentHarnessEndpoint(),
        UUID.randomUUID().toString(),
        mapItPromptService.getPrompt(),
        text
    );

    /*
     * Retaining the workaround from the old implementation.
     */
    response = response.replace("<REDACTED>", "sparql");

    return stripCodeFence(response);
  }

  private String invokeHarness(
      String harnessArn,
      String endpoint,
      String sessionId,
      String systemPrompt,
      String inputText
  ) throws InterruptedException, ExecutionException, TimeoutException
  {
    validateHarnessConfiguration(harnessArn);
    validateSessionId(sessionId);

    HarnessMessage userMessage =
        HarnessMessage.builder()
            .role(HarnessConversationRole.USER)
            .content(
                HarnessContentBlock.builder()
                    .text(inputText)
                    .build()
            )
            .build();

    InvokeHarnessRequest.Builder requestBuilder =
        InvokeHarnessRequest.builder()
            .harnessArn(harnessArn)
            .runtimeSessionId(sessionId)
            .systemPrompt(HarnessSystemContentBlock.builder().text(systemPrompt).build()
            )
            .messages(userMessage);

    if (endpoint != null && !endpoint.isBlank())
    {
      requestBuilder.qualifier(endpoint);
    }

    InvokeHarnessRequest request = requestBuilder.build();

    StringBuilder currentMessage = new StringBuilder();
    AtomicReference<String> finalMessage =
        new AtomicReference<>("");

    InvokeHarnessResponseHandler handler =
        InvokeHarnessResponseHandler.builder()
            .onResponse(response -> {
              log.info(
                  "Response received from AgentCore harness: {}",
                  response
              );
            })
            .onEventStream(publisher -> {
              publisher.subscribe(event -> {
                log.debug(
                    "AgentCore harness event: {}",
                    event.sdkEventType()
                );

                if (event instanceof HarnessMessageStartEvent)
                {
                  currentMessage.setLength(0);
                }
                else if (
                    event instanceof HarnessContentBlockDeltaEvent deltaEvent
                )
                {
                  HarnessContentBlockDelta delta =
                      deltaEvent.delta();

                  if (delta != null &&
                      delta.type() ==
                          HarnessContentBlockDelta.Type.TEXT &&
                      delta.text() != null)
                  {
                    currentMessage.append(delta.text());
                  }
                }
                else if (
                    event instanceof HarnessMessageStopEvent stopEvent
                )
                {
                  String stopReason =
                      stopEvent.stopReasonAsString();

                  log.debug(
                      "Harness message completed: {}",
                      stopReason
                  );

                  if ("end_turn".equals(stopReason))
                  {
                    finalMessage.set(
                        currentMessage.toString().trim()
                    );
                  }

                  currentMessage.setLength(0);
                }
              });
            })
            .onError(error -> {
              log.error(
                  "Error invoking AgentCore harness",
                  error
              );
            })
            .build();

    try (BedrockAgentCoreAsyncClient client = getClient())
    {
      client.invokeHarness(request, handler)
          .get(
              MAX_TIMEOUT_MINUTES,
              TimeUnit.MINUTES
          );
    }

    String result = finalMessage.get();

    if (result == null || result.isBlank())
    {
      throw new GenericRestException(
          "AgentCore harness completed without returning a final assistant message."
      );
    }

    return result;
  }

  private void appendTextDelta(
      StringBuilder content,
      HarnessContentBlockDeltaEvent event
  )
  {
    HarnessContentBlockDelta delta =
        event.delta();

    if (delta == null)
    {
      return;
    }

    String text = delta.text();

    if (text != null)
    {
      content.append(text);
    }
  }

  private String normalizeSessionId(String sessionId)
  {
    /*
     * AgentCore harness session IDs must contain 33–100 characters and
     * match [a-zA-Z0-9][a-zA-Z0-9-_]*.
     */
    if (isValidSessionId(sessionId))
    {
      return sessionId;
    }

    String prefix =
        sessionId == null || sessionId.isBlank()
            ? "session"
            : sessionId.replaceAll(
                "[^a-zA-Z0-9_-]",
                "_"
            );

    if (!Character.isLetterOrDigit(prefix.charAt(0)))
    {
      prefix = "session-" + prefix;
    }

    /*
     * Avoid exceeding AgentCore's 100-character maximum after adding
     * the UUID.
     */
    int maximumPrefixLength =
        100 - 1 - 36;

    if (prefix.length() > maximumPrefixLength)
    {
      prefix =
          prefix.substring(
              0,
              maximumPrefixLength
          );
    }

    return prefix + "-" + UUID.randomUUID();
  }

  private boolean isValidSessionId(String sessionId)
  {
    return sessionId != null &&
        sessionId.length() >= 33 &&
        sessionId.length() <= 100 &&
        sessionId.matches(
            "[a-zA-Z0-9][a-zA-Z0-9-_]*"
        );
  }

  private void validateHarnessConfiguration(
      String harnessArn
  )
  {
    if (harnessArn == null || harnessArn.isBlank())
    {
      throw new GenericRestException(
          "The Bedrock AgentCore harness ARN must be configured before invocation."
      );
    }
  }

  private void validateSessionId(
      String sessionId
  )
  {
    if (!isValidSessionId(sessionId))
    {
      throw new GenericRestException(
          "The AgentCore harness session ID must contain between " +
          "33 and 100 characters and may contain only letters, " +
          "numbers, hyphens and underscores."
      );
    }
  }

  private String stripCodeFence(
      String response
  )
  {
    String text = response.trim();

    if (text.startsWith("```"))
    {
      text = text.replaceFirst(
          "^```(?:sparql|sql)?\\s*",
          ""
      );

      text = text.replaceFirst(
          "\\s*```$",
          ""
      );

      text = text.trim();
    }

    return text;
  }

  private BedrockAgentCoreAsyncClient getClient()
  {
    Duration timeout =
        Duration.ofMinutes(MAX_TIMEOUT_MINUTES);

    return BedrockAgentCoreAsyncClient.builder()
        .region(properties.getBedrockRegion())
        .credentialsProvider(
            properties.getCredentialsProvider()
        )
        .httpClientBuilder(
            NettyNioAsyncHttpClient.builder()
                .readTimeout(timeout)
        )
        .overrideConfiguration(configuration -> {
          configuration.apiCallTimeout(timeout);
          configuration.apiCallAttemptTimeout(timeout);
        })
        .build();
  }
}