package net.geoprism.geoai.explorer.core.model;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSelection
{
  @NotBlank(message = "A template is required")
  private String                  templateId;

  @NotNull(message = "A project is required")
  private Long                    projectId;

  /**
   * Every fact in the report, whether or not it is assigned to a section.
   */
  @Builder.Default
  private List<Long>              factIds      = new LinkedList<>();

  /**
   * Section name -> ids of the facts assigned to it. A fact may be in several
   * sections.
   */
  @Builder.Default
  private Map<String, List<Long>> sectionFacts = new LinkedHashMap<>();

  /**
   * Section name -> the user's own text for the agent to use in that section.
   */
  @Builder.Default
  private Map<String, String>     sectionNotes = new LinkedHashMap<>();
}
