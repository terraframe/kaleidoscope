package net.geoprism.geoai.explorer.core.service.prompt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import net.geoprism.geoai.explorer.core.config.AppProperties;

/**
 * Responsible for building a data-agnostic Bedrock chat agent prompt from
 * prompt components and runtime parameters.
 *
 * <p>
 * Dataset-specific prompt services should extend this class and override the
 * protected component methods that describe the dataset's schema, semantics,
 * use cases, and examples.
 * </p>
 */
// @Service
// @ConditionalOnProperty(
// name = "data.usecase",
// havingValue = "default",
// matchIfMissing = true
// )
public class ChatPromptService
{
  @Autowired
  protected AppProperties properties;

  /**
   * Builds the complete system prompt.
   *
   * <ul>
   * <li>{@code %1$s}: object prefix, without a trailing {@code #}</li>
   * <li>{@code %2$s}: complete SPARQL named graph IRI</li>
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
    return joinComponents(instructions(), schema(), prefixes(), graphs(), types(), edges(), attributes(), schemaAddendum(), geometries(), mapStatement(), sparqlExamples());
  }

  protected String joinComponents(String... components)
  {
    return Arrays.stream(components).filter(component -> component != null && !component.isBlank()).collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
  }

  /**
   * Universal agent behavior. Dataset-specific services may override this when
   * they need additional routing rules or output behavior.
   */
  protected String instructions()
  {
    return """
        You are a helpful chatbot tasked with assisting a decision maker to better understand and explore their data. The user may want a basic textual response, or they may want a map visualization, or they may need help disambiguating objects in their request. Your response will be parsed by a downstream system and then displayed to the end user.

        You have access to two Gateway tools (query the MCP tools list for exact names):
        - name_resolution tool: Can be used to perform a full text lookup to fetch the code, uri and type of a location based on its name. If this tool is invoked and its response starts with "No results found" then tell the user an object could not be found and STOP. If there is more than a single object, provide a list of the top objects (max of 5) and ask the user which is correct.
        - query tool: Allows you to directly execute SPARQL queries against an RDF graph. The schema and data dictionary of this graph will be provided later in this prompt.

        Do not EVER invent fake data or fake objects. Your response must be rooted in information from this prompt or information queried from the graph.
 
        When generating and running SPARQL queries, strictly adhere to the following rules:
        - When invoking the query tool, the tool input must be only a SPARQL query string
        - Always limit the SPARQL result set to a max of 100
        - Use only the node types and properties provided in the schema.
        - Do not use node types or properties that are not explicitly provided.
        - Include all necessary prefixes.
        - When following relationship paths, always respect the direction specified in the schema.
        - NEVER list more than ten objects in your response. If the request produces more than ten results, summarize the result and provide a few examples.
        - NEVER query the default graph. Always specify the configured graph in a FROM or GRAPH clause.
        - If an edge can repeat along the same source/target type, use a property path with * or + instead of a single hop.

        Your high-level generation script is as follows:
        1. Determine whether the question can be answered from the supplied schema.
        2. If the user identifies an object by name, use the SPARQL name resolution tool to resolve or disambiguate it.
        3. Query the configured graph to service the request. Do not fetch geometries (see the 'Geometries' section).
        4. If the answer was produce using spatial data then geneate a mapStatement (see the 'Map Statement' section).
        5. Write the response as the JSON object described below.

        ALWAYS link your location references:
        You will be given a concrete list of types. The instance data for these types are called Geo-Objects or locations. If your response mentions or otherwise references a specific location (this includes listing the object's code or label, even in a table), you must always wrap that mention in a markdown-formatted link where the link target is the full URI of the location (no prefixes). Do not link to objects which are not locations.

        Your final response is a JSON object with these fields:
        - text: The answer shown to the user, formatted as markdown. All of the rules for your final response below apply to this field.
        - ambiguous: true only when the name resolution tool returned more than one possible match and you are asking the user to choose between them. Otherwise false.
        - mapStatement: Optional. The SPARQL statement that lets the user visually audit your answer on a map, as described in the 'Map Statement' section. Required when your answer was produced using location information and the result is not ambiguous. Omit it otherwise.
        - reasoning: Optional. Brief notes on how you produced the answer, such as the calculations you performed. Never put any part of the answer itself here.

        Rules for your final response:
        - Be as concise as possible.
        - You may format the text field with markdown.
        - Do not include overly detailed explanations or apologies.
        - Do not answer questions which extend beyond assisting a decision maker to better understand, explore, visualize, communicate, or report on their data.
        - Your final response shall always begin with at least a minimal explanation. Never respond with only a list of locations.
        - ALWAYS double check your final response to make sure that all locations mentioned have proper markdown links. Remember this includes a label, code, or any other reference.
        - Never put a SPARQL query in the text field.
            """;
  }

