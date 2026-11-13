package net.geoprism.geoai.explorer.core.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.model.GenericRestException;
import net.geoprism.geoai.explorer.core.model.ReportSelection;
import net.geoprism.geoai.explorer.core.model.ReportTemplate;
import net.geoprism.geoai.explorer.core.model.SectionData;

@Service
public class ReportService
{
  @Autowired
  private BedrockService        bedrock;

  @Autowired
  private ReportTemplateService templateService;

  @Autowired
  private PdfService            pdfService;

  /**
   * Generates every section of the report that has facts assigned to it and
   * returns the finished report as PDF bytes.
   */
  public byte[] generate(ReportSelection selection)
  {
    ReportTemplate template = this.templateService.get(selection.getTemplateId());

    String sessionId = UUID.randomUUID().toString();

    List<String> sections = template.getSections()
        .parallelStream()
        // Only generate sections with facts
        .filter(s -> selection.getSectionFacts().containsKey(s.getName()))
        // Create the section data object
        .map(section -> {
          SectionData data = SectionData.builder() //
              .templateId(selection.getTemplateId()) //
              .projectId(selection.getProjectId()) //
              .name(section.getName()) //
              .factIds(selection.getSectionFacts().get(section.getName())) //
              .notes(selection.getSectionNotes().get(section.getName())) //
              .build();

          return data;
        })
        // Generate the section content
        .map(data -> {
          try
          {
            return this.bedrock.report(sessionId, data);
          }
          catch (Exception e)
          {
            throw new GenericRestException("Unable to generate a section of the report", e);
          }
        })
        // Collecting to a list preserves the template's section order
        .toList();

    return this.pdfService.fromMarkdown(template.getName(), String.join("\n\n", sections));
  }
}
