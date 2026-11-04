package net.geoprism.geoai.explorer.core.service;

import java.util.List;

import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.model.ReportTemplate;
import net.geoprism.geoai.explorer.core.model.Section;

/**
 * Report templates used by the report generation agent.
 *
 * The Plan Evaluation template is derived from section 3.5 of the Upper
 * Guadalupe River Draft Integrated General Reevaluation Report (pp. 105-115).
 * Section boundaries follow the USACE Principles & Guidelines (P&G) structure:
 * the four evaluation criteria (completeness, effectiveness, efficiency,
 * acceptability) and the four accounts (NED, RED, EQ, OSE). Examples are
 * excerpts from that report, with tables converted to markdown.
 */
@Service
public class ReportTemplateService
{
  public static final String PLAN_EVAL = "PLAN_EVAL";

  private static ReportTemplate planEvaluation()
  {
    return ReportTemplate.builder() //
        .id(PLAN_EVAL) //
        .name("Plan Evaluation") //
        // TODO: the source report follows the 1983 Principles & Guidelines as
        // implemented by ER 1105-2-100. Confirm whether new studies must instead
        // follow 33 CFR Part 234 (§234.9), effective 2025-01-17, and replace
        // this archived link with an official USACE copy.
        .regulation("Economic and Environmental Principles and Guidelines for Water and Related Land Resources "
            + "Implementation Studies (1983), as implemented by USACE ER 1105-2-100 Planning Guidance Notebook") //
        .regulationUrl("https://webarchive.library.unt.edu/web/20130218063957/http:/publications.usace.army.mil/publications/eng-regs/ER_1105-2-100/toc.pdf") //
        .sections(List.of( //
            evaluationFramework(), //
            nedNetBenefits(), //
            nedEffectiveness(), //
            acceptabilityAndCompleteness(), //
            redAnalysis(), //
            eqMethodology(), //
            eqResults(), //
            oseSocialVulnerability(), //
            osePopulationAtRisk(), //
            oseLifeSafety(), //
            oseEffectsOnPopulation(), //
            oseCriticalInfrastructure() //
        )) //
        .build();
  }

  private static Section evaluationFramework()
  {
    return Section.builder() //
        .name("Evaluation Framework") //
        .summary("""
            Introduces how the final (focused) array of alternatives was evaluated. Describes the comparison of
            with-project and without-project conditions, lists the Principles and Guidelines evaluation criteria
            (efficiency, effectiveness, acceptability, completeness) and the four accounts (NED, RED, EQ, OSE),
            states which resources or metrics were considered under each account for this study, and cites the
            policy guidance requiring comprehensive documentation of benefits. Largely standard text; the
            project-specific parts are the alternatives evaluated and the metrics chosen for each account.
            """) //
        .example("""
            The focused array of alternatives, as described above, was evaluated by projecting and comparing the
            with project and without project conditions. Plan formulation focused on addressing the identified
            problems and meeting study objectives, including those responsive to national, state, and local concerns.
            Alternative plans were assessed to determine if they have net benefits in total and by type. The four
            action-alternatives carried into the final array were evaluated on the Principles and Guidelines Criteria of:

            - Efficiency – The potential benefits/outcome of the measure are greater than what could be provided by another measure/plan of equal or greater cost.
            - Effectiveness – Extent to which a measure or alternative alleviates problem areas and meets planning objectives.
            - Acceptability – Viability and appropriateness of an alternative from the perspective of the general public and consistency with existing Federal laws, authorities, and public policies.
            - Completeness – Extent to which an alternative provides and accounts for all features, investments, and/or other actions necessary to realize the planned effects, including any necessary actions by others.

            Additionally, plans were assessed on the Principles and Guidelines four accounts:

            - National Economic Development (NED) – the value of national output of goods and services
            - Regional Economic Development (RED) – changes in regional income and employment
            - Environmental Quality (EQ) – riparian habitat, aquatic habitat, cultural resources, and trucking air emissions from sediment disposal offsite
            - Other Social Effects (OSE) – life safety, environmental justice, and critical infrastructure resiliency

            Comprehensive documentation of total benefits of project alternatives, including equal consideration of
            economic, environmental, and social categories has been undertaken in accordance with the Assistant
            Secretary for the Army for Civil Works (ASACW) Policy Directive on the "Comprehensive Documentation of
            Benefits in Decision Document," dated 5 January 2021.
            """) //
        .build();
  }

