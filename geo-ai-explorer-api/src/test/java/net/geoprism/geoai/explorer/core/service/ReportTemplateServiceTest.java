package net.geoprism.geoai.explorer.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

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
    assertEquals("Plan Evaluation", template.getName());
    assertNotNull(template.getRegulation());
    assertTrue(template.getRegulationUrl().startsWith("https://"));
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
    assertTrue(template.getRegulation().startsWith("33 CFR Part 234"));
    assertEquals("https://www.ecfr.gov/current/title-33/chapter-II/part-234", template.getRegulationUrl());
    assertEquals(12, template.getSections().size());
    assertEquals(21, template.getNotes().size());
  }

  @Test
  public void testNotesAreNotSerialized()
  {
    ReportTemplate template = service.get(ReportTemplateService.PLAN_EVAL_234);

    assertFalse(template.getNotes().isEmpty());

    // get_template sends the serialized template to the report agent; notes are for people only
    String json = JsonMapper.shared().writeValueAsString(template);

    assertFalse(json.contains("\"notes\""), json);
    assertTrue(json.contains("\"sections\""));
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
