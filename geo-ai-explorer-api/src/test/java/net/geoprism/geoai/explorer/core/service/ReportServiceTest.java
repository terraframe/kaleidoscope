package net.geoprism.geoai.explorer.core.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;
import net.geoprism.geoai.explorer.core.model.GenericRestException;
import net.geoprism.geoai.explorer.core.model.ReportSelection;
import net.geoprism.geoai.explorer.core.model.ReportTemplate;
import net.geoprism.geoai.explorer.core.model.SectionData;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class ReportServiceTest extends MockDataTest
{
  private static final String   NED = "National Economic Development (NED) Analysis";

  private static final String   RED = "Regional Economic Development (RED) Analysis";

  private static final byte[]   PDF = new byte[] { 1, 2, 3 };

  @Autowired
  private ReportService         service;

  @MockitoBean
  private BedrockService        bedrock;

  @MockitoBean
  private ReportTemplateService templateService;

  @MockitoBean
  private PdfService            pdfService;

  private ReportTemplate        template;

  @BeforeEach
  public void setUp() throws Exception
  {
    template = createReportTemplate();

    when(templateService.get(template.getId())).thenReturn(template);
    when(pdfService.fromMarkdown(anyString(), anyString())).thenReturn(PDF);
    when(bedrock.report(anyString(), any(SectionData.class))).thenAnswer(invocation -> {
      SectionData data = invocation.getArgument(1);
      return "## " + data.getName();
    });
  }

  private ReportSelection selection(Map<String, List<Long>> sectionFacts, Map<String, String> sectionNotes)
  {
    return ReportSelection.builder() //
        .templateId(template.getId()) //
        .projectId(7L) //
        .factIds(List.of(1L, 2L, 3L)) //
        .sectionFacts(new LinkedHashMap<>(sectionFacts)) //
        .sectionNotes(new LinkedHashMap<>(sectionNotes)) //
        .build();
  }

  private List<SectionData> captureSections(int count) throws Exception
  {
    ArgumentCaptor<SectionData> captor = ArgumentCaptor.forClass(SectionData.class);
    verify(bedrock, times(count)).report(anyString(), captor.capture());

    // Sections are generated in parallel, so sort the captured values by name
    return captor.getAllValues().stream().sorted(Comparator.comparing(SectionData::getName)).toList();
  }

  @Test
  public void testGenerate() throws Exception
  {
    ReportSelection selection = selection(Map.of(NED, List.of(1L, 2L), RED, List.of(3L)), Map.of(NED, "Use 2024 price levels"));

    byte[] result = service.generate(selection);

    assertArrayEquals(PDF, result);

    List<SectionData> sections = captureSections(2);

    SectionData ned = sections.get(0);
    assertEquals(NED, ned.getName());
    assertEquals(template.getId(), ned.getTemplateId());
    assertEquals(7L, ned.getProjectId());
    assertEquals(List.of(1L, 2L), ned.getFactIds());
    assertEquals("Use 2024 price levels", ned.getNotes());

    SectionData red = sections.get(1);
    assertEquals(RED, red.getName());
    assertEquals(template.getId(), red.getTemplateId());
    assertEquals(7L, red.getProjectId());
    assertEquals(List.of(3L), red.getFactIds());
    assertNull(red.getNotes());
  }

  @Test
  public void testGenerate_PreservesTemplateOrder() throws Exception
  {
    // Section facts are given in the opposite order from the template
    Map<String, List<Long>> sectionFacts = new LinkedHashMap<>();
    sectionFacts.put(RED, List.of(3L));
    sectionFacts.put(NED, List.of(1L));

    service.generate(selection(sectionFacts, Map.of()));

    verify(pdfService).fromMarkdown(template.getName(), "## " + NED + "\n\n## " + RED);
  }

  @Test
  public void testGenerate_SkipsSectionsWithoutFacts() throws Exception
  {
    service.generate(selection(Map.of(RED, List.of(3L)), Map.of(NED, "Notes without facts")));

    List<SectionData> sections = captureSections(1);
    assertEquals(RED, sections.get(0).getName());

    verify(pdfService).fromMarkdown(template.getName(), "## " + RED);
  }

  @Test
  public void testGenerate_IgnoresSectionsNotInTemplate() throws Exception
  {
    service.generate(selection(Map.of(NED, List.of(1L), "Not A Section", List.of(2L)), Map.of()));

    List<SectionData> sections = captureSections(1);
    assertEquals(NED, sections.get(0).getName());

    verify(pdfService).fromMarkdown(template.getName(), "## " + NED);
  }

  @Test
  public void testGenerate_NoSectionFacts() throws Exception
  {
    byte[] result = service.generate(selection(Map.of(), Map.of()));

    assertArrayEquals(PDF, result);
    verify(bedrock, never()).report(anyString(), any(SectionData.class));
    verify(pdfService).fromMarkdown(template.getName(), "");
  }

  @Test
  public void testGenerate_SharesSessionAcrossSections() throws Exception
  {
    service.generate(selection(Map.of(NED, List.of(1L), RED, List.of(2L)), Map.of()));

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(bedrock, times(2)).report(captor.capture(), any(SectionData.class));

    List<String> sessionIds = captor.getAllValues();
    assertFalse(sessionIds.get(0).isBlank());
    assertEquals(sessionIds.get(0), sessionIds.get(1));
  }

  @Test
  public void testGenerate_NewSessionPerReport() throws Exception
  {
    ReportSelection selection = selection(Map.of(NED, List.of(1L)), Map.of());

    service.generate(selection);
    service.generate(selection);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(bedrock, times(2)).report(captor.capture(), any(SectionData.class));

    List<String> sessionIds = captor.getAllValues();
    assertNotEquals(sessionIds.get(0), sessionIds.get(1));
  }

  @Test
  public void testGenerate_BedrockFailure() throws Exception
  {
    TimeoutException cause = new TimeoutException("Bedrock timed out");

    when(bedrock.report(anyString(), any(SectionData.class))).thenThrow(cause);

    GenericRestException ex = assertThrows(GenericRestException.class, () -> service.generate(selection(Map.of(NED, List.of(1L)), Map.of())));

    // A parallel stream may rethrow a copy of the exception that wraps the
    // original, so look for the service's exception anywhere in the chain
    boolean found = false;
    for (Throwable t = ex; t != null; t = t.getCause())
    {
      if (t instanceof GenericRestException && "Unable to generate a section of the report".equals(t.getMessage()))
      {
        assertTrue(t.getCause() == cause);
        found = true;
      }
    }

    assertTrue(found, "Expected the original failure to be wrapped in a GenericRestException");
    verify(pdfService, never()).fromMarkdown(anyString(), anyString());
  }

  @Test
  public void testGenerate_UsesSelectedTemplate() throws Exception
  {
    service.generate(selection(Map.of(NED, List.of(1L)), Map.of()));

    verify(templateService).get(template.getId());
    verify(pdfService).fromMarkdown(eq(template.getName()), anyString());
  }
}
