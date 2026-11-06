package net.geoprism.geoai.explorer.core.model;

import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplate
{
  private String        id;

  private String        name;

  /**
   * One or two sentences on what the template covers, shown to people
   * choosing a template.
   */
  private String        description;

  /**
   * The statutes, regulations and agency guidance that define what this
   * template's sections ask for. Usually several documents, each governing a
   * different aspect of the report (see {@link Reference#getRole()}).
   */
  @Builder.Default
  private List<Reference> references = new LinkedList<>();

  @Builder.Default
  private List<Section> sections = new LinkedList<>();

  /**
   * Review notes for people maintaining the template: how it was built, known
   * gaps, and decisions that need confirming. Read from the template file but
   * never serialized, so they are not sent to the report generation agent.
   */
  @Builder.Default
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private List<String>  notes    = new LinkedList<>();
}