  private static Section nedNetBenefits()
  {
    return Section.builder() //
        .name("National Economic Development (NED) Analysis") //
        .summary("""
            The purpose of this section is to compare and contrast different alternative plans or flood scenarios for
            their cost effectiveness (the P&G efficiency criterion), measured in terms of net benefits. For each
            alternative, report average annual costs (construction plus OMRR&R), average annual benefits, net annual
            benefits (benefits minus costs) and the benefit-cost ratio, and identify the plan with the highest net
            benefits. State the modeling tool used for expected annual damages, the price level, the period of
            analysis and the discount rate.
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

            Table 21. Economic net benefits and benefit cost ratio (BCR) of alternatives carried forward.

            | Alternative | Average Annual Costs | Average Annual Benefits | Net Annual Benefits | Benefit to Cost Ratio |
            |---|---|---|---|---|
            | 2b – Modified Valley View Plan (NED Plan from 1998 FS/EIS/EIR) | $14.0M | $22.1M | $8.1M | 1.6 |
            | 3b – Modified Bypass Plan (previous authorized plan, partially constructed) | $21.2M | $21.9M | $0.7M | 1.0 |
            | 4 - Nonstructural Plan | $9.9M | $9.2M | $0.7M | 1.1 |
            | 7 – Low Scope Plan | $5.5M | $20.1M | $14.6M | 3.7 |
            | 8b – Combination Plan | $6.4M | $21.6M | $15.2M | 3.4 |

            Fiscal Year (FY) 2023 price levels, 50-year period of analysis, 2.5% discount rate
            """) //
        .build();
  }

  private static Section nedEffectiveness()
  {
    return Section.builder() //
        .name("Effectiveness and Residual Risk") //
        .summary("""
            Measures how well each alternative alleviates the problem (the P&G effectiveness criterion). For flood risk
            management studies this is the percent of expected annual flood damages reduced and the residual risk
            (damages that remain with the project in place), compared against the No Action alternative. Identify which
            alternatives are most and least effective and reference the appendix documenting the modeling.
            """) //
        .example("""
            Effectiveness of alternative plans was measured in percent of flood damages reduced. The alternatives
            were modeled using economic modeling software (HEC-FDA), and the results are shown in Table 22.
            More information on the modeling process and inputs are described in Appendix B. The Valley View
            Plan, Bypass Plan, and Combination Plan are most effective at reducing damages, while the Lower Scope
            Plan and Nonstructural Plan show the greatest remaining residual risk (i.e. risk that remains after the
            project is in place).

            Table 22. With-Project expected annual damages (residual risk) by damage category ($1,000s) for final
            array of alternatives.

            | Alternative | Total With-Project Damages | Damages Reduced | % Damages Reduced |
            |---|---|---|---|
            | 1. No Action | $22,528 | - | - |
            | 2b – Modified Valley View Plan (NED Plan from 1998 FS/EIS/EIR) | $467 | $22,061 | 98% |
            | 3b – Modified Bypass Plan (previous authorized plan, partially constructed) | $649 | $21,879 | 97% |
            | 4 - Nonstructural Plan | $12,610 | $9,918 | 43% |
            | 7 – Low Scope Plan | $2,457 | $20,071 | 87% |
            | 8b – Combination Plan | $939 | $21,589 | 95% |
            """) //
        .build();
  }

  private static Section acceptabilityAndCompleteness()
  {
    return Section.builder() //
        .name("Acceptability and Completeness") //
        .summary("""
            Addresses the remaining P&G criteria. Acceptability: whether each alternative is implementable and
            acceptable to the public, agencies and existing law and policy (e.g. space for mitigation, real estate
            constraints). Completeness: whether each alternative includes everything needed to realize its benefits,
            including actions required by others (e.g. voluntary participation by property owners in nonstructural
            measures) and how that dependency affects benefits and residual risk.
            """) //
        .example("""
            The Valley View and Bypass plans may be difficult to identify sufficient space for required mitigation.
            The Combination, Low Scope, and Nonstructural plans were found to be highly acceptable and
            implementable. Mitigation is expected to be contained within the flood risk management footprint.

            All alternative plans in the final array are expected to be complete plans that would not require further
            action from other to implement. For nonstructural measures on private properties, i.e. elevation of
            residences and dry floodproofing of privately owned commercial properties, individual property owners
            must opt-in to participate in the plan to manage flood risk at their property. The rate at which property
            owners participate, or participation rate, affects the effectiveness of the plan. As such, the participation
            rate will affect benefits and residual risk of the Nonstructural Plan.
            """) //
        .build();
  }

