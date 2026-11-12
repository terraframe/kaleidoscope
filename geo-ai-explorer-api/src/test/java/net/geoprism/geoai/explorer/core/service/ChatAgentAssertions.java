package net.geoprism.geoai.explorer.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.geoprism.geoai.explorer.core.model.Message;

/**
 * Shared checks on chat agent output, used by the live chat agent integration
 * tests. Each check returns a list of problems (empty when it passes) so that
 * the reliability test can count failures instead of stopping at the first.
 */
public final class ChatAgentAssertions
{
  private static final Pattern GEOMETRY = Pattern.compile("geo:asWKT|geo:hasGeometry|asWKT|hasGeometry|\\?wkt\\b", Pattern.CASE_INSENSITIVE);

  private ChatAgentAssertions()
  {
  }

  /**
   * Executed (chat) queries must not fetch geometries: doing so makes them
   * dramatically slower. Geometry belongs in the map statement only.
   */
  public static List<String> checkNoGeometryInExecutedQueries(List<String> statements)
  {
    List<String> problems = new ArrayList<>();

    for (String statement : statements == null ? List.<String> of() : statements)
    {
      if (GEOMETRY.matcher(statement).find())
      {
        problems.add("Executed query fetched geometry:\n" + statement);
      }
    }

    return problems;
  }

  /**
   * The map statement must be present, satisfy the map pipeline's contract, and
   * reference the given fragments (codes, types, edges) from the question.
   */
  public static List<String> checkMapStatement(String mapStatement, String... mustContain)
  {
    List<String> problems = new ArrayList<>();

    if (mapStatement == null)
    {
      problems.add("Expected a valid mapStatement but there was none (missing, or discarded by MapStatementValidator)");
      return problems;
    }

    if (MapStatementValidator.validate(mapStatement) == null)
    {
      problems.add("mapStatement failed validation:\n" + mapStatement);
    }

    for (String variable : MapStatementValidator.EXPECTED_VARIABLES)
    {
      if (!mapStatement.contains("?" + variable))
      {
        problems.add("mapStatement does not select ?" + variable + ":\n" + mapStatement);
      }
    }

    if (!mapStatement.toUpperCase(Locale.ROOT).contains("OPTIONAL"))
    {
      problems.add("mapStatement does not retrieve geometry in an OPTIONAL block:\n" + mapStatement);
    }

    for (String fragment : mustContain)
    {
      if (!mapStatement.contains(fragment))
      {
        problems.add("mapStatement does not contain '" + fragment + "':\n" + mapStatement);
      }
    }

    return problems;
  }

  /**
   * The map statement is required when, and only when, the agent sets mapIt.
   * When mapIt is true it must also satisfy {@link #checkMapStatement}.
   */
  public static List<String> checkMapStatementMatchesMapIt(Message message, String... mustContain)
  {
    if (Boolean.TRUE.equals(message.getMappable()))
    {
      return checkMapStatement(message.getMapStatement(), mustContain);
    }

    List<String> problems = new ArrayList<>();

    if (message.getMapStatement() != null)
    {
      problems.add("mapStatement present although mapIt is false:\n" + message.getMapStatement());
    }

    return problems;
  }

  /**
   * Whenever the agent flags the response as ambiguous it must also identify
   * the location name that was ambiguous.
   */
  public static List<String> checkLocationSetWhenAmbiguous(Message message)
  {
    List<String> problems = new ArrayList<>();

    if (Boolean.TRUE.equals(message.getAmbiguous()) && ( message.getLocation() == null || message.getLocation().isBlank() ))
    {
      problems.add("location is not set although ambiguous is true. Content:\n" + message.getContent());
    }

    return problems;
  }

  public static void assertNoProblems(List<String> problems)
  {
    if (!problems.isEmpty())
    {
      throw new AssertionError(String.join("\n\n", problems));
    }
  }
}
