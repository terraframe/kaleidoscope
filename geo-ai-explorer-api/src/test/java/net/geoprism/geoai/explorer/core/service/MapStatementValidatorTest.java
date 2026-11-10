package net.geoprism.geoai.explorer.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class MapStatementValidatorTest
{
  private static final String VALID = """
      PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      PREFIX geo: <http://www.opengis.net/ont/geosparql#>
      PREFIX obj: <https://spn.geoprism.net#>

      SELECT DISTINCT ?uri ?type ?code ?label ?wkt ?landUse
      FROM <https://spn.geoprism.net/spn>
      WHERE {
        ?scenario obj:GeoObject-code "1" .
        ?scenario obj:HasFloodRisk ?uri .
        ?uri obj:GeoObject-code ?code .
        ?uri rdfs:label ?label .
        BIND("LandParcel" AS ?type)
        OPTIONAL { ?uri obj:LandParcel-landUse ?landUse . }
        OPTIONAL {
          ?uri geo:hasGeometry ?geometry .
          ?geometry geo:asWKT ?wkt .
        }
      }""";

  @Test
  public void testValid()
  {
    assertEquals(VALID.trim(), MapStatementValidator.validate(VALID));
  }

  @Test
  public void testStripsCodeFence()
  {
    assertEquals(VALID.trim(), MapStatementValidator.validate("```sparql\n" + VALID + "\n```"));
  }

  @Test
  public void testBlank()
  {
    assertNull(MapStatementValidator.validate(null));
    assertNull(MapStatementValidator.validate("   "));
  }

  @Test
  public void testSyntaxError()
  {
    assertNull(MapStatementValidator.validate("SELECT ?uri ?wkt WHERE { ?uri ?p "));
  }

  @Test
  public void testMissingUri()
  {
    assertNull(MapStatementValidator.validate(VALID.replace("SELECT DISTINCT ?uri ", "SELECT DISTINCT ")));
  }

  @Test
  public void testMissingWkt()
  {
    assertNull(MapStatementValidator.validate(VALID.replace("?label ?wkt ?landUse", "?label ?landUse")));
  }

  @Test
  public void testAggregateOnly()
  {
    assertNull(MapStatementValidator.validate("""
        PREFIX obj: <https://spn.geoprism.net#>
        SELECT (COUNT(DISTINCT ?parcel) AS ?parcelCount)
        FROM <https://spn.geoprism.net/spn>
        WHERE { ?scenario obj:HasFloodRisk ?parcel . }
        """));
  }

  @Test
  public void testNotSelect()
  {
    assertNull(MapStatementValidator.validate("""
        PREFIX obj: <https://spn.geoprism.net#>
        CONSTRUCT { ?uri obj:x ?wkt } WHERE { ?uri obj:y ?wkt . }
        """));
  }
}