  private static Section redAnalysis()
  {
    return Section.builder() //
        .name("Regional Economic Development (RED) Analysis") //
        .summary("""
            Estimates the regional economic impact of constructing each alternative: output, jobs, labor income and
            gross regional product supported by project expenditures within the study region. Present construction
            costs per alternative and a comparison of RED results, rank the alternatives, and note the region analyzed,
            the price level and the appendix with details. RED effects are typically proportional to construction cost.
            """) //
        .example("""
            The RED analysis estimates number of jobs and other economic measures such as labor income, value
            added, and sales that are supported by future implementation of alternative plans. The team completed
            modeling to evaluate the regional economic impact and project expenditures, activities, and infrastructure.
            The results of this analysis are presented in Table 23 and Table 24, and Appendix B provides additional
            details.

            Construction costs were used to estimate the regional economic impacts of all alternatives in Santa Clara
            County. Generally, the higher the cost of construction, the higher the RED benefits for that alternative.
            Table 23 shows the construction costs for all plans. The Bypass Plan (Alt 3b) produces the highest
            amount of output ($373.1 M), followed by the Nonstructural Plan (alt 4) ($304.6 M), the Valley View
            Plan (Alt 2b) ($233.7 M), the Combination Plan (Alt 8b) ($83.9 M), and the Lower Scope Plan (Alt 7)
            ($59.4 M) (Table 24).

            Table 23. Construction costs, 2020 price level.

            | Alternative | Construction Cost |
            |---|---|
            | 2b – Modified Valley View Plan (NED Plan from 1998 FS/EIS/EIR) | $200.1 M |
            | 3b – Modified Bypass Plan (previous authorized plan, partially constructed) | $319.4 M |
            | 4 - Nonstructural Only Plan | $260.8 M |
            | 7 – Low Scope Plan | $50.9 M |
            | 8b – Combination Plan | $71.8 M |

            Table 24. Comparison of alternatives in RED, local impacts, 2022 price level.

            | Alternative | Output | Jobs | Labor Income | Gross Regional Product* |
            |---|---|---|---|---|
            | 2b – Modified Valley View Plan (NED Plan from 1998 FS/EIS/EIR) | $233.7 M | 1,543.5 | $147.4 M | $164.5 M |
            | 3b – Modified Bypass Plan (previous authorized plan, partially constructed) | $373.1 M | 2,463.3 | $235.3 M | $262.5 M |
            | 4 – Modified Nonstructural Only | $304.6 M | 2,011 | $192 M | $214.3 M |
            | 7 – Low Scope Plan | $59.4 M | 392.8 | $37.5 M | $41.8 M |
            | 8b – Combination Plan | $83.9 M | 554.5 | $52.9 M | $59 M |

            *Gross Regional product is defined as the sum of employee compensation, proprietor income, other
            property type income, and indirect business taxes.
            """) //
        .build();
  }

  private static Section eqMethodology()
  {
    return Section.builder() //
        .name("Environmental Quality (EQ) Analysis Methodology") //
        .summary("""
            Describes how net changes to the Environmental Quality account were measured: the habitat types
            evaluated, the models used (and their USACE certification or single-use approval status), the evaluation
            species selected for each habitat and why, and the habitat variables each model uses. Reference the
            appendix with the full habitat analysis.
            """) //
        .example("""
            To characterize net changes to the Environmental Quality (EQ) account, the study evaluated impacts to
            existing habitat and benefits from creation of new habitat for aquatic and riparian species, while
            maximizing use of existing data and previously certified models. The analysis combined two separate
            elements: 1) a hydraulic modeling based aquatic habitat suitability evaluation, and 2) a habitat suitability
            index (HSI) for the yellow warbler. The yellow warbler HSI is an already USACE-certified model, and the
            team worked with the Ecosystem Restoration Planning Center of Expertise (ECO-PCX) to obtain single-use
            authorization for a hydraulic habitat suitability analysis for steelhead habitat.

            Representative evaluation species were selected for each habitat type based on several criteria: (1) species
            known to be sensitive to specific land- and water- use actions; (2) species that play a key role in nutrient
            cycling or energy flow; (3) species that utilize a common environmental resource; (4) species that are
            associated with important resource problems, such as anadromous fish and migratory birds; (5) species
            that have existing habitat response models suitable for the evaluation of proposed alternatives; (6) habitat
            data available or easily collected to support modeling; (7) species that provide relevant evaluation
            throughout the geographic range of proposed alternatives and across the broad range of effects of
            proposed alternative. Table 25 below summarizes the habitats, species, and variables used in this analysis.
            A complete write-up of the habitat analysis can be found in Appendix C1.

            Table 25. Habitat type, species, and habitat variables used in the EQ analysis.

            | Habitat Type | Evaluation Species | Habitat Variables |
            |---|---|---|
            | Riverine | rearing and spawning steelhead | Depth, velocity, substrate and cover |
            | Riparian Forest | yellow warbler | Percent canopy cover, average canopy height |
            """) //
        .build();
  }

