package net.geoprism.geoai.explorer.core.model;

import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A legal or regulatory document that governs what a {@link ReportTemplate}
 * asks for, such as a statute, regulation or agency guidance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reference
{
  /**
   * Short citation, e.g. "33 CFR Part 234" or "EP 1105-2-61, §4-10".
   */
  private String        citation;

  /**
   * Full title of the document.
   */
  private String        title;

  /**
   * Link to the authoritative text, or null if none is known.
   */
  private String        url;

  /**
   * What the document governs in the template, as a short kebab-case label,
   * e.g. "required-contents" or "report-format". Free text, so templates for
   * other kinds of reports can use their own labels.
   */
  private String        role;

  /**
   * The parts of the document that apply to the template, e.g. "234.9". Empty
   * when the document applies as a whole.
   */
  @Builder.Default
  private List<String>  provisions = new LinkedList<>();

  /**
   * What the document requires of a report built from the template, written
   * as instructions to the report generation agent.
   */
  private String        summary;

  /**
   * Notes for people maintaining the template: why the reference is attached,
   * where it came from, which parts were reviewed and anything still to
   * confirm. Read from the template file but never serialized, so they are
   * not sent to the report generation agent.
   */
  @Builder.Default
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private List<String>  notes      = new LinkedList<>();
}
