package net.geoprism.geoai.explorer.core.service;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessContentBlockStart;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessConversationRole;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessMessage;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessMessageStartEvent;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessMessageStopEvent;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessSystemContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessTool;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolResultBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolResultContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolUseStatus;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolUseType;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeHarnessRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeHarnessResponseHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class BedrockService
{
  private static final int MAX_TOOL_ROUNDS = 10;

  private record ToolCall(String toolUseId, String name, StringBuilder input) {}

  private static final int MAX_TIMEOUT_MINUTES = 5;

  private static class HarnessTurn
  {
    final StringBuilder          text             = new StringBuilder();

    final Map<Integer, ToolCall> messageToolCalls = new ConcurrentHashMap<>();

    volatile List<ToolCall>      pendingToolCalls = List.of();

    volatile String              stopReason;

    volatile String              finalMessage     = "";
  }

  private static final Logger log =
      LoggerFactory.getLogger(BedrockService.class);

  private static final Pattern LOCATION_NAME_PATTERN =
      Pattern.compile(
          ".*<name>(.*?)<\\/name>.*",
          Pattern.DOTALL
      );
  
  private static final Pattern REASONING_PATTERN =
      Pattern.compile(
          "<reasoning\\b[^>]*>(.*?)</reasoning\\s*>",
          Pattern.DOTALL | Pattern.CASE_INSENSITIVE
      );

  @Autowired
  private AppProperties properties;
  
  @Autowired
  private ChatPromptService chatPromptService;
  
  @Autowired
  private MapItPromptService mapItPromptService;

  @Autowired
  private ToolService toolService;
  
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
        inputText,
        toolService.getDefinitions()
    );
    
    String reasoning = extractReasoning(value);
    value = stripReasoning(value);

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
    
    message.setReasoning(reasoning);

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
  
  private String extractReasoning(String response)
  {
      Matcher matcher = REASONING_PATTERN.matcher(response);

      if (matcher.find())
      {
          return matcher.group(1).trim();
      }

      return null;
  }

  private String stripReasoning(String response)
  {
      return REASONING_PATTERN
          .matcher(response)
          .replaceAll("")
          .trim();
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
        text,
        new LinkedList<>()
    );

    /*
     * Retaining the workaround from the old implementation.
     */
    response = response.replace("<REDACTED>", "sparql");

    return stripCodeFence(response);
  }


private String invokeHarness(String harnessArn, String endpoint, String sessionId,
    String systemPrompt, String inputText, List<HarnessTool> tools)
    throws InterruptedException, ExecutionException, TimeoutException
{
  validateHarnessConfiguration(harnessArn);
  validateSessionId(sessionId);

  HarnessMessage nextMessage = HarnessMessage.builder() //
      .role(HarnessConversationRole.USER) //
      .content(HarnessContentBlock.builder().text(inputText).build()) //
      .build();

  try (BedrockAgentCoreAsyncClient client = getClient())
  {
    for (int round = 0; round < MAX_TOOL_ROUNDS; round++)
    {
      InvokeHarnessRequest.Builder requestBuilder = InvokeHarnessRequest.builder()
          .harnessArn(harnessArn)
          .runtimeSessionId(sessionId)
          .systemPrompt(HarnessSystemContentBlock.builder().text(systemPrompt).build())
          .tools(tools)
          .messages(nextMessage);

      if (endpoint != null && !endpoint.isBlank())
      {
        requestBuilder.qualifier(endpoint);
      }

      HarnessTurn turn = streamHarness(client, requestBuilder.build());

      if (!"tool_use".equals(turn.stopReason) || turn.pendingToolCalls.isEmpty())
      {
        if (turn.finalMessage.isBlank())
        {
          throw new GenericRestException(
              "AgentCore harness completed without returning a final assistant message.");
        }
        return turn.finalMessage;
      }

      nextMessage = HarnessMessage.builder()
          .role(HarnessConversationRole.USER)
          .content(turn.pendingToolCalls.stream().map(this::executeToolCall).toList())
          .build();
    }
  }

  throw new GenericRestException("AgentCore harness exceeded the maximum number of tool rounds.");
}

private HarnessTurn streamHarness(BedrockAgentCoreAsyncClient client, InvokeHarnessRequest request)
    throws InterruptedException, ExecutionException, TimeoutException
{
  HarnessTurn turn = new HarnessTurn();

  InvokeHarnessResponseHandler handler = InvokeHarnessResponseHandler.builder()
      .onEventStream(publisher -> publisher.subscribe(event -> {
        if (event instanceof HarnessMessageStartEvent)
        {
          turn.text.setLength(0);
          turn.messageToolCalls.clear();
        }
        else if (event instanceof HarnessContentBlockStartEvent startEvent)
        {
          HarnessContentBlockStart start = startEvent.start();

          if (start != null && start.toolUse() != null)
          {
            turn.messageToolCalls.put(startEvent.contentBlockIndex(),
                new ToolCall(start.toolUse().toolUseId(), start.toolUse().name(), new StringBuilder()));
          }
        }
        else if (event instanceof HarnessContentBlockDeltaEvent deltaEvent)
        {
          HarnessContentBlockDelta delta = deltaEvent.delta();

          if (delta == null)
          {
            return;
          }

          if (delta.type() == HarnessContentBlockDelta.Type.TEXT && delta.text() != null)
          {
            turn.text.append(delta.text());
          }
          else if (delta.type() == HarnessContentBlockDelta.Type.TOOL_USE && delta.toolUse() != null)
          {
            ToolCall call = turn.messageToolCalls.get(deltaEvent.contentBlockIndex());

            if (call != null && delta.toolUse().input() != null)
            {
              call.input().append(delta.toolUse().input());   // JSON arrives in fragments
            }
          }
        }
        else if (event instanceof HarnessMessageStopEvent stopEvent)
        {
          turn.stopReason = stopEvent.stopReasonAsString();
          turn.pendingToolCalls = List.copyOf(turn.messageToolCalls.values());

          if ("end_turn".equals(turn.stopReason))
          {
            turn.finalMessage = turn.text.toString().trim();
          }
        }
      }))
      .onError(error -> log.error("Error invoking AgentCore harness", error))
      .build();

  client.invokeHarness(request, handler).get(MAX_TIMEOUT_MINUTES, TimeUnit.MINUTES);

  return turn;
}

private HarnessContentBlock executeToolCall(ToolCall call)
{
  HarnessToolUseStatus status = HarnessToolUseStatus.SUCCESS;
  String output;

  try
  {
    JsonNode input = JsonMapper.shared().readTree(call.input().isEmpty() ? "{}" : call.input().toString());
    output = toolService.execute(call.name(), input);
  }
  catch (Exception e)
  {
    log.error("Inline tool {} failed", call.name(), e);
    status = HarnessToolUseStatus.ERROR;
    output = "Tool error: " + e.getMessage();   // let the model see the failure and recover
  }

  return HarnessContentBlock.builder()
      .toolResult(HarnessToolResultBlock.builder()
          .toolUseId(call.toolUseId())
          .type(HarnessToolUseType.TOOL_USE)
          .status(status)
          .content(HarnessToolResultContentBlock.builder().text(output).build())
          .build())
      .build();
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