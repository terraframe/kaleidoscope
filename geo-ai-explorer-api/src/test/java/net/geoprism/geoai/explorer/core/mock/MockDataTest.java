package net.geoprism.geoai.explorer.core.mock;

import java.util.List;

import net.geoprism.geoai.explorer.core.entity.Fact;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.model.Location;
import net.geoprism.geoai.explorer.core.model.ReportTemplate;
import net.geoprism.geoai.explorer.core.model.Section;

public abstract class MockDataTest
{
  protected ReportTemplate createReportTemplate()
  {
    ReportTemplate plan = ReportTemplate.builder() //
        .id("PLAN_EVAL") //
        .name("Plan Evaluation") //
        .sections(List.of(Section.builder() //
            .name("National Economic Development (NED) Analysis") //
            .summary("""
                  The purpose of this section is to compare and contrast different alternative plans or flood scenarios for
                   their cost effectiveness at a national level, measured in terms of net benefits.
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
            .build(),
            Section.builder() //
                .name("Regional Economic Development (RED) Analysis") //
                .summary("""
                      The purpose of this section is to compare and contrast different alternative plans or flood scenarios number of jobs
                      for number of jobs and  other economic measures such as labor income, value added, and sales that are supported by future
                      implementation of alternative plans
                    """) //
                .example("""
                    The RED analysis estimates number of jobs and other economic measures such as labor income, value
                    added, and sales that are supported by future implementation of alternative plans. The team completed
                    modeling to evaluate the regional economic impact and project expenditures, activities, and infrastructure.
                    The results of this analysis are presented in Table 23Table and Table 24, and Appendix B provides
                    additional details.

                    Construction costs were used to estimate the regional economic impacts of all alternatives in Santa Clara
                    County. Generally, the higher the cost of construction, the higher the RED benefits for that alternative.
                    Table 23 shows the construction costs for all plans. The Bypass Plan (Alt 3b) produces the highest
                    amount of output ($373.1 M), followed by the Nonstructural Plan (alt 4) ($304.6 M), the Valley View
                    Plan (Alt 2b) ($233.7 M), the Combination Plan (Alt 8b) ($83.9 M), and the Lower Scope Plan (Alt 7)
                    (59.4 M) (Table 24). Table 24 shows a comparison of RED results for all plans.                    """)
                .build()))
        .build();
    return plan;
  }

  protected Project createProject(Long id)
  {
    return createProject(id, "Test Project");
  }

  protected Project createProject(Long id, String name)
  {
    return Project.builder() //
        .id(id) //
        .name(name) //
        .build();
  }

  protected Fact createFact(Long id)
  {
    return createFact(id, createProject(1L));
  }

  protected Fact createFact(Long id, Project project)
  {
    return Fact.builder() //
        .id(id) //
        .name("Original Name") //
        .description("Original Description") //
        .text("Some text") //
        .statements("SELECT * WHERE { ?s ?p ?o }") //
        .project(project) //
        .build();
  }
  
  protected Location location(String code, String label, String type)
  {
    return new Location("https://localhost/graph#Structure-" + code, type, code, label, null);
  }



}
