package net.geoprism.geoai.explorer.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.entity.Discovery;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;
import net.geoprism.geoai.explorer.core.model.NameResolutionResult;
import net.geoprism.geoai.explorer.core.model.ToolResponse;
import net.geoprism.geoai.explorer.core.service.search.BasicSearchService;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessInlineFunctionConfig;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessTool;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class ToolServiceTest extends MockDataTest
{
  @Autowired
  private ToolService        service;

  @MockitoBean
  private GraphQueryService  queryService;

  @MockitoBean
  private BasicSearchService search;

  @MockitoBean
  private ProjectService     projectService;

  @MockitoBean
  private DiscoveryService   snippetService;

  private HarnessInlineFunctionConfig getInlineFunction(HarnessTool tool)
  {
    assertNotNull(tool.config());
    assertNotNull(tool.config().inlineFunction());

    return tool.config().inlineFunction();
  }

  @Test
  public void testGetNameResolutionToolDefinition_NameAndType()
  {
    HarnessTool tool = service.getNameResolutionToolDefinition();

    assertEquals(ToolService.RESOLUTION_TOOL, tool.name());
    assertEquals(HarnessToolType.INLINE_FUNCTION, tool.type());
  }

  @Test
  public void testGetNameResolutionToolDefinition_Description()
  {
    HarnessInlineFunctionConfig function = getInlineFunction(service.getNameResolutionToolDefinition());

    assertEquals("Look up a location by name and return its code and geographic object type.", function.description());
  }

  @Test
  public void testGetNameResolutionToolDefinition_InputSchema()
  {
    Document schema = getInlineFunction(service.getNameResolutionToolDefinition()).inputSchema();

    assertNotNull(schema);
    assertTrue(schema.isMap());

    Map<String, Document> root = schema.asMap();

    assertEquals("object", root.get("type").asString());

    // A single "name" string property
    Map<String, Document> properties = root.get("properties").asMap();

    assertEquals(1, properties.size());
    assertTrue(properties.containsKey(ToolService.NAME_PARAM));

    Map<String, Document> name = properties.get(ToolService.NAME_PARAM).asMap();

    assertEquals("string", name.get("type").asString());
    assertEquals("The location name to look up.", name.get("description").asString());

    // "name" is the only required property
    List<Document> required = root.get("required").asList();

    assertEquals(1, required.size());
    assertEquals(ToolService.NAME_PARAM, required.get(0).asString());
  }

  @Test
  public void testGetNameResolutionToolDefinition_IncludedInPromptDefinitions()
  {
    List<HarnessTool> tools = service.getPromptTools();

    assertTrue(tools.contains(service.getNameResolutionToolDefinition()));
  }

  /*
   * getQueryToolDefinition
   */

  @Test
  public void testGetQueryToolDefinition_NameAndType()
  {
    HarnessTool tool = service.getQueryToolDefinition();

    assertEquals(ToolService.QUERY_TOOL, tool.name());
    assertEquals(HarnessToolType.INLINE_FUNCTION, tool.type());
  }

  @Test
  public void testGetQueryToolDefinition_Description()
  {
    HarnessInlineFunctionConfig function = getInlineFunction(service.getQueryToolDefinition());

    assertEquals("Execute a SPARQL query against the configured RDF graph and return the query results.", function.description());
  }

  @Test
  public void testGetQueryToolDefinition_InputSchema()
  {
    Document schema = getInlineFunction(service.getQueryToolDefinition()).inputSchema();

    assertNotNull(schema);
    assertTrue(schema.isMap());

    Map<String, Document> root = schema.asMap();

    assertEquals("object", root.get("type").asString());

    // A single "sparql" string property
    Map<String, Document> properties = root.get("properties").asMap();

    assertEquals(1, properties.size());
    assertTrue(properties.containsKey(ToolService.SPARQL_PARAM));

    Map<String, Document> sparql = properties.get(ToolService.SPARQL_PARAM).asMap();

    assertEquals("string", sparql.get("type").asString());
    assertEquals("The complete SPARQL query to execute.", sparql.get("description").asString());

    // "sparql" is the only required property
    List<Document> required = root.get("required").asList();

    assertEquals(1, required.size());
    assertEquals(ToolService.SPARQL_PARAM, required.get(0).asString());
  }

  @Test
  public void testGetQueryToolDefinition_IncludedInPromptDefinitions()
  {
    List<HarnessTool> tools = service.getPromptTools();

    assertTrue(tools.contains(service.getQueryToolDefinition()));
  }

  /*
   * getListProjectsToolDefinition
   */

  @Test
  public void testGetListProjectsToolDefinition_NameAndType()
  {
    HarnessTool tool = service.getListProjectsToolDefinition();

    assertEquals(ToolService.LIST_PROJECT_TOOL, tool.name());
    assertEquals(HarnessToolType.INLINE_FUNCTION, tool.type());
  }

  @Test
  public void testGetListProjectsToolDefinition_Description()
  {
    HarnessInlineFunctionConfig function = getInlineFunction(service.getListProjectsToolDefinition());

    assertEquals("Returns a list of project name and id pairings in the system", function.description());
  }

  @Test
  public void testGetListProjectsToolDefinition_InputSchema()
  {
    Document schema = getInlineFunction(service.getListProjectsToolDefinition()).inputSchema();

    assertNotNull(schema);
    assertTrue(schema.isMap());

    Map<String, Document> root = schema.asMap();

    assertEquals("object", root.get("type").asString());

    // Takes no arguments
    assertTrue(root.get("properties").asMap().isEmpty());
    assertTrue(root.get("required").asList().isEmpty());
  }

  /*
   * getListSnippetsToolDefinition
   */

  @Test
  public void testGetListSnippetsToolDefinition_NameAndType()
  {
    HarnessTool tool = service.getListDiscoveryToolDefinition();

    assertEquals(ToolService.LIST_DISCOVERIES_TOOL, tool.name());
    assertEquals(HarnessToolType.INLINE_FUNCTION, tool.type());
  }

  @Test
  public void testGetListSnippetsToolDefinition_Description()
  {
    HarnessInlineFunctionConfig function = getInlineFunction(service.getListDiscoveryToolDefinition());

    assertEquals("Returns a list of approved report snippets for the a project", function.description());
  }

  @Test
  public void testGetListSnippetsToolDefinition_InputSchema()
  {
    Document schema = getInlineFunction(service.getListDiscoveryToolDefinition()).inputSchema();

    assertNotNull(schema);
    assertTrue(schema.isMap());

    Map<String, Document> root = schema.asMap();

    assertEquals("object", root.get("type").asString());

    // A single "project" integer property
    Map<String, Document> properties = root.get("properties").asMap();

    assertEquals(1, properties.size());
    assertTrue(properties.containsKey(ToolService.PROJECT_PARAM));

    Map<String, Document> project = properties.get(ToolService.PROJECT_PARAM).asMap();

    assertEquals("integer", project.get("type").asString());
    assertEquals("ID of the project", project.get("description").asString());

    // "project" is the only required property
    List<Document> required = root.get("required").asList();

    assertEquals(1, required.size());
    assertEquals(ToolService.PROJECT_PARAM, required.get(0).asString());
  }

  @Test
  public void testToolDefinitions_HaveUniqueNames()
  {
    List<String> names = List.of( //
        service.getQueryToolDefinition().name(), //
        service.getNameResolutionToolDefinition().name(), //
        service.getListProjectsToolDefinition().name(), //
        service.getListDiscoveryToolDefinition().name() //
    );

    assertEquals(names.size(), names.stream().distinct().count(), "Tool names must be unique: " + names);
  }

  /*
   * execute
   */

  @Test
  public void testExecute_QueryTool()
  {
    String statement = "SELECT ?s ?label WHERE { ?s <http://www.w3.org/2000/01/rdf-schema#label> ?label } LIMIT 2";

    List<Map<String, Object>> results = List.of( //
        Map.of("s", "https://localhost/graph#Structure-1", "label", "Structure 1"), //
        Map.of("s", "https://localhost/graph#Structure-2", "label", "Structure 2") //
    );

    when(queryService.select(statement)).thenReturn(results);

    ObjectNode args = JsonMapper.shared().createObjectNode();
    args.put(ToolService.SPARQL_PARAM, statement);

    ToolResponse response = service.execute(ToolService.QUERY_TOOL, args);

    assertEquals(ToolService.QUERY_TOOL, response.getName());
    assertEquals(statement, response.getStatement());

    // The text is the query results serialized as JSON
    JsonNode text = JsonMapper.shared().readTree(response.getText());

    assertTrue(text.isArray());
    assertEquals(2, text.size());
    assertEquals("https://localhost/graph#Structure-1", text.get(0).get("s").asString());
    assertEquals("Structure 1", text.get(0).get("label").asString());
    assertEquals("https://localhost/graph#Structure-2", text.get(1).get("s").asString());
    assertEquals("Structure 2", text.get(1).get("label").asString());

    verify(queryService).select(statement);
    verifyNoInteractions(search, projectService, snippetService);
  }

  @Test
  public void testExecute_ResolutionTool()
  {
    String name = "849W84F9+M6C-4-5-3-4";

    when(search.nameResolution(anyString())).thenReturn(List.of( //
        NameResolutionResult.builder() //
            .uri("https://localhost/graph#Structure-2012X") //
            .type("Structure") //
            .code("2012X") //
            .label(name) //
            .build() //
    ));

    ObjectNode args = JsonMapper.shared().createObjectNode();
    args.put(ToolService.NAME_PARAM, name);

    ToolResponse response = service.execute(ToolService.RESOLUTION_TOOL, args);

    assertEquals(ToolService.RESOLUTION_TOOL, response.getName());
    assertNull(response.getStatement());

    // The text is the resolution results serialized as JSON
    JsonNode text = JsonMapper.shared().readTree(response.getText());

    assertTrue(text.isArray());
    assertEquals(1, text.size());

    JsonNode result = text.get(0);

    assertEquals("https://localhost/graph#Structure-2012X", result.get("uri").asString());
    assertEquals("Structure", result.get("type").asString());
    assertEquals("2012X", result.get("code").asString());
    assertEquals(name, result.get("label").asString());

    verify(search).nameResolution(name);
    verifyNoInteractions(queryService, projectService, snippetService);
  }

  @Test
  public void testExecute_ListProjectsTool()
  {
    when(projectService.fetchList()).thenReturn(List.of( //
        createProject(1L, "Project A"), //
        createProject(2L, "Project B") //
    ));

    // The tool takes no arguments
    ObjectNode args = JsonMapper.shared().createObjectNode();

    ToolResponse response = service.execute(ToolService.LIST_PROJECT_TOOL, args);

    assertEquals(ToolService.LIST_PROJECT_TOOL, response.getName());
    assertNull(response.getStatement());

    // The text is the projects serialized as JSON
    JsonNode text = JsonMapper.shared().readTree(response.getText());

    assertTrue(text.isArray());
    assertEquals(2, text.size());
    assertEquals(1L, text.get(0).get("id").asLong());
    assertEquals("Project A", text.get(0).get("name").asString());
    assertEquals(2L, text.get(1).get("id").asLong());
    assertEquals("Project B", text.get(1).get("name").asString());

    verify(projectService).fetchList();
    verifyNoInteractions(queryService, search, snippetService);
  }

  @Test
  public void testExecute_ListSnippetsTool()
  {
    Project project = createProject(5L, "Project A");

    Discovery first = createDiscovery(10L, project);
    first.setName("Snippet 1");
    first.setDescription("First snippet");

    Discovery second = createDiscovery(11L, project);
    second.setName("Snippet 2");
    second.setDescription("Second snippet");

    when(snippetService.fetchListByProject(5L)).thenReturn(List.of(first, second));

    ObjectNode args = JsonMapper.shared().createObjectNode();
    args.put(ToolService.PROJECT_PARAM, 5L);

    ToolResponse response = service.execute(ToolService.LIST_DISCOVERIES_TOOL, args);

    assertEquals(ToolService.LIST_DISCOVERIES_TOOL, response.getName());
    assertNull(response.getStatement());

    // The text is a JSON array of id/name/description summaries
    JsonNode text = JsonMapper.shared().readTree(response.getText());

    assertTrue(text.isArray());
    assertEquals(2, text.size());

    assertEquals(10L, text.get(0).get("id").asLong());
    assertEquals("Snippet 1", text.get(0).get("name").asString());
    assertEquals("First snippet", text.get(0).get("description").asString());

    assertEquals(11L, text.get(1).get("id").asLong());
    assertEquals("Snippet 2", text.get(1).get("name").asString());
    assertEquals("Second snippet", text.get(1).get("description").asString());

    // Only the summary fields are returned, not the snippet body or its project
    for (JsonNode node : text)
    {
      assertEquals(3, node.size());
      assertFalse(node.has("text"));
      assertFalse(node.has("statements"));
      assertFalse(node.has("project"));
    }

    verify(snippetService).fetchListByProject(5L);
    verifyNoInteractions(queryService, search, projectService);
  }
}
