package net.geoprism.geoai.explorer.core.service.prompt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import net.geoprism.geoai.explorer.core.config.AppProperties;

/**
 * Fast, deterministic guards on the chat agent prompts (no LLM calls). These
 * protect the chat agent from regressions that are cheap to catch statically:
 * prompts that fail to format, prompts that silently grow too large, and the
 * geometry / map statement rules going missing.
 */
public class ChatPromptServiceTest
{
  /*
   * Size budgets, in characters, for the fully rendered chat prompts. They are
   * intentionally tight: the chat agent's reliability is paramount, and every
   * addition to its prompt competes for its attention. If you need to grow a
   * prompt past its budget, re-run the chat agent integration tests
   * (BedrockServiceIntegrationTest, ChatAgentReliabilityIntegrationTest)
   * before raising the number.
   */
  private static final int SPN_CHAT_PROMPT_BUDGET          = 23_500;

  private static final int KALEIDOSCOPE_CHAT_PROMPT_BUDGET = 28_500;

  private static AppProperties properties()
  {
    AppProperties properties = mock(AppProperties.class);
    when(properties.getLpgPrefix()).thenReturn("https://spn.geoprism.net#");
    when(properties.getSparqlGraph()).thenReturn("https://spn.geoprism.net/spn");
    return properties;
  }

  static String spnChatPrompt()
  {
    SpnChatPromptService service = new SpnChatPromptService();
    ReflectionTestUtils.setField(service, "properties", properties());
    ReflectionTestUtils.setField(service, "metadata", new SpnMetadataService());
    return service.getPrompt();
  }

  static String kaleidoscopeChatPrompt()
  {
    KaleidoscopeChatPromptService service = new KaleidoscopeChatPromptService();
    ReflectionTestUtils.setField(service, "properties", properties());
    return service.getPrompt();
  }

  @Test
  public void testPromptsFormat()
  {
    // A stray '%' in a prompt component throws at runtime from String.formatted
    assertDoesNotThrow(ChatPromptServiceTest::spnChatPrompt);
    assertDoesNotThrow(ChatPromptServiceTest::kaleidoscopeChatPrompt);
  }

  @Test
  public void testGeometryAndMapStatementRules()
  {
    for (String prompt : new String[] { spnChatPrompt(), kaleidoscopeChatPrompt() })
    {
      // The chat agent knows geometries exist and how they are stored
      assertTrue(prompt.contains("Geometries"));
      assertTrue(prompt.contains("geo:hasGeometry"));
      assertTrue(prompt.contains("geo:asWKT"));
      assertTrue(prompt.contains("potentially gigantic"));
      assertTrue(prompt.contains("Do NOT select ?wkt"));

      // And that geometries belong in the audit map statement
      assertTrue(prompt.contains("Map Statement (visual audit)"));
      assertTrue(prompt.contains("audit your results visually on a map"));
      assertTrue(prompt.contains("lowest-level objects"));
      assertTrue(prompt.contains("- mapStatement:"));
    }
  }

  @Test
  public void testKaleidoscopeExecutedAggregateExampleHasNoGeometry()
  {
    String section = kaleidoscopeChatPrompt().split("Aggregation functions", 2)[1].split("Usecase 1: 'budget line item'", 2)[0];

    assertTrue(section.contains("?totalPopulation"));
    assertTrue(!section.contains("geo:asWKT"), "The executed-query aggregate example must not fetch geometries");
  }

  @Test
  public void testPromptSizeBudget()
  {
    String spn = spnChatPrompt();
    String kaleidoscope = kaleidoscopeChatPrompt();

    System.out.println("Spn chat prompt: " + spn.length() + " chars");
    System.out.println("Kaleidoscope chat prompt: " + kaleidoscope.length() + " chars");

    assertTrue(spn.length() <= SPN_CHAT_PROMPT_BUDGET, "Spn chat prompt is " + spn.length() + " chars, budget is " + SPN_CHAT_PROMPT_BUDGET);
    assertTrue(kaleidoscope.length() <= KALEIDOSCOPE_CHAT_PROMPT_BUDGET, "Kaleidoscope chat prompt is " + kaleidoscope.length() + " chars, budget is " + KALEIDOSCOPE_CHAT_PROMPT_BUDGET);
  }
}
