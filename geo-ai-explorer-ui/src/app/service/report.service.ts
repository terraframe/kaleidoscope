import { Injectable } from '@angular/core';
import { v4 as uuidv4 } from 'uuid';

import { ChatMessage, Fact, Project } from '../models/chat.model';
import { ReportSelection, ReportTemplate } from '../models/report.model';

/**
 * The report generation chat. Templates come with the app's configuration
 * (see getReportTemplates); projects and facts from ProjectService and
 * FactService.
 *
 * TODO: sendMessage is still a mock. POST /api/chat/report/start exists, but
 * only takes a session and prompt; it needs the template, facts by section
 * and section notes too.
 */
@Injectable({
  providedIn: 'root',
})
export class ReportService {
  /**
   * Sends a message to the report generation agent. Resolves with the
   * agent's reply, like ChatService.sendMessage. `template`, `project` and
   * `facts` are the selection's, already loaded by the caller.
   */
  sendMessage(
    _sessionId: string,
    message: ChatMessage,
    selection: ReportSelection,
    template: ReportTemplate | undefined,
    project: Project | undefined,
    facts: Fact[]
  ): Promise<ChatMessage> {
    const factName = (id: number) => facts.find((f) => f.id === id)?.name ?? `#${id}`;
    const sections = (template?.sections ?? []).map((section) => {
      const ids = selection.sectionFacts[section.name] ?? [];
      const notes = selection.sectionNotes[section.name]?.trim();
      return (
        `- ${section.name}: ${ids.length > 0 ? ids.map(factName).join(', ') : '_no facts_'}` +
        (notes ? ` (notes: "${notes}")` : '')
      );
    });

    const lines = [
      '_The report generation agent is not connected yet. This is a placeholder reply._',
      '',
      `**Template:** ${template?.name ?? 'none selected'}`,
      `**Project:** ${project?.name ?? 'none selected'}`,
      `**Facts:** ${facts.length > 0 ? facts.map((f) => f.name).join(', ') : 'none selected'}`,
      ...(sections.length > 0 ? ['', '**Sections:**', ...sections] : []),
      '',
      `> ${message.text}`,
    ];

    return new Promise((resolve) => {
      setTimeout(
        () =>
          resolve({
            id: uuidv4(),
            sender: 'system',
            text: lines.join('\n'),
            mappable: false,
            ambiguous: false,
            purpose: 'standard',
            statements: [],
          }),
        600
      );
    });
  }
}