  private static Section eqResults()
  {
    return Section.builder() //
        .name("Environmental Quality (EQ) Analysis Results") //
        .summary("""
            Reports the habitat outcomes for each alternative relative to the future without-project condition, one
            habitat type at a time (e.g. riparian, aquatic): habitat units or acres gained or lost, how habitat changes
            over time after construction, which alternatives improve or degrade habitat and why, opportunities to
            improve habitat through design refinement, and any alternatives excluded from EQ modeling and why.
            """) //
        .example("""
            In general, the analysis found that the Combination and Lower Scope plans improve riparian habitat,
            while the Bypass and Valley View plans cause some riparian habitat degradation over time. For the
            Combination Plan, there is some reduction in habitat with the initial clearing and grubbing, but this
            habitat comes back within 5 years and then is substantially improved after 10 years of vegetation growth.

            For aquatic habitat, the analysis found that the alternatives generally perform somewhat similar to each
            other (Table 26). The Bypass and Valley View plans provide the most habitat as analyzed here because
            they widen the low-flow channel throughout the study area. This appears favorable because the analysis
            used depths and velocities during relatively low flows (mean winter and mean spring flow for spawning
            and rearing, respectively) to evaluate habitat. There is significant opportunity to refine designs to improve
            the provision of aquatic habitat, particularly through the use of selective floodplain grading and pool-
            forcing large wood structures in Reaches 7 and 8.

            Table 26. Results of rearing habitat analysis for project alternatives (acreage of steelhead rearing habitat
            by HSI class).

            | Plan | Low HSI (0.01 to 0.33) | Medium HSI (0.33 to 0.67) | High HSI (0.67 to 1) | Total med and high habitat (acres) | Change med and high (acres) |
            |---|---|---|---|---|---|
            | FWOP | 6.58 | 11.25 | 3.29 | 14.54 | 0 |
            | 2b – Modified Valley View | 6.14 | 13.77 | 3.25 | 17.02 | +2.47 |
            | 3b – Modified Bypass | 6.00 | 12.77 | 3.27 | 16.04 | +1.49 |
            | 7 - Low Scope | 6.58 | 11.16 | 3.31 | 14.47 | -0.08 |
            | 8b - Combo | 6.26 | 12.01 | 3.47 | 15.47 | +0.93 |

            The Nonstructural Plan (Alternative 4) is not expected to affect the EQ as it does not include in-channel
            work which would impact rearing nor aquatic habitat. Thus, it was not included in the EQ modeling.
            """) //
        .build();
  }

