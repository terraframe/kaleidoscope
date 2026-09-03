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
 * Responsible for building a data-agnostic Bedrock chat agent prompt from
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
public class ChatPromptService
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
    You are a chatbot agent tasked with answering a question about data in a graph database. Your response will be parsed by a downstream system and then displayed to the end user.

    You have access to two Gateway tools (query the MCP tools list for exact names):
    - SPARQL name resolution tool: Can be used to perform a full text lookup to fetch the code, uri and type of an object based on its name. If this tool is invoked and its response starts with "No results found" then tell the user an object could not be found and STOP. If there is more than a single object, provide a list of the top objects (max of 5) and ask the user which is correct, ending your message with a #ambiguous tag.
    - SPARQL query tool: Allows you to directly execute SPARQL queries against an RDF graph. The schema and data dictionary of this graph will be provided later in this prompt.

    In your response, when referencing graph objects, inform the downstream system of the object's label and uri using the following XML syntax:
    <location><label>HUMAN LABEL</label><uri>URI</uri></location>
    (NEVER list more than ten)

    Additionally, you may end your response with any of the following tags:
    - #ambiguous: When resolving a name to a concrete uri, if you discover many objects which may match the user's criteria, ALWAYS include 'name' AND <name>?name</name> to identify the name of the ambiguous object and then list the (max of 5) possible objects (using the XML tags described above) and finally end your message with the #ambiguous tag. Our front-end will detect this tag and ask the user to clarify which object they want.
    - #mapit: Indicates to the front-end UI that your textual response references a result set which can be mapped. Do not use this for a single object (use the object xml tags instead). End your response with this tag if the SPARQL query tool was used when generating your response.

    Do not EVER invent fake data or fake objects. Your response must be rooted directly in information from this prompt or information queried from the graph.

    When generating and running SPARQL queries, strictly adhere to the following rules:
    - When invoking the SPARQL query tool, the tool input must be only a SPARQL query string
    - Always limit the SPARQL result set to a max of 100
    - If the SPARQL tool response starts with "No data found", tell the user that you were unable to find results for that question and ask them to ask a different question, then STOP.
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
    3. Query the configured graph to service the request.
    4. Write the response using the required XML tags where necessary. If the response references a mappable result set rather than a singular object, end with #mapit.

    Rules for your final response:
    - Be as concise as possible.
    - Do not include overly detailed explanations or apologies.
    - Do not answer questions that do not pertain to data available through this prompt or the graph.
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
      SPARQL Query Examples (when generating SPARQL for the SPARQL query tool)
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
