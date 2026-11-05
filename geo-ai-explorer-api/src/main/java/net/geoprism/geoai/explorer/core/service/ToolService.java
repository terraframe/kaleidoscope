package net.geoprism.geoai.explorer.core.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.entity.Fact;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.model.NameResolutionResult;
import net.geoprism.geoai.explorer.core.model.ReportTemplate;
import net.geoprism.geoai.explorer.core.model.ToolResponse;
import net.geoprism.geoai.explorer.core.service.search.BasicSearchService;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessTool;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ToolService
{
  private static final Logger   log                 = LoggerFactory.getLogger(ToolService.class);

  public static final String    QUERY_TOOL          = "query";

  public static final String    RESOLUTION_TOOL     = "name_resolution";

  public static final String    LIST_PROJECT_TOOL   = "list_projects";

  public static final String    LIST_TEMPLATES_TOOL = "list_templates";

  public static final String    GET_TEMPLATE_TOOL   = "get_template";

  public static final String    LIST_FACTS_TOOL     = "list_facts";

  public static final String    GET_FACTS_TOOL      = "get_fact";

  public static final String    SPARQL_PARAM        = "sparql";

  public static final String    NAME_PARAM          = "name";

  public static final String    PROJECT_PARAM       = "project";

  public static final String    FACT_PARAM          = "fact";

  public static final String    TEMPLATE_PARAM      = "template";

  @Autowired
  private GraphQueryService     queryService;

  @Autowired
  private BasicSearchService    search;

  @Autowired
  private ProjectService        projectService;

  @Autowired
  private FactService           factService;

  @Autowired
  private ReportTemplateService templateService;

  public HarnessTool getNameResolutionToolDefinition()
  {
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

    HarnessTool tool = HarnessTool.builder() //
        .type(HarnessToolType.INLINE_FUNCTION) //
        .name(RESOLUTION_TOOL) //
        .config(config -> config.inlineFunction(function -> function //
            .description("Look up a location by name and return its code and geographic object type.") //
            .inputSchema(nameResolutionSchema))) //
        .build();
    return tool;
  }

  public HarnessTool getQueryToolDefinition()
  {
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

    HarnessTool tool = HarnessTool.builder() //
        .type(HarnessToolType.INLINE_FUNCTION) //
        .name(QUERY_TOOL) //
        .config(config -> config.inlineFunction(function -> function //
            .description("Execute a SPARQL query against the configured RDF graph and return the query results.") //
            .inputSchema(sparqlSchema))) //
        .build();
    return tool;
  }

  public HarnessTool getListProjectsToolDefinition()
  {
    Document sparqlSchema = Document.mapBuilder() //
        .putString("type", "object") //
        .putDocument("properties", Document.mapBuilder() //
            .build()) //
        .putList("required", List.of()) //
        .build();

    HarnessTool tool = HarnessTool.builder() //
        .type(HarnessToolType.INLINE_FUNCTION) //
        .name(LIST_PROJECT_TOOL) //
        .config(config -> config.inlineFunction(function -> function //
            .description("Returns a list of project name and id pairings in the system") //
            .inputSchema(sparqlSchema))) //
        .build();
    return tool;
  }

  public HarnessTool getListFactsToolDefinition()
  {
    Document sparqlSchema = Document.mapBuilder() //
        .putString("type", "object") //
        .putDocument("properties", Document.mapBuilder() //
            .putDocument(PROJECT_PARAM, Document.mapBuilder() //
                .putString("type", "integer") //
                .putString("description", "ID of the project") //
                .build()) //
            .build()) //
        .putList("required", List.of(Document.fromString(PROJECT_PARAM))) //
        .build();

    HarnessTool tool = HarnessTool.builder() //
        .type(HarnessToolType.INLINE_FUNCTION) //
        .name(LIST_FACTS_TOOL) //
        .config(config -> config.inlineFunction(function -> function //
            .description("Returns a list of approved report facts for the a project") //
            .inputSchema(sparqlSchema))) //
        .build();
    return tool;
  }

  public HarnessTool getListTemplateToolDefinition()
  {
    Document sparqlSchema = Document.mapBuilder() //
        .putString("type", "object") //
        .putDocument("properties", Document.mapBuilder() //
            .build()) //
        .putList("required", List.of()) //
        .build();

    HarnessTool tool = HarnessTool.builder() //
        .type(HarnessToolType.INLINE_FUNCTION) //
        .name(LIST_TEMPLATES_TOOL) //
        .config(config -> config.inlineFunction(function -> function //
            .description("Returns a list of template names and ids") //
            .inputSchema(sparqlSchema))) //
        .build();
    return tool;
  }

  public HarnessTool getGetDiscoveryFactDefinition()
  {
    Document sparqlSchema = Document.mapBuilder() //
        .putString("type", "object") //
        .putDocument("properties", Document.mapBuilder() //
            .putDocument(TEMPLATE_PARAM, Document.mapBuilder() //
                .putString("type", "integer") //
                .putString("description", "ID of the discovery") //
                .build()) //
            .build()) //
        .putList("required", List.of(Document.fromString(FACT_PARAM))) //
        .build();

    HarnessTool tool = HarnessTool.builder() //
        .type(HarnessToolType.INLINE_FUNCTION) //
        .name(GET_FACTS_TOOL) //
        .config(config -> config.inlineFunction(function -> function //
            .description("Returns a JSON serialized fact for the given id") //
            .inputSchema(sparqlSchema))) //
        .build();
    return tool;
  }

  public HarnessTool getGetTemplateToolDefinition()
  {
    Document sparqlSchema = Document.mapBuilder() //
        .putString("type", "object") //
        .putDocument("properties", Document.mapBuilder() //
            .putDocument(TEMPLATE_PARAM, Document.mapBuilder() //
                .putString("type", "integer") //
                .putString("description", "ID of the report template") //
                .build()) //
            .build()) //
        .putList("required", List.of(Document.fromString(TEMPLATE_PARAM))) //
        .build();

    HarnessTool tool = HarnessTool.builder() //
        .type(HarnessToolType.INLINE_FUNCTION) //
        .name(GET_TEMPLATE_TOOL) //
        .config(config -> config.inlineFunction(function -> function //
            .description("Return a JSON report template by id") //
            .inputSchema(sparqlSchema))) //
        .build();
    return tool;
  }

  public List<HarnessTool> getPromptTools()
  {
    List<HarnessTool> tools = new LinkedList<>();

    tools.add(getQueryToolDefinition());
    tools.add(getNameResolutionToolDefinition());

    return tools;
  }

  public List<HarnessTool> getReportTools()
  {
    List<HarnessTool> tools = new LinkedList<>();

    tools.add(getListProjectsToolDefinition());
    tools.add(getListTemplateToolDefinition());
    tools.add(getGetTemplateToolDefinition());
    tools.add(getListFactsToolDefinition());
    tools.add(getGetDiscoveryFactDefinition());

    return tools;
  }

  public ToolResponse execute(String toolName, JsonNode argsNode)
  {
    log.info("Executing tool - " + toolName);

    if (QUERY_TOOL.equals(toolName))
    {
      String statement = argsNode.get(SPARQL_PARAM).asString("");

      // TODO: Handle size limit
      List<Map<String, Object>> results = this.queryService.select(statement);

      return ToolResponse.builder() //
          .name(toolName) //
          .statement(statement) //
          .text(JsonMapper.shared().writeValueAsString(results)) //
          .build();
    }
    else if (RESOLUTION_TOOL.equals(toolName))
    {
      String name = argsNode.get(NAME_PARAM).asString("");

      List<NameResolutionResult> results = this.search.nameResolution(name);

      return ToolResponse.builder() //
          .name(toolName) //
          .text(JsonMapper.shared().writeValueAsString(results)) //
          .build();
    }
    else if (LIST_PROJECT_TOOL.equals(toolName))
    {
      List<Project> projects = this.projectService.fetchList();

      return ToolResponse.builder() //
          .name(toolName) //
          .text(JsonMapper.shared().writeValueAsString(projects)) //
          .build();
    }
    else if (LIST_FACTS_TOOL.equals(toolName))
    {
      long projectId = argsNode.get(PROJECT_PARAM).asLong(-1);

      List<Map<String, Object>> discoveries = this.factService.fetchListByProject(projectId).stream().map(s -> {
        Map<String, Object> dto = Map.of( //
            "id", s.getId(), //
            "name", s.getName(), //
            "description", s.getDescription() //
        );

        return dto;
      }).toList();

      return ToolResponse.builder() //
          .name(toolName) //
          .text(JsonMapper.shared().writeValueAsString(discoveries)) //
          .build();
    }
    else if (GET_FACTS_TOOL.equals(toolName))
    {
      long id = argsNode.get(FACT_PARAM).asLong(-1);

      Fact discovery = this.factService.findById(id).orElseThrow();

      return ToolResponse.builder() //
          .name(toolName) //
          .text(JsonMapper.shared().writeValueAsString(discovery)) //
          .build();
    }
    else if (LIST_TEMPLATES_TOOL.equals(toolName))
    {
      List<Map<String, Object>> templates = this.templateService.getAll().stream().map(s -> {
        Map<String, Object> dto = Map.of( //
            "id", s.getId(), //
            "name", s.getName() //
        );

        return dto;
      }).toList();

      return ToolResponse.builder() //
          .name(toolName) //
          .text(JsonMapper.shared().writeValueAsString(templates)) //
          .build();
    }
    else if (GET_TEMPLATE_TOOL.equals(toolName))
    {
      String templateId = argsNode.get(TEMPLATE_PARAM).asString("");

      if (StringUtils.isNotBlank(templateId))
      {
        ReportTemplate template = this.templateService.get(templateId);

        return ToolResponse.builder() //
            .name(toolName) //
            .text(JsonMapper.shared().writeValueAsString(template)) //
            .build();
      }
    }

    throw new UnsupportedOperationException("Unknown tool request [" + toolName + "]");
  }

}