  private static Section oseSocialVulnerability()
  {
    return Section.builder() //
        .name("Other Social Effects (OSE): Social Vulnerability Screening") //
        .summary("""
            Introduces the OSE account and identifies socially vulnerable or disadvantaged communities in the study
            area. Cite the governing guidance, name the screening tool used (e.g. CDC Social Vulnerability Index) and
            why it was chosen, describe what the tool measures, and identify which areas of the study area are
            moderately or highly vulnerable.
            """) //
        .example("""
            Because the Administration's default Climate and Economic Justice Screening Tool (CEJST) is still in
            beta version, the CDC's Social Vulnerability Index (SVI) tool was used to screen for and identify
            disadvantaged communities for the Upper Guadalupe Flood Risk Management Project. The tool was used
            to measure impacts to the OSE account from the no-action, nonstructural, and structural alternatives.

            SVI data was used to identify socially vulnerable communities and to see how various alternatives would
            affect the people in each socially vulnerable group (either positively or negatively) as part of the OSE
            assessment. The SVI uses U.S. Census data to determine the relative social vulnerability of every census
            tract. It ranks each tract on 14 social factors and identifies and maps communities most likely needing
            support during and after a hazardous event such as flooding. These factors are aggregated into four main
            "themes:" Socioeconomic, Household Composition and Disability, Minority Status and Language, and
            Housing Type and Transportation. The sum of all four themes reflects the overall percentile tract summary
            ranking, or overall theme, for each Census tract.
            """) //
        .build();
  }

  private static Section osePopulationAtRisk()
  {
    return Section.builder() //
        .name("Other Social Effects (OSE): Population at Risk") //
        .summary("""
            Quantifies who and what is exposed to flooding under without-project conditions: population at risk (PAR)
            in the relevant floodplains (e.g. 1% and 0.2% AEP), how much of the PAR lives in socially vulnerable
            areas, structure counts, maximum flood depths and total damages by occupancy type, and any populations
            not captured by structure inventories (e.g. unhoused persons living in or near the channel).
            """) //
        .example("""
            The population at risk (PAR) is approximately 3,490 persons in the 1% ACE floodplain and 5,726
            persons in the 0.2% floodplain (excluding unhoused persons). There is a large population of socially
            vulnerable people residing in the 1% AEP event; approximately half of the PAR reside in moderate to
            high socially vulnerable areas, which are identified as Flooding Impact Areas 1, 2, 6, and 7. Table 27
            shows the structure distribution, PAR, maximum depths, and total damages at structure by occupancy
            type at the 1% AEP and the 0.2% AEP events.

            Table 27. Population at risk (PAR), structure distribution, maximum depth, and total damages for the 1%
            and 0.2% AEP flood events.

            | Occupancy Type | PAR 1% AEP | PAR 0.2% AEP | Max Depth 1% AEP (ft) | Max Depth 0.2% AEP (ft) | Total Damages 1% AEP | Total Damages 0.2% AEP |
            |---|---|---|---|---|---|---|
            | Autos | 7,553 | 9,996 | 8.3 | 9.4 | $72 M | $118 M |
            | Commercial | 81 | 136 | 3.3 | 4.7 | $35 M | $93 M |
            | Industrial | 118 | 174 | 4.4 | 5.5 | $82 M | $130 M |
            | Public | 21 | 29 | 2.5 | 3.3 | $63 M | $88 M |
            | Residential | 1,175 | 1,928 | 6.4 | 7.9 | $323 M | $472 M |
            | Population at Residential Structures | 3,490 | 5,726 | | | | |

            Another component of the PAR is comprised of encampments living in or near the channel, where
            velocities, depths, and velocity-depth combinations pose a significant life safety hazard to transient,
            vulnerable persons. There are approximately 33 unhoused encampments and over 130 persons that will
            likely be touched by open floodwater at the 1% AEP event. Ten of the 33 affected camps are likely to
            experience moderate or high risk flood velocities and depths.
            """) //
        .build();
  }

  private static Section oseLifeSafety()
  {
    return Section.builder() //
        .name("Other Social Effects (OSE): Life Safety") //
        .summary("""
            Describes how life safety risk was evaluated and what it shows for each alternative: the method and
            criteria used (e.g. LifeSim depth, velocity and velocity-depth thresholds), how hazard zones are classified,
            the limitations and assumptions of the analysis, whether each alternative reduces high and moderate
            hazard zones, where residual hazards remain, and any further life safety analysis planned.
            """) //
        .example("""
            Life safety was evaluated using LifeSim 2.0 criteria. This analysis does not consider the life safety risk to
            persons in structures or in vehicles on roadways; rather, the analysis assumes a "worst case scenario"
            event where persons are openly caught in fast-moving and deep floodwater. The LifeSim default stability
            criteria function was used to classify depth, velocity, and velocity-depth combinations into "low," "high,"
            and "moderate" life safety hazard zones. The critical threshold for velocity was set to 9.8 ft/s, while the
            critical threshold for velocity*depth was set to 6.46 ft squared/s. The critical threshold for depth is 4 ft.
            If the velocity and velocity*depth thresholds are exceeded, this would constitute a "high" life safety
            hazard zone. If the depth threshold is exceeded, this would constitute a "moderate" life safety hazard zone
            because the risk to life would be dependent upon if pedestrians could or could not swim. The PDT fully
            intends to implement a LifeSim 2.0 assessment post-TSP and will conduct a breach analysis to determine
            the incremental life loss of project implementation.

            All structural alternatives successfully minimize the high and moderate life safety hazard zones in the
            study area. While this is not a substitute for LifeSim 2.0, the analysis is informative of the magnitude of
            potential consequences related to life safety hazards in the study area and what each plan does to reduce
            the residual risk of flooding. Most of the residual flooding has shallow depths and low velocities, but the
            Lower Scope plan does have residual life safety hazard zones in flooding impact area 8.
            """) //
        .build();
  }