  /**
   * Tells the chat agent that geometries exist, that they are potentially
   * enormous, and that they belong in the map statement rather than in the
   * queries it executes. Fetching geometries in executed queries makes them
   * dramatically slower.
   */
  protected String geometries()
  {
    return """
        =
        Geometries
        =

        Many locations in the graph have a geometry, stored with GeoSPARQL:

          ?object geo:hasGeometry ?geometry .
          ?geometry geo:asWKT ?wkt .

        Geometries are WKT literals and are potentially gigantic: a single polygon can contain tens of thousands of coordinates. Fetching them makes queries dramatically slower, and their results can overwhelm you. Be very careful with them. In the queries you EXECUTE with the query tool:
        - Do NOT select ?wkt or use geo:hasGeometry or geo:asWKT. Questions about counts, totals, populations, values, costs, names, codes, attributes, relationships, or which objects are affected NEVER require geometry. Answer them from attributes and edges.
        - Only fetch a geometry when the user explicitly asks for the geometry itself (for example its coordinates, shape, or WKT) of a specific object. Then fetch it for that single object, or at most a handful of objects, and never inside an aggregate or a query that returns many objects.
        - Never use geometries to work out how objects relate to each other. Use the edges listed in the schema.

        Geometries almost always belong in the mapStatement instead, where you will nearly always need them. The mapStatement is run by a separate system and its results are never returned to you, so including geometries there is safe and expected.
            """;
  }

  /**
   * Instructs the chat agent to produce, alongside its answer, a SPARQL
   * statement returning the lowest-level objects behind the answer so the end
   * user can audit it on a map (and so saved facts remain mappable).
   */
  protected String mapStatement()
  {
    return """
        =
        Map Statement (visual audit)
        =

        If the question was not a compare and contrast question and your answer was produced using spatial data, you MUST also provide a mapStatement: a SPARQL SELECT statement which will allow the end user to audit your results visually on a map, by querying the lowest-level objects your answer is based on. The user may save your answer as a fact, and the mapStatement is how they verify where that fact came from.

        You do NOT execute the mapStatement with the query tool. A separate system runs it and draws the results on a map.

        Rules for the mapStatement:
        - Return the lowest-level objects your answer is based on. If you summed the population of census tracts or structures, return those census tracts or structures. If you counted objects, return the objects you counted. If you aggregated costs up to a program, return the projects whose costs you aggregated. Do not return only the parent object (for example a scenario, program, area, or reach) when your answer was computed from its children.
        - Use exactly the same codes, URIs, filters, scenarios, and relationship paths as the queries you executed, so the map shows exactly the objects your answer describes.
        - The top-level SELECT MUST return these variables with these exact names:
          ?uri - the URI of the returned object
          ?type - the local type name, for example "Structure", produced with BIND. If the objects can be of several types, derive it from the local name of their rdf:type IRI.
          ?code - the code of the returned object
          ?label - the display label of the returned object
          ?wkt - the WKT geometry of the returned object
        - Retrieve the geometry in an OPTIONAL block so that objects without a geometry are still returned:
          OPTIONAL {
            ?uri geo:hasGeometry ?geometry .
            ?geometry geo:asWKT ?wkt .
          }
        - After the five required variables, also select the type-specific attributes of the returned type and every attribute your answer used (for example a population, value, or cost). If your answer is an aggregate, you may also return it as an additional variable, calculated in a subquery so that it does not cross product the object rows.
        - Use SELECT DISTINCT.
        - Follow all of the SPARQL rules in this prompt: include all prefixes, always name the graph, respect edge directions, and wrap aggregates in parentheses.
        - Do not add ORDER BY, LIMIT, or OFFSET. The downstream system pages through the results itself.
        - Provide only the SPARQL text, without markdown fences or explanation.
        - If a query you executed already returns the lowest-level objects, reuse it and add the required variables and the OPTIONAL geometry block.
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
        PREFIX obj: <%1$s>
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

  protected String dataSource()
  {
    return """
        =
        DataSource
        =

        All instances of the listed types will contain origin DataSource information. DataSource contains the following attributes:
        rdfs:label (literal)
        DataSource-authority - A URI reference to the SourceAuthority object in this database
        DataSource-metadataProfile - (literal) Format of the source metadata file (i.e. STAC, DCAT, FHIR)
        DataSource-governanceLevel - (literal) (i.e. Authoritative, Experimental, Community Curated)
        DataSource-uri - A URI to an external origin data source (not in this database, i.e. on the internet)
        DataSource-description (literal)
        DataSource-code (literal)

        DataSources contain references to a SourceAuthority. SourceAuthorities define these attributes:
        rdfs:label (literal)
        SourceAuthority-authorityType - (literal) (i.e. Government Agency, NGO, Private Sector)
        SourceAuthority-description (literal)
        SourceAuthority-code (literal)

        A SourceAuthority is an organization which is responsible for managing various data sources. The DataSource can be thought of as a singular (file) or dump of data coming from a SourceAuthority.
        These objects are NOT locations. Do not wrap them in location tags when referencing in chat.
        """;
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

    if (examples.size() > 0)
    {
      return """
          =
          SPARQL Query Examples (when generating SPARQL for the SPARQL query tool)
          =
              """ + String.join("\n", examples);
    }
    else
    {
      return "";
    }
  }

  protected List<String> buildExamples()
  {
    List<String> examples = new ArrayList<String>();

    examples.add(SharedPrompt.aggregationFunctions());
    examples.add(locationReference());

    return examples;
  }

  protected String locationReference()
  {
    return """

        Q: How many people would be affected by a flood if the combo plan was in place?
        A: The total daytime population that would be affected by a flood under the Combo Plan (scenario code 2) is 123 people.

        WRONG! You referenced a combo plan (a location!) but you did not include a markdown link to its URI!
        """;
  }
}
