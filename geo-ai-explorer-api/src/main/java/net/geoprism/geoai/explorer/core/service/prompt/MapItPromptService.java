package net.geoprism.geoai.explorer.core.service.prompt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.config.AppProperties;

/**
 * Responsible for building a data-agnostic Bedrock 'map it' agent prompt from
 * prompt components and runtime parameters.
 *
 * <p>Dataset-specific prompt services should extend this class and override the
 * protected component methods that describe the dataset's schema, semantics,
 * use cases, and examples.</p>
 */
//@Service
//@ConditionalOnProperty(
//    name = "data.usecase",
//    havingValue = "default",
//    matchIfMissing = true
//)
public class MapItPromptService
{
  @Autowired
  protected AppProperties properties;
  
  /**
   * Builds the complete system prompt.
   *
   * <ul>
   *   <li>{@code %1$s}: object prefix, without a trailing {@code #}</li>
   *   <li>{@code %2$s}: complete SPARQL named graph IRI</li>
   * </ul>
   */
  public String getPrompt()
  {
    return build().formatted(properties.getLpgPrefix(), properties.getSparqlGraph());
  }

  /**
   * Defines the ordering of all prompt components. Empty dataset-specific
   * components are omitted from the resulting prompt.
   */
  protected String build()
  {
    return joinComponents(
        instructions(),
        schema(),
        prefixes(),
        graphs(),
        types(),
        edges(),
        attributes(),
        schemaAddendum(),
        sparqlExamples());
  }

  protected String joinComponents(String... components)
  {
    return Arrays.stream(components)
        .filter(component -> component != null && !component.isBlank())
        .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
  }

  /**
   * Universal agent behavior. Dataset-specific services may override this when
   * they need additional routing rules or output behavior.
   */
  protected String instructions()
  {
    return """
      The user will provide a chat history.
      Based on the rules and the chat history, generate the SPARQL query for the most recent unresolved user request. If the latest user message is only a clarification, disambiguation, code, identifier, or selection, combine it with the earlier user request that prompted the clarification.
      ALWAYS return information about the lowest-level node type needed to answer the request.
      
      Every generated top-level SELECT query MUST return, at a minimum, the following variables with these exact names:
      
      ?uri
      ?type
      ?code
      ?label
      ?wkt
      
      The query MUST also return every available type-specific attribute for the selected node type.
      
      The required output variables represent the following:
      
      * ?uri: The URI of the lowest-level returned object.
      * ?type: The local node type name, such as "Project", "School", or "CensusTract".
      * ?code: The code of the returned object.
      * ?label: The display label of the returned object.
      * ?wkt: The GeoSPARQL WKT geometry of the returned object, when available.
      
      Geometry retrieval SHOULD normally be OPTIONAL so that objects without geometry are not removed from the result set:
      
      OPTIONAL {
      ?uri geo:hasGeometry ?geometry .
      ?geometry geo:asWKT ?wkt .
      }
      
      Return only the generated SPARQL query to the user.
      
      It is NOT guaranteed that all chat history will be relevant.
      
      For example, a chat history may:
      
      1. Begin with a question about the population affected by flooding from a channel reach.
      2. Transition to a question about objects reachable from a program.
      3. Transition again to a question about a flood inundation scenario.
      
      For each separate context, determine whether preceding context is relevant.
      
      Instructions:
      
      * Always generate valid SPARQL.
      * Do not generate Markdown fences or explanatory text.
      * Return only the SPARQL query.
      * Do not rely on the default graph.
      * Explicitly identify each queried named graph using GRAPH <iri> blocks.
      * A query may contain multiple GRAPH blocks when multiple datasets are required.
      * Use only node types and properties explicitly provided in the schema.
      * Do not invent node types, properties, or relationship directions.
      * Include all necessary prefixes.
      * To be safe, include all prefixes listed in the Prefixes section.
      * Follow relationship paths exactly as defined in the schema.
      * Respect the direction of every relationship.
      * Preserve all newlines in the response.
      * Every top-level SELECT query MUST select ?uri, ?type, ?code, ?label, and ?wkt.
      * Select all available type-specific attributes for the returned type.
      * Return only the lowest-level node type needed to answer the request.
      * Additional aggregate or contextual variables may be selected after the five required variables.
      * Use DISTINCT when relationship traversal could produce duplicate objects.
      * Prefer OPTIONAL geometry patterns unless the user explicitly requests only objects that have geometry.
      * Use BIND to produce the required ?type variable.
      * For a query that returns multiple possible node types, derive ?type from the RDF type URI.
      * If a self-referencing relationship is traversed transitively, use a SPARQL property path with *.
      * Wrap aggregation expressions in parentheses and assign them to variables.
      * When an aggregate is needed alongside object results, calculate it in a subquery and expose it as an additional top-level variable.
      * Avoid aggregate cross products by keeping aggregate calculations isolated from optional labels and other one-to-many attributes.
      * Use LIMIT 100 unless the user explicitly requests a different result limit.
      * Use OFFSET only when pagination is requested or relevant.
        """;
  }

  protected String schema()
  {
    return """
    =
    Schema
    =

    The full schema of the active database is provided below. This schema will be used to generate SPARQL queries used to serve end-user requests.
        """;
  }

  protected String prefixes()
  {
    return """
    =
    Prefixes
    =
    
    To be safe, always include all of these prefixes in your queries.

    A full list of the prefixes used for the IRIs within this database:
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
    PREFIX dct: <http://purl.org/dc/terms/>
    PREFIX geo: <http://www.opengis.net/ont/geosparql#>
    PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
    PREFIX sf: <http://www.opengis.net/ont/sf#>
    PREFIX obj: <%1$s#>
        """;
  }

  protected String graphs()
  {
    return """
    =
    Graphs
    =

    The database does NOT include data in the default graph.
    Always query the following named graph:
    - <%2$s>
        """;
  }

  /** Dataset-specific type declarations. */
  protected String types()
  {
    return "";
  }

  /** Dataset-specific directed relationship declarations. */
  protected String edges()
  {
    return "";
  }

  /** Dataset-specific attribute declarations and interpretation rules. */
  protected String attributes()
  {
    return "";
  }

  protected String schemaAddendum()
  {
    return "";
  }

  protected String sparqlExamples()
  {
    List<String> examples = buildExamples();
    
    if (examples.size() > 0) {
      return """
      =
      Examples
      =
          """ + String.join("\n", examples);
    } else {
      return "";
    }
  }
  
  protected List<String> buildExamples()
  {
    List<String> examples = new ArrayList<String>();
    
    examples.add(SharedPrompt.aggregationFunctions());
    
    return examples;
  }
}
