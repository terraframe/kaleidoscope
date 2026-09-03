package net.geoprism.geoai.explorer.core.service.prompt;

public class SharedPrompt
{
  public static String aggregationFunctions()
  {
    return """
    =
    Aggregation functions must always be wrapped in parenthesis with its variable name
    =
    // Incorrect
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX obj: <%1$s#>
    
    SELECT SUM(?population) as ?totalPopulation
    FROM <%2$s>
    WHERE {
     ?censusTract obj:GeoObject-code "CEMVK_RR_03_ONE_27" .
     ?censusTract obj:CensusTract-population ?population . 
    }
    
    // Correct
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX obj: <%1$s#>
    
    SELECT (SUM(?population) as ?totalPopulation)
    FROM <%2$s>
    WHERE {
     ?censusTract obj:GeoObject-code "CEMVK_RR_03_ONE_27" .
     ?censusTract obj:CensusTract-population ?population . 
    }
    
    =
    Aggregation functions must always restrict the values to their distinct subject
    =
    // Incorrect
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> 
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX obj: <%1$s#>
    
    SELECT (SUM(?pop2pmu65) as ?totalPopulation) 
    FROM <%2$s>
    WHERE {
      ?structure obj:GeoObject-code "1443" .
      ?structure obj:Structure-pop2pmu65 ?pop2pmu65 . 
    } 
    
    // Correct
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> 
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX obj: <%1$s#>
    
    SELECT (SUM(?pop2pmu65) as ?totalPopulation) 
    FROM <%2$s>
    WHERE {
      SELECT DISTINCT ?structure ?pop2pmu65
      WHERE {      
        ?structure obj:GeoObject-code "1443" .
        ?structure obj:Structure-pop2pmu65 ?pop2pmu65 . 
      }
    }
        """;
  }
}
