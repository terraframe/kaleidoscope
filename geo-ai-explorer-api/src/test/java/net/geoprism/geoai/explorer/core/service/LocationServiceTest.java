package net.geoprism.geoai.explorer.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;
import net.geoprism.geoai.explorer.core.model.GenericRestException;
import net.geoprism.geoai.explorer.core.model.Location;
import net.geoprism.geoai.explorer.core.model.LocationPage;
import net.geoprism.geoai.explorer.core.model.TypeSummary;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class LocationServiceTest extends MockDataTest
{
  private static final String STATEMENT          = "SELECT ?uri WHERE { ?uri a ?type }";

  private static final String TYPE_STATEMENT     = "TYPE-FILTERED";

  private static final String EXCLUDED_STATEMENT = "EXCLUDED-FILTERED";

  private static final String TYPE               = "Structure";

  @Autowired
  private LocationService     service;

  @MockitoBean
  private GraphQueryService   graph;

  private static Location location(String code, String label)
  {
    return new Location("https://localhost/graph#Structure-" + code, TYPE, code, label, null);
  }

  // ---------------------------------------------------------------------------
  // getPage
  // ---------------------------------------------------------------------------

  @Test
  public void testGetPage_WithType_FiltersByTypeAndInjectsAttributes()
  {
    List<Location> locations = List.of(location("1", "One"), location("2", "Two"));

    when(graph.buildTypeFilterQuery(STATEMENT, TYPE)).thenReturn(TYPE_STATEMENT);
    when(graph.query(TYPE_STATEMENT, 10, 20, "label", "ASC")).thenReturn(locations);
    when(graph.getCount(TYPE_STATEMENT)).thenReturn(42L);

    LocationPage page = service.getPage(STATEMENT, TYPE, 10, 20, List.of("Ignored"), "label", "ASC");

    assertEquals(TYPE, page.getType());
    assertSame(locations, page.getLocations());
    assertEquals(42L, page.getCount());
    assertEquals(10L, page.getOffset());
    assertEquals(20, page.getLimit());
    assertEquals(STATEMENT, page.getStatement());
    assertEquals("label", page.getSortField());
    assertEquals("ASC", page.getSortDirection());
    assertNull(page.getAvailableTypes());

    verify(graph).injectAttributes(page);
    verify(graph, never()).buildExcludedTypesQuery(anyString(), any());
    verify(graph, never()).getTypeCounts(anyString());
  }

  @Test
  public void testGetPage_WithoutType_ExcludesTypesAndReturnsTypeCounts()
  {
    List<String> excluded = List.of("FloodScenario");
    List<Location> locations = List.of(location("1", "One"));
    List<TypeSummary> types = List.of(new TypeSummary());

    when(graph.buildExcludedTypesQuery(STATEMENT, excluded)).thenReturn(EXCLUDED_STATEMENT);
    when(graph.query(EXCLUDED_STATEMENT, 0, 100, null, null)).thenReturn(locations);
    when(graph.getCount(EXCLUDED_STATEMENT)).thenReturn(1L);
    when(graph.getTypeCounts(STATEMENT)).thenReturn(types);

    LocationPage page = service.getPage(STATEMENT, null, 0, 100, excluded);

    assertNull(page.getType());
    assertSame(locations, page.getLocations());
    assertEquals(1L, page.getCount());
    assertSame(types, page.getAvailableTypes());
    assertNull(page.getSortField());
    assertNull(page.getSortDirection());

    verify(graph, never()).buildTypeFilterQuery(anyString(), anyString());
    verify(graph, never()).injectAttributes(any());
    verify(graph, never()).injectAttributes(any(), anyBoolean());
  }

  @Test
  public void testGetPage_BlankTypeIsTreatedAsCombinedPage()
  {
    when(graph.buildExcludedTypesQuery(STATEMENT, List.of())).thenReturn(EXCLUDED_STATEMENT);
    when(graph.query(EXCLUDED_STATEMENT, 0, 50, null, null)).thenReturn(List.of());
    when(graph.getCount(EXCLUDED_STATEMENT)).thenReturn(0L);

    LocationPage page = service.getPage(STATEMENT, "   ", 0, 50);

    assertNull(page.getType());
    verify(graph).buildExcludedTypesQuery(STATEMENT, List.of());
    verify(graph).getTypeCounts(STATEMENT);
    verify(graph, never()).buildTypeFilterQuery(anyString(), anyString());
  }

  @Test
  public void testGetPage_GraphFailureIsWrappedInGenericRestException()
  {
    RuntimeException cause = new RuntimeException("SPARQL endpoint unavailable");

    when(graph.buildTypeFilterQuery(STATEMENT, TYPE)).thenReturn(TYPE_STATEMENT);
    when(graph.query(eq(TYPE_STATEMENT), anyInt(), anyInt(), any(), any())).thenThrow(cause);

    GenericRestException exception = assertThrows(GenericRestException.class, () -> service.getPage(STATEMENT, TYPE, 0, 10));

    assertSame(cause, exception.getCause());
  }

  // ---------------------------------------------------------------------------
  // exportPageCsv
  // ---------------------------------------------------------------------------

  @Test
  public void testExportPageCsv_WithType_UsesCountAsLimitAndInjectsAttributes()
  {
    Location location = location("2012X", "Building");
    location.addProperty("value", 431826);

    when(graph.buildTypeFilterQuery(STATEMENT, TYPE)).thenReturn(TYPE_STATEMENT);
    when(graph.getCount(TYPE_STATEMENT)).thenReturn(1L);
    when(graph.query(TYPE_STATEMENT, 0, 1, "label", "DESC")).thenReturn(List.of(location));

    String csv = service.exportPageCsv(STATEMENT, TYPE, List.of(), "label", "DESC");

    String[] lines = csv.split("\n");
    assertEquals(2, lines.length);

    List<String> header = List.of(lines[0].split(","));
    List<String> row = List.of(lines[1].split(","));

    assertEquals(header.size(), row.size());
    assertEquals("2012X", row.get(header.indexOf("code")));
    assertEquals("Building", row.get(header.indexOf("label")));
    assertEquals("431826", row.get(header.indexOf("value")));

    verify(graph).injectAttributes(any(LocationPage.class));
  }

  @Test
  public void testExportPageCsv_WithoutType_DoesNotInjectAttributes()
  {
    when(graph.buildExcludedTypesQuery(STATEMENT, List.of("Excluded"))).thenReturn(EXCLUDED_STATEMENT);
    when(graph.getCount(EXCLUDED_STATEMENT)).thenReturn(1L);
    when(graph.query(EXCLUDED_STATEMENT, 0, 1, null, null)).thenReturn(List.of(location("1", "One")));

    service.exportPageCsv(STATEMENT, null, List.of("Excluded"), null, null);

    verify(graph, never()).injectAttributes(any());
    verify(graph, never()).buildTypeFilterQuery(anyString(), anyString());
  }

  @Test
  public void testExportPageCsv_CapsLimitAt100000()
  {
    when(graph.buildTypeFilterQuery(STATEMENT, TYPE)).thenReturn(TYPE_STATEMENT);
    when(graph.getCount(TYPE_STATEMENT)).thenReturn(5_000_000L);
    when(graph.query(TYPE_STATEMENT, 0, 100000, null, null)).thenReturn(List.of());

    service.exportPageCsv(STATEMENT, TYPE, List.of(), null, null);

    verify(graph).query(TYPE_STATEMENT, 0, 100000, null, null);
  }

  @Test
  public void testExportPageCsv_ExcludesGeometryColumns()
  {
    Location location = location("1", "One");
    location.addProperty("wkt", "POINT (1 2)");
    location.addProperty("geometry", "geo");
    location.addProperty("geom", "geo");
    location.addProperty("the_geom", "geo");
    location.addProperty("bbox", "[0,0,1,1]");
    location.addProperty("edges", "[]");

    when(graph.buildTypeFilterQuery(STATEMENT, TYPE)).thenReturn(TYPE_STATEMENT);
    when(graph.getCount(TYPE_STATEMENT)).thenReturn(1L);
    when(graph.query(TYPE_STATEMENT, 0, 1, null, null)).thenReturn(List.of(location));

    String csv = service.exportPageCsv(STATEMENT, TYPE, List.of(), null, null);

    List<String> header = List.of(csv.split("\n")[0].split(","));

    assertEquals(4, header.size());
    assertTrue(header.containsAll(List.of("uri", "type", "code", "label")));
  }

  @Test
  public void testExportPageCsv_EscapesSpecialCharactersAndNulls()
  {
    Location location = new Location();
    location.addProperty("comma", "a,b");
    location.addProperty("quote", "say \"hi\"");
    location.addProperty("newline", "line1\nline2");
    location.addProperty("plain", "text");
    location.addProperty("empty", null);

    when(graph.buildTypeFilterQuery(STATEMENT, TYPE)).thenReturn(TYPE_STATEMENT);
    when(graph.getCount(TYPE_STATEMENT)).thenReturn(1L);
    when(graph.query(TYPE_STATEMENT, 0, 1, null, null)).thenReturn(List.of(location));

    String csv = service.exportPageCsv(STATEMENT, TYPE, List.of(), null, null);

    assertTrue(csv.contains("\"a,b\""), csv);
    assertTrue(csv.contains("\"say \"\"hi\"\"\""), csv);
    assertTrue(csv.contains("\"line1\nline2\""), csv);
    assertTrue(csv.contains("text"), csv);
    assertTrue(!csv.contains("null"), "Null value should render as an empty cell:\n" + csv);
  }

  @Test
  public void testExportPageCsv_UnionOfColumnsAcrossRows()
  {
    Location first = new Location();
    first.addProperty("a", "1");

    Location second = new Location();
    second.addProperty("b", "2");

    when(graph.buildTypeFilterQuery(STATEMENT, TYPE)).thenReturn(TYPE_STATEMENT);
    when(graph.getCount(TYPE_STATEMENT)).thenReturn(2L);
    when(graph.query(TYPE_STATEMENT, 0, 2, null, null)).thenReturn(List.of(first, second));

    String csv = service.exportPageCsv(STATEMENT, TYPE, List.of(), null, null);

    assertEquals("a,b\n1,\n,2\n", csv);
  }

  @Test
  public void testExportPageCsv_NullLocationsProducesEmptyCsv()
  {
    when(graph.buildTypeFilterQuery(STATEMENT, TYPE)).thenReturn(TYPE_STATEMENT);
    when(graph.getCount(TYPE_STATEMENT)).thenReturn(0L);
    when(graph.query(TYPE_STATEMENT, 0, 0, null, null)).thenReturn(null);

    String csv = service.exportPageCsv(STATEMENT, TYPE, List.of(), null, null);

    assertEquals("\n", csv);
  }
}
