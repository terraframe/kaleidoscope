/**
 * The kind of agent a chat conversation talks to. Chosen when the
 * conversation is created and fixed for its lifetime.
 */
export type ChatType = 'exploration' | 'report';

/** Mirrors net.geoprism.geoai.explorer.core.model.Section. */
export interface ReportSection {
  name: string;
  summary: string;
  example?: string;
  required: boolean;
}

/** Mirrors net.geoprism.geoai.explorer.core.model.ReportTemplate. */
export interface ReportTemplate {
  id: string;
  name: string;
  regulation?: string;
  regulationUrl?: string;
  sections: ReportSection[];
}

/** What a report generation chat has been set up with. */
export interface ReportSelection {
  templateId: string | null;
  projectId: number | null;
  /** Every fact in the report, whether or not it is assigned to a section. */
  factIds: number[];
  /** Section name -> ids of the facts assigned to it. A fact may be in several sections. */
  sectionFacts: Record<string, number[]>;
  /** Section name -> the user's own text for the agent to use in that section. */
  sectionNotes: Record<string, string>;
}
