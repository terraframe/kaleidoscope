package net.geoprism.geoai.explorer.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.geoprism.geoai.explorer.core.model.Reference;
import net.geoprism.geoai.explorer.core.model.ReportTemplate;
import net.geoprism.geoai.explorer.core.model.Section;
import tools.jackson.databind.json.JsonMapper;

public class ReportTemplateServiceTest
{
  private final ReportTemplateService service = new ReportTemplateService();

  @Test
  public void testGetAll()
  {
    List<ReportTemplate> templates = service.getAll();

    assertFalse(templates.isEmpty());
    templates.forEach(t -> {
      assertNotNull(t.getId());
      assertNotNull(t.getName());
      assertNotNull(t.getDescription(), t.getId());
      assertFalse(t.getReferences().isEmpty(), t.getId());
      t.getReferences().forEach(r -> {
        assertNotNull(r.getCitation(), t.getId());
        assertNotNull(r.getTitle(), r.getCitation());
        assertNotNull(r.getRole(), r.getCitation());
        assertNotNull(r.getSummary(), r.getCitation());
        assertTrue(r.getUrl() == null || r.getUrl().startsWith("https://"), r.getCitation());
      });
      assertFalse(t.getSections().isEmpty(), t.getId());
      t.getSections().forEach(s -> {
        assertNotNull(s.getName(), t.getId());
        assertNotNull(s.getSummary(), s.getName());
        assertNotNull(s.getExample(), s.getName());
      });
    });
  }

  @Test
  public void testPlanEvaluation()
  {
    ReportTemplate template = service.get(ReportTemplateService.PLAN_EVAL);

    assertNotNull(template);
    assertEquals("Plan Evaluation (1983)", template.getName());
    List<String> citations = template.getReferences().stream().map(Reference::getCitation).toList();
    assertEquals(List.of("Principles and Guidelines (1983)", "ER 1105-2-100", "33 USC 2282(a)(2)"), citations);
    assertEquals(12, template.getSections().size());

    List<String> names = template.getSections().stream().map(Section::getName).toList();

    // Referenced by name in ReportGenerationIntegrationTest
    assertTrue(names.contains("National Economic Development (NED) Analysis"));
    assertTrue(names.contains("Regional Economic Development (RED) Analysis"));

    Section last = template.getSections().getLast();
    assertEquals("Other Social Effects (OSE): Critical Infrastructure Resiliency", last.getName());
    assertFalse(last.isRequired());
    assertTrue(template.getSections().getFirst().isRequired());
  }

  @Test
  public void testPlanEvaluation234()
  {
    ReportTemplate template = service.get(ReportTemplateService.PLAN_EVAL_234);

    assertNotNull(template);
    Reference part234 = template.getReferences().stream()
        .filter(r -> r.getCitation().equals("33 CFR Part 234"))
        .findFirst()
        .orElseThrow();
    assertEquals("https://www.ecfr.gov/current/title-33/chapter-II/part-234", part234.getUrl());
    assertEquals("planning-criteria", part234.getRole());
    assertEquals(List.of("234.7", "234.9", "234.10"), part234.getProvisions());

    List<String> roles = template.getReferences().stream().map(Reference::getRole).toList();
    assertTrue(roles.contains("required-contents"));
    assertTrue(roles.contains("uncertainty-reporting"));

    assertEquals(12, template.getSections().size());
    assertEquals(20, template.getNotes().size());
  }

  @Test
  public void testNotesAreNotSerialized()
  {
    ReportTemplate template = service.get(ReportTemplateService.PLAN_EVAL_234);

    assertFalse(template.getNotes().isEmpty());
    assertFalse(template.getReferences().getFirst().getNotes().isEmpty());

    // get_template sends the serialized template to the report agent; template
    // and reference notes are for people only, reference summaries are for the agent
    String json = JsonMapper.shared().writeValueAsString(template);

    assertFalse(json.contains("\"notes\""), json);
    assertTrue(json.contains("\"sections\""));
    assertTrue(json.contains("\"summary\""));
    assertTrue(json.contains("\"role\":\"required-contents\""), json);
  }

  @Test
  public void testTemplateNamesAreDistinct()
  {
    List<String> names = service.getAll().stream().map(ReportTemplate::getName).toList();

    assertEquals(names.size(), names.stream().distinct().count(), names.toString());
  }

  @Test
  public void testUnknownId()
  {
    assertNull(service.get("DOES_NOT_EXIST"));
  }
}
