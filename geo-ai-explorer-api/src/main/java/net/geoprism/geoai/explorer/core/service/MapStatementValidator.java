package net.geoprism.geoai.explorer.core.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the 'map statement' the chat agent returns alongside its answer.
 * The agent never executes this statement itself, so nothing else catches a
 * malformed one before the user clicks "View on Map". Validation is parse-only
 * (no database round trip) so that it adds no latency to chat responses.
 */
public final class MapStatementValidator
{
  private static final Logger      log               = LoggerFactory.getLogger(MapStatementValidator.class);

  /** Variables the map pipeline cannot work without. */
  public static final List<String> REQUIRED_VARIABLES = List.of("uri", "wkt");

  /** Variables the prompt asks for. Missing ones are tolerated but logged. */
  public static final List<String> EXPECTED_VARIABLES = List.of("uri", "type", "code", "label", "wkt");

  private MapStatementValidator()
  {
  }

  /**
   * @return The cleaned statement if it is a SELECT query projecting the
   *         required variables, otherwise null.
   */
  public static String validate(String statement)
  {
    if (StringUtils.isBlank(statement))
    {
      return null;
    }

    String cleaned = stripCodeFence(statement);

    try
    {
      Query query = QueryFactory.create(cleaned);

      if (!query.isSelectType())
      {
        log.warn("Discarding map statement which is not a SELECT query: {}", cleaned);
        return null;
      }

      Set<String> variables = query.getResultVars().stream().collect(Collectors.toSet());

      List<String> missingRequired = REQUIRED_VARIABLES.stream().filter(v -> !variables.contains(v)).toList();

      if (!missingRequired.isEmpty())
      {
        log.warn("Discarding map statement missing required variables {}: {}", missingRequired, cleaned);
        return null;
      }

      List<String> missingExpected = EXPECTED_VARIABLES.stream().filter(v -> !variables.contains(v)).toList();

      if (!missingExpected.isEmpty())
      {
        log.warn("Map statement is missing expected variables {}: {}", missingExpected, cleaned);
      }

      return cleaned;
    }
    catch (Exception e)
    {
      log.warn("Discarding map statement which failed to parse: {}", cleaned, e);
      return null;
    }
  }

  static String stripCodeFence(String statement)
  {
    String value = statement.trim();

    if (value.startsWith("```"))
    {
      value = value.replaceFirst("^```[a-zA-Z]*\\s*", "");
      value = value.replaceFirst("\\s*```$", "");
    }

    return value.trim();
  }
}