  private static Section oseEffectsOnPopulation()
  {
    return Section.builder() //
        .name("Other Social Effects (OSE): Effects of Alternatives on Socially Vulnerable Populations") //
        .summary("""
            Compares how each alternative affects people, with emphasis on socially vulnerable groups: the change in
            population removed from the floodplain in vulnerable versus other areas, qualitative social effects such as
            community disruption or loss of cohesion (especially for nonstructural measures like elevation or
            relocation), and how the alternatives perform against the OSE social factors (health and safety, economic
            vitality, social connectedness, identity, social vulnerability and resiliency, participation, leisure and
            recreation).
            """) //
        .example("""
            The nonstructural alternative considers the elevation of residential structures to the 1% AEP event and 3ft
            dry floodproofing on commercial structures. While this option would be highly effective at reducing flood
            risk to damageable property, it would likely not reduce the amount of high and moderate hazard life
            safety pockets in the area. In addition, such activities would significantly disrupt the community by
            dispersing residents and business activity. This would adversely impact hundreds of families and
            relationships in the community.

            In general, all structural alternatives are expected to contribute positively to the OSE factors, as defined in
            the USACE OSE Primer. To assess the impact of each alternative on socially vulnerable groups, the
            change in the number of persons removed from the 1% AEP floodplain by plan was assessed. All
            alternatives, excluding the nonstructural alternative, significantly reduce the PAR (Table 28).

            Table 28. Percent of persons and structures removed from the 1% AEP by Plan, $0 Damages or Greater
            (SVI Tool).

            | Alternative | Population at Residential Structures in Socially Vulnerable Flooding Impact Areas (1, 2, 6, and 7) | Population at Residential Structures in all Other Flooding Impact Areas (3, 4, 5, 8, 9, 10, and 11) | Total Population at Residential Structures in Study Area |
            |---|---|---|---|
            | 2b - Valley View Plan (NED Plan from 1998 FS/EIS/EIR) | -99.3% | -97.9% | -98.6% |
            | 3b - Bypass Plan (previous authorized plan, partially constructed) | -100.0% | -99.8% | -98.9% |
            | 4 - Nonstructural Only | -74.8% | -32.5% | -54.0% |
            | 7 – Low Scope Plan | -100.0% | -80.3% | -71.2% |
            | 8b – Combination Plan | -99.7% | -99.1% | -99.4% |
            """) //
        .build();
  }

  private static Section oseCriticalInfrastructure()
  {
    return Section.builder() //
        .name("Other Social Effects (OSE): Critical Infrastructure Resiliency") //
        .summary("""
            Identifies critical infrastructure (schools, hospitals, emergency services, utilities, transportation) in the
            without-project floodplain and reports how many critical facilities each alternative removes from or leaves
            in the floodplain.
            """) //
        .example("""
            The OSE analysis also considered critical infrastructure resiliency. There are 11 structures flagged as
            critical infrastructure in the FWOP 0.2% AEP event floodplain, including the Canoas Creek Elementary
            School that will be impacted by flooding at less than 3ft of flood depths. The nonstructural alternative
            removes two critical structures from the floodplain while the other nine remain unprotected. The
            structural alternatives remove all 11 critical structures from the floodplain.
            """) //
        .required(false) //
        .build();
  }

  public List<ReportTemplate> getAll()
  {
    return List.of(planEvaluation());
  }

  public ReportTemplate get(String id)
  {
    return getAll().stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
  }

}
