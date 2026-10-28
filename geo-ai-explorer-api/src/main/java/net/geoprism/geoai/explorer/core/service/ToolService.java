package net.geoprism.geoai.explorer.core.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.service.search.BasicSearchService;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessTool;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ToolService
{
  public static final String SPARQL_TOOL     = "sparql";

  public static final String SPARQL_PARAM    = "sparql";

  public static final String RESOLUTION_TOOL = "sparqlnameresolution";

  public static final String NAME_PARAM      = "name";

  @Autowired
  private GraphQueryService  queryService;

  @Autowired
  private BasicSearchService search;

  public List<HarnessTool> getDefinitions()
  {
    List<HarnessTool> tools = new LinkedList<>();

    Document sparqlSchema = Document.mapBuilder() //
        .putString("type", "object") //
        .putDocument("properties", Document.mapBuilder() //
            .putDocument(SPARQL_PARAM, Document.mapBuilder() //
                .putString("type", "string") //
                .putString("description", "The complete SPARQL query to execute.") //
                .build()) //
            .build()) //
        .putList("required", List.of(Document.fromString(SPARQL_PARAM))) //
        .build();

    tools.add(HarnessTool.builder() //
        .type(HarnessToolType.INLINE_FUNCTION) //
        .name(SPARQL_TOOL) //
        .config(config -> config.inlineFunction(function -> function //
            .description("Execute a SPARQL query against the configured RDF graph and return the query results.") //
            .inputSchema(sparqlSchema))) //
        .build());

    Document nameResolutionSchema = Document.mapBuilder() //
        .putString("type", "object") //
        .putDocument("properties", Document.mapBuilder() //
            .putDocument(NAME_PARAM, Document.mapBuilder() //
                .putString("type", "string") //
                .putString("description", "The location name to look up.") //
                .build()) //
            .build()) //
        .putList("required", List.of(Document.fromString(NAME_PARAM))) //
        .build();

    tools.add(HarnessTool.builder() //
        .type(HarnessToolType.INLINE_FUNCTION) //
        .name("sparqlnameresolution") //
        .config(config -> config.inlineFunction(function -> function //
            .description("Look up a location by name and return its code and geographic object type.") //
            .inputSchema(nameResolutionSchema))) //
        .build());

    return tools;
  }

  public String execute(String toolName, JsonNode argsNode)
  {
    if (SPARQL_TOOL.equals(toolName))
    {
      String statement = argsNode.get(SPARQL_PARAM).asString("");

      List<Map<String, Object>> results = this.queryService.select(statement);

      // TODO: Handle size limit

      return JsonMapper.shared().writeValueAsString(results);
    }
    else if (RESOLUTION_TOOL.equals(toolName))
    {
      String name = argsNode.get(NAME_PARAM).asString("");

      List<Map<String, Object>> results = this.search.nameResolution(name);

      return JsonMapper.shared().writeValueAsString(results);
    }

    return "";
  }

}
