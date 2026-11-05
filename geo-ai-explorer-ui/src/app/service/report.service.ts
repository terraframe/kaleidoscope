import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';

import { ChatMessage } from '../models/chat.model';
import { Fact, Project, ReportSelection, ReportTemplate } from '../models/report.model';
import { MOCK_TEMPLATES } from './report-templates.mock';

/**
 * Templates, projects and facts for the report generation chat.
 *
 * TODO: The back end is not hooked up yet. Everything here is mock data held
 * in memory. When it is wired in:
 *  - facts:      GET/PUT/DELETE /api/fact/facts/... (FactController)
 *  - templates:  needs an endpoint over ReportTemplateService
 *  - projects:   needs an endpoint (ToolService.list_projects has the data)
 *  - sendMessage: needs a report chat endpoint that takes the selection
 */
@Injectable({
  providedIn: 'root',
})
export class ReportService {

  private readonly templates: ReportTemplate[] = MOCK_TEMPLATES;
  private readonly projects: Project[] = MOCK_PROJECTS;
  private readonly factsSubject = new BehaviorSubject<Fact[]>(MOCK_FACTS);

  readonly facts$: Observable<Fact[]> = this.factsSubject.asObservable();

  getTemplates(): ReportTemplate[] {
    return this.templates;
  }

  getTemplate(id: string | null | undefined): ReportTemplate | undefined {
    return this.templates.find(t => t.id === id);
  }

  getProjects(): Project[] {
    return this.projects;
  }

  getProject(id: number | null | undefined): Project | undefined {
    return this.projects.find(p => p.id === id);
  }

  getFacts(): Fact[] {
    return this.factsSubject.value;
  }

  deleteFact(id: number): void {
    this.factsSubject.next(this.factsSubject.value.filter(f => f.id !== id));
  }

  /**
   * Sends a message to the report generation agent. Resolves with the
   * agent's reply, like ChatService.sendMessage.
   */
  sendMessage(_sessionId: string, message: ChatMessage, selection: ReportSelection): Promise<ChatMessage> {
    const template = this.getTemplate(selection.templateId);
    const project = this.getProject(selection.projectId);
    const facts = this.getFacts().filter(f => selection.factIds.includes(f.id));
    const factName = (id: number) => facts.find(f => f.id === id)?.name ?? `#${id}`;
    const sections = (template?.sections ?? []).map(section => {
      const ids = selection.sectionFacts[section.name] ?? [];
      const notes = selection.sectionNotes[section.name]?.trim();
      return `- ${section.name}: ${ids.length > 0 ? ids.map(factName).join(', ') : '_no facts_'}`
        + (notes ? ` (notes: "${notes}")` : '');
    });

    const lines = [
      '_The report generation agent is not connected yet. This is a placeholder reply._',
      '',
      `**Template:** ${template?.name ?? 'none selected'}`,
      `**Project:** ${project?.name ?? 'none selected'}`,
      `**Facts:** ${facts.length > 0 ? facts.map(f => f.name).join(', ') : 'none selected'}`,
      ...(sections.length > 0 ? ['', '**Sections:**', ...sections] : []),
      '',
      `> ${message.text}`
    ];

    return new Promise(resolve => {
      setTimeout(() => resolve({
        id: uuidv4(),
        sender: 'system',
        text: lines.join('\n'),
        mappable: false,
        ambiguous: false,
        purpose: 'standard'
      }), 600);
    });
  }
}

const MOCK_PROJECTS: Project[] = [
  { id: 1, name: 'Upper Guadalupe River GRR' },
  { id: 2, name: 'Lower Mississippi Levee Assessment' }
];

const MOCK_FACTS: Fact[] = [
  {
    id: 101,
    projectId: 1,
    projectName: 'Upper Guadalupe River GRR',
    name: 'Population in the 1% floodplain',
    description: 'Total census population inside the 1% annual chance floodplain.',
    text: 'An estimated 42,180 people live in census tracts that intersect the 1% annual chance floodplain.',
    statements: 'SELECT (SUM(?pop) AS ?total) WHERE { ?tract a :CensusTract ; :population ?pop ; :intersects ?fz . ?fz a :FloodZone100 }'
  },
  {
    id: 102,
    projectId: 1,
    projectName: 'Upper Guadalupe River GRR',
    name: 'Schools at risk of flooding',
    description: 'Schools located in leveed areas downstream of the study reach.',
    text: '14 schools serving about 9,300 students are in leveed areas downstream of the study reach.',
    statements: 'SELECT ?school WHERE { ?school a :School ; :locatedIn ?area . ?area a :LeveedArea }'
  },
  {
    id: 103,
    projectId: 1,
    projectName: 'Upper Guadalupe River GRR',
    name: 'Hospitals in the floodplain',
    description: 'Hospitals with critical services inside the floodplain.',
    text: 'Two hospitals with a combined 610 beds are inside the 1% floodplain.'
  },
  {
    id: 201,
    projectId: 2,
    projectName: 'Lower Mississippi Levee Assessment',
    name: 'Channel reach CEMVK_RR_03_ONE_25 impact',
    description: 'Population affected if channel reach CEMVK_RR_03_ONE_25 floods.',
    text: 'Flooding of channel reach CEMVK_RR_03_ONE_25 would affect about 18,450 residents across 6 census tracts.'
  },
  {
    id: 202,
    projectId: 2,
    projectName: 'Lower Mississippi Levee Assessment',
    name: 'Leveed area real property value',
    description: 'Real property value protected by the levee system.',
    text: 'The levee system protects about $2.1B of real property.'
  }
];
