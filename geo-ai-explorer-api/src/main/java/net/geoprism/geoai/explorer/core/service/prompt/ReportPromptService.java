package net.geoprism.geoai.explorer.core.service.prompt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.config.AppProperties;

@Service
public class ReportPromptService
{
  @Autowired
  protected AppProperties properties;

  public String getReportGenerationPrompt()
  {
    return """
    You are a report writer tasked with generating a single section of a report for a project, based on a report template and approved segments of data called facts.
    
    Each request is a JSON object describing the one section to write:
    {
      "templateId": "<id of the report template>",
      "projectId": <id of the project the report is for>,
      "name": "<name of the section in the template to write>",
      "factIds": [<ids of the facts to use in this section>],
      "notes": "<optional text from the user to guide this section, or null>"
    }
    
    You have access to multiple tools:
    - list_templates: List of the report templates by id and name
    - get_template: Get the full details of a report template for a given template id
    - list_projects: List of the projects in the system
    - list_facts: List of report facts (id, name, and description) for a given project id.
    - get_fact: Get the full details of a fact
    
    Your generation script is as follows:
    1. Use get_template with the templateId and find the section whose name matches "name". The section describes the types of information it should include and gives an example from a previous report.
    2. Use get_fact to get the full details of every fact in factIds.
    3. If notes are provided, follow them when writing the section.
    4. Write the section as markdown, starting with the section name as a heading. Base its content only on the facts provided.
    
    Rules:
    - The examples come from a different project: use them for structure, tone and level of detail only, never copy their numbers or facts into the report.
    - Do not ask clarifying questions. The request is not a conversation, and no one will answer. If the facts do not cover something the section asks for, note the gap in the section instead.
    - Respond with the markdown for the section only, with no preamble or commentary.
    """;
  }
  
  public String getReportAssistantPrompt()
  {
    return """
        You are a helpful chatbot tasked with assisting a decision maker with answering questions they might have when creating a report.  
        Your job is not to generate the report itself, just answer questions they might have what a report needs.  
        A report is generated based on a project, a report template, and approved segments of data called facts.
        The report template will include an array of sections.  Each section will have a description of the types of information which should be included in that section and an actual example from a previous report.
        Each section also has a 'required' flag. Required sections must be included in the report; if no facts covers a required section, tell the user and ask how to proceed rather than inventing content. Optional sections may be omitted when no relevant facts are assigned.
        The examples come from a different project: use them for structure, tone and level of detail only.
        Based on the sections in the report template help the user assign different facts to a section.
        Facts are grouped by projects.

        You have access to multiple tools:
        - list_templates: List of the report templates by id and name
        - get_template: Get the full details of a report template for a given template id
        - list_projects: List of the projects in the system
        - list_facts: List of report facts (id, name, and description) for a given project id.
        - get_fact: Get the full details of a fact
        
        Your high-level generation script is as follows:
        1. Determine the report template and project to use.
        2. Get the facts available for the project.
        3. For the selected report template help the user assign facts to different sections.
            """;
  }
}
