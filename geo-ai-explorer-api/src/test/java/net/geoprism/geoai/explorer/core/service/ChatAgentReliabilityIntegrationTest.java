package net.geoprism.geoai.explorer.core.service;

import static net.geoprism.geoai.explorer.core.service.ChatAgentAssertions.checkMapStatementMatchesMapIt;
import static net.geoprism.geoai.explorer.core.service.ChatAgentAssertions.checkNoGeometryInExecutedQueries;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.model.Message;
import net.geoprism.geoai.explorer.core.model.NameResolutionResult;
import net.geoprism.geoai.explorer.core.service.search.BasicSearchService;

/**
 * Repeatedly runs a fixed set of questions through the live chat agent and
 * measures how reliably it answers them, and how long it takes. This exists to
 * catch regressions in the chat agent when its prompt changes (for example,
 * adding the map statement and geometry instructions).
 *
 * <p>
 * The LLM is non-deterministic, so a single run proves little. Tune with:
 * </p>
 * <ul>
 * <li>{@code -Dchat.reliability.runs=N} runs per question (default 3)</li>
 * <li>{@code -Dchat.reliability.minPassRate=0.9} minimum pass rate (default
 * 0.9)</li>
 * <li>{@code -Dchat.reliability.maxAverageLatencySeconds=90} maximum average
 * response time (default 90)</li>
 * </ul>
 *
 * <p>
 * To compare against a baseline, run this on main and on your branch and
 * compare the printed summaries. On a branch without map statements, pass
 * {@code -Dchat.reliability.checkMapStatement=false}.
 * </p>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class ChatAgentReliabilityIntegrationTest
{
  private record Case(String name, String question, Map<String, Object> queryResult, NameResolutionResult resolution, List<String> expectedAnswer, boolean expectQuery, Boolean expectMapIt, String[] mapStatementFragments)
  {
  }

  private static final NameResolutionResult STRUCTURE = NameResolutionResult.builder() //
      .uri("https://localhost/graph#Structure-2012X") //
      .type("Structure") //
      .label("849W84F9+M6C-4-5-3-4") //
      .code("2012X") //
      .build();

  private static final NameResolutionResult SCENARIO  = NameResolutionResult.builder() //
      .uri("https://localhost/graph#FloodScenario-1") //
      .type("FloodScenario") //
      .label("No Mitigation") //
      .code("1") //
      .build();

  private static final List<Case>           CASES     = List.of( //
      new Case("structure value", "Give me the value of a structure with the name 849W84F9+M6C-4-5-3-4", Map.of("value", 431826), STRUCTURE, List.of("431,826", "431826"), true, false, new String[] { "2012X" }), //
      new Case("parcels at risk", "How many land parcels are at risk with the no mitigation flood scenario?", Map.of("total", 200), SCENARIO, List.of("200"), true, true, new String[] { "LandParcel" }), //
      new Case("project area population", "How many people are in the project area with a code of '1'?", Map.of("totalPopulation", 5432), SCENARIO, List.of("5,432", "5432"), true, true, new String[] { "Structure" }), //
      new Case("structure value at risk", "What is the total value of structures at risk under the combo plan scenario?", Map.of("totalValue", 987654), SCENARIO, List.of("987,654", "987654"), true, true, new String[] { "Structure" }), //
      new Case("greeting", "Hello! What kinds of questions can you help me with?", Map.of("value", 1), STRUCTURE, List.of(), false, false, new String[] {}) //
  );

  @Autowired
  private BedrockService                    service;

  @MockitoBean
  private BasicSearchService                searchService;

  @MockitoBean
  private GraphQueryService                 queryService;

  @Test
  public void testChatAgentReliability() throws Exception
  {
    int runs = Integer.getInteger("chat.reliability.runs", 3);
    double minPassRate = Double.parseDouble(System.getProperty("chat.reliability.minPassRate", "0.9"));
    double maxAverageLatency = Double.parseDouble(System.getProperty("chat.reliability.maxAverageLatencySeconds", "90"));
    boolean checkMapStatement = Boolean.parseBoolean(System.getProperty("chat.reliability.checkMapStatement", "true"));

    int attempts = 0;
    int passes = 0;
    long totalMillis = 0;
    List<String> failures = new ArrayList<>();
    StringBuilder summary = new StringBuilder();

    for (Case c : CASES)
    {
      int casePasses = 0;
      long caseMillis = 0;

      for (int run = 0; run < runs; run++)
      {
        when(searchService.nameResolution(anyString())).thenReturn(List.of(c.resolution()));
        when(queryService.select(anyString())).thenReturn(List.of(c.queryResult()));

        attempts++;
        long start = System.currentTimeMillis();
        List<String> problems = new ArrayList<>();

        try
        {
          Message message = service.prompt(UUID.randomUUID().toString(), c.question());

          problems.addAll(check(c, message, checkMapStatement));
        }
        catch (Exception e)
        {
          problems.add("Exception: " + e);
        }

        long elapsed = System.currentTimeMillis() - start;
        caseMillis += elapsed;
        totalMillis += elapsed;

        if (problems.isEmpty())
        {
          passes++;
          casePasses++;
        }
        else
        {
          failures.add("[" + c.name() + " run " + ( run + 1 ) + "]\n" + String.join("\n", problems));
        }
      }

      summary.append(String.format("  %-26s %d/%d passed, avg %.1fs%n", c.name(), casePasses, runs, caseMillis / 1000.0 / runs));
    }

    double passRate = (double) passes / attempts;
    double averageLatency = totalMillis / 1000.0 / attempts;

    System.out.println("Chat agent reliability summary:");
    System.out.print(summary);
    System.out.println(String.format("  TOTAL %d/%d passed (%.0f%%), avg latency %.1fs", passes, attempts, passRate * 100, averageLatency));
    failures.forEach(f -> System.out.println("\n" + f));

    Assertions.assertTrue(passRate >= minPassRate, String.format("Chat agent pass rate %.2f is below %.2f. Failures:%n%s", passRate, minPassRate, String.join("\n\n", failures)));
    Assertions.assertTrue(averageLatency <= maxAverageLatency, String.format("Chat agent average latency %.1fs exceeds %.1fs", averageLatency, maxAverageLatency));
  }

  private List<String> check(Case c, Message message, boolean checkMapStatement)
  {
    List<String> problems = new ArrayList<>();
    String content = message.getContent() == null ? "" : message.getContent();
    List<String> statements = message.getStatements() == null ? List.of() : message.getStatements();

    if (content.isBlank())
    {
      problems.add("Empty answer");
    }

    if (!c.expectedAnswer().isEmpty() && c.expectedAnswer().stream().noneMatch(content::contains))
    {
      problems.add("Answer does not contain any of " + c.expectedAnswer() + ": " + content);
    }

    if (Boolean.TRUE.equals(message.getAmbiguous()))
    {
      problems.add("Answer was unexpectedly flagged ambiguous: " + content);
    }

    if (c.expectQuery() && statements.isEmpty())
    {
      problems.add("Expected the query tool to be used, but no queries were executed: " + content);
    }

    if (!c.expectQuery() && !statements.isEmpty())
    {
      problems.add("Expected no queries, but executed: " + statements);
    }

    problems.addAll(checkNoGeometryInExecutedQueries(statements));

    if (c.expectMapIt() != null && !c.expectMapIt().equals(message.getMappable()))
    {
      problems.add("Expected mapIt " + c.expectMapIt() + " but was " + message.getMappable() + ": " + content);
    }

    if (checkMapStatement)
    {
      // Required when, and only when, mapIt is true
      problems.addAll(checkMapStatementMatchesMapIt(message, c.mapStatementFragments()));
    }

    return problems;
  }
}
