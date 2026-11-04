package net.geoprism.geoai.explorer.core.service;

import java.util.List;

import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.model.ReportTemplate;
import net.geoprism.geoai.explorer.core.model.Section;

@Service
public class ReportTemplateService
{
  private ReportTemplate mock()
  {
    ReportTemplate plan = ReportTemplate.builder() //
        .id("PLAN_EVAL") //
        .name("Plan Evaluation") //
        .sections(List.of(Section.builder() //
            .name("National Economic Development (NED) Analysis") //
            .summary("""
                  The purpose of this section is to compare and contrast different alternative plans or flood scenarios for
                   their cost effectiveness, measured in terms of net benefits
                """) //
            .example("""
                   The final array alternatives were evaluated for their cost effectiveness, measured in terms of net benefits,
                   calculated by subtracting the annual costs from the expected annual economic benefits (Table 21). The
                   Combination Plan had the highest net annual benefits of the alternatives considered in the final array.
                   NED analysis assesses how the alternatives in the final array may reasonably maximize expected net
                   benefits. The net benefits are computed as the annualized flood damage reduction benefits gained minus
                   the annualized cost of construction and Operations, Maintenance, Repair, Rehabilitation and Replacement
                   (OMRR&R). Expected annual damages were estimated using the HEC-FDA computer program. Net
                   benefit computations for the focused array were evaluated based on October 2023 price levels.
                """)
            .build()))
        .build();
    return plan;
  }

  public List<ReportTemplate> getAll()
  {
    return List.of(mock());
  }

  public ReportTemplate get(String id)
  {
    return mock();
  }

}
