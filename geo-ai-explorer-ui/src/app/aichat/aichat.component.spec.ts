import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { AichatComponent } from './aichat.component';
import { provideTestApp } from '../testing/test-providers';
import { Fact, Project } from '../models/chat.model';
import { apiBase } from '../service/url.util';

const PROJECTS: Project[] = [
  { id: 1, name: 'Upper Guadalupe River GRR' },
  { id: 2, name: 'Lower Mississippi Levee Assessment' },
];

const FACTS: Fact[] = [
  {
    id: 101,
    projectId: 1,
    projectName: 'Upper Guadalupe River GRR',
    name: 'Population in the 1% floodplain',
    description: 'Census population inside the 1% floodplain.',
    text: 'About 42,180 people.',
    statements: ['SELECT (SUM(?pop) AS ?total) WHERE { }'],
  },
  {
    id: 102,
    projectId: 1,
    projectName: 'Upper Guadalupe River GRR',
    name: 'Schools at risk of flooding',
    description: 'Schools in leveed areas.',
    text: '14 schools.',
    statements: [],
  },
  {
    id: 201,
    projectId: 2,
    projectName: 'Lower Mississippi Levee Assessment',
    name: 'Leveed area real property value',
    description: 'Real property protected by the levee system.',
    text: 'About $2.1B.',
    statements: [],
  },
];

describe('AichatComponent', () => {
  let component: AichatComponent;
  let fixture: ComponentFixture<AichatComponent>;
  let http: HttpTestingController;

  /** Lets pending promise callbacks run (the app is zoneless, so whenStable doesn't wait for them). */
  const settle = () => new Promise((resolve) => setTimeout(resolve));

  /** Answers the component's project and fact requests with the fixtures above. */
  async function flushProjectsAndFacts(): Promise<void> {
    http.expectOne(apiBase() + 'api/projects').flush(PROJECTS);
    await settle();

    for (const project of PROJECTS) {
      http.expectOne(apiBase() + 'api/facts/' + project.id).flush(FACTS.filter((f) => f.projectId === project.id));
    }

    await settle();
  }

  beforeEach(async () => {
    localStorage.clear();

    await TestBed.configureTestingModule({
      imports: [AichatComponent],
      providers: provideTestApp(),
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AichatComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    await flushProjectsAndFacts();
  });

  it('loads projects and their facts from the server', () => {
    expect(component.projects).toEqual(PROJECTS);
    expect(component.facts.map((f) => f.id)).toEqual([101, 102, 201]);
    expect(component.factGroups.map((g) => g.projectName)).toEqual(PROJECTS.map((p) => p.name));
  });

  it('deletes a fact on the server and removes it from reports', async () => {
    component.newConversation(true, 'report');
    component.selectTemplate('PLAN_EVAL_234');
    component.selectProject(1);
    const section = component.selectedTemplate!.sections[0];
    component.assignFactToSection(section, 101);

    const confirm = spyOn((component as any).confirmationService, 'confirm').and.callFake((options: any) =>
      options.accept()
    );
    component.deleteFact(new Event('click'), component.facts[0]);

    expect(confirm).toHaveBeenCalled();
    const req = http.expectOne(apiBase() + 'api/facts/101');
    expect(req.request.method).toBe('DELETE');
    req.flush('Deleted Successfully');
    await settle();

    expect(component.facts.map((f) => f.id)).toEqual([102, 201]);
    expect(component.activeReport!.factIds).toEqual([]);
    expect(component.factsInSection(section)).toEqual([]);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('creates a report generation chat from the chooser', () => {
    component.toggleChatTypeChooser();
    component.newConversation(true, 'report');

    expect(component.isReportChat).toBeTrue();
    expect(component.activeConversation?.title).toBe('New report');
    expect(component.activeReport).toEqual({
      templateId: null,
      projectId: null,
      factIds: [],
      sectionFacts: {},
      sectionNotes: {},
    });
    expect(component.choosingChatType).toBeFalse();
  });

  it('keeps chat type and report selection across reloads', () => {
    component.newConversation(true, 'report');
    component.selectTemplate('PLAN_EVAL_234');
    component.selectProject(1);
    const id = component.activeConversationId;

    const reloaded = TestBed.createComponent(AichatComponent).componentInstance;

    expect(reloaded.activeConversationId).toBe(id);
    expect(reloaded.isReportChat).toBeTrue();
    expect(reloaded.activeReport?.templateId).toBe('PLAN_EVAL_234');
    expect(reloaded.activeReport?.projectId).toBe(1);
  });

  it('clears picked facts when the project changes', () => {
    component.newConversation(true, 'report');
    component.selectTemplate('PLAN_EVAL_234');
    component.selectProject(1);
    const section = component.selectedTemplate!.sections[0];
    component.assignFactToSection(section, component.availableReportFacts[0].id!);

    expect(component.availableReportFacts.every((f) => f.projectId === 1)).toBeTrue();

    component.selectProject(2);

    expect(component.activeReport!.factIds).toEqual([]);
    expect(component.factsInSection(section)).toEqual([]);
  });

  it('assigns facts to template sections', () => {
    component.newConversation(true, 'report');
    component.selectTemplate('PLAN_EVAL_234');
    component.selectProject(1);
    const [first, second] = component.selectedTemplate!.sections;
    const fact = component.availableReportFacts[0];
    const before = component.requiredSectionCoverage.covered;

    component.assignFactToSection(first, fact.id!);
    component.assignFactToSection(second, fact.id!);

    expect(component.factsInSection(first)).toEqual([fact]);
    expect(component.factsInSection(second)).toEqual([fact]);
    expect(component.activeReport!.factIds).toEqual([fact.id!]);
    expect(component.factsAssignableToSection(first)).not.toContain(fact);
    expect(component.requiredSectionCoverage.covered).toBe(before + 2);

    // Still in another section, so it stays in the report
    component.unassignFactFromSection(first, fact);
    expect(component.activeReport!.factIds).toEqual([fact.id!]);

    // In no section now, so it leaves the report
    component.unassignFactFromSection(second, fact);
    expect(component.activeReport!.factIds).toEqual([]);
  });

  it('counts a required section with notes as covered, and keeps notes across reloads', () => {
    component.newConversation(true, 'report');
    component.selectTemplate('PLAN_EVAL_234');
    const section = component.selectedTemplate!.sections.find((s) => s.required)!;
    const before = component.requiredSectionCoverage.covered;

    component.setSectionNotes(section, 'Use the 2023 price level throughout.');

    expect(component.sectionHasContent(section)).toBeTrue();
    expect(component.requiredSectionCoverage.covered).toBe(before + 1);

    const reloaded = TestBed.createComponent(AichatComponent).componentInstance;
    expect(reloaded.sectionNotes(section)).toBe('Use the 2023 price level throughout.');

    component.setSectionNotes(section, '');
    expect(component.activeReport!.sectionNotes[section.name]).toBeUndefined();
  });

  it('shows template context and notes when a section is expanded', () => {
    component.newConversation(true, 'report');
    component.selectTemplate('PLAN_EVAL_234');
    component.selectProject(1);
    const section = component.selectedTemplate!.sections[0];
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.section-details')).toBeNull();

    (fixture.nativeElement.querySelector('.section-assignment-header') as HTMLButtonElement).click();
    fixture.detectChanges();

    const details: HTMLElement = fixture.nativeElement.querySelector('.section-details');
    expect(details.querySelector('.section-summary')!.textContent).toContain(section.summary.substring(0, 40));
    expect(details.querySelector('.section-example')).not.toBeNull();
    expect(details.querySelector('textarea')).not.toBeNull();
  });

  it('generates only once a template and project are picked, and keeps the draft', () => {
    component.newConversation(true, 'report');
    component.message = 'half-typed question';
    const send = spyOn(component, 'sendMessage').and.callThrough();

    component.generateReport();
    expect(send).not.toHaveBeenCalled();

    component.selectTemplate('PLAN_EVAL_234');
    component.selectProject(1);
    component.generateReport();

    expect(send).toHaveBeenCalledTimes(1);
    expect(component.activeConversation!.messages[0].text).toContain('Generate the report');
    expect(component.message).toBe('half-typed question');
    expect(component.canGenerateReport).toBeFalse();
  });

  it('toggles a section from anywhere on its card, but not from its controls', () => {
    component.newConversation(true, 'report');
    component.selectTemplate('PLAN_EVAL_234');
    component.selectProject(1);
    const section = component.selectedTemplate!.sections[0];
    component.assignFactToSection(section, component.availableReportFacts[0].id!);
    fixture.detectChanges();

    const card: HTMLElement = fixture.nativeElement.querySelector('.section-assignment');

    card.querySelector<HTMLElement>('.section-facts')!.click();
    expect(component.isSectionExpanded(section)).toBeTrue();

    card.querySelector<HTMLElement>('.add-fact-select')!.click();
    card.querySelector<HTMLElement>('.assigned-fact-remove')!.click();
    fixture.detectChanges();
    expect(component.isSectionExpanded(section)).toBeTrue();
    expect(component.factsInSection(section)).toEqual([]);

    card.querySelector<HTMLElement>('.section-details textarea')!.click();
    expect(component.isSectionExpanded(section)).toBeTrue();

    card.querySelector<HTMLElement>('.section-assignment-header')!.click();
    expect(component.isSectionExpanded(section)).toBeFalse();
  });

  it('lists facts as not in a section when a template change drops their section', () => {
    component.newConversation(true, 'report');
    component.selectTemplate('PLAN_EVAL_234');
    component.selectProject(1);
    const fact = component.availableReportFacts[0];
    // Only PLAN_EVAL_234 has this section
    component.assignFactToSection(component.selectedTemplate!.sections[1], fact.id!);

    expect(component.unassignedFacts).toEqual([]);

    component.selectTemplate('PLAN_EVAL');

    expect(component.unassignedFacts).toEqual([fact]);

    component.assignFactToSection(component.selectedTemplate!.sections[0], fact.id!);

    expect(component.unassignedFacts).toEqual([]);
  });

  it('has no add-to-report controls on Facts tab cards', () => {
    component.newConversation(true, 'report');
    component.setActiveSidebarTab('facts');
    fixture.detectChanges();

    const card: HTMLElement = fixture.nativeElement.querySelector('.fact-card');

    expect(card.querySelectorAll('.query-action').length).toBe(1);
    expect(card.querySelector('.query-action')!.getAttribute('title')).toBe('Delete fact');
  });

  it('offers Save as Fact only on system answers that have query statements', () => {
    const conversation = component.activeConversation!;
    conversation.messages = [
      { id: 'u', sender: 'user', text: 'q', mappable: false, purpose: 'standard', statements: ['SELECT 1'] },
      { id: 'a', sender: 'system', text: 'no query', mappable: false, purpose: 'standard', statements: [] },
      {
        id: 'b',
        sender: 'system',
        text: 'with query',
        mappable: false,
        purpose: 'standard',
        statements: ['SELECT ?s'],
      },
    ];
    (component as any).refreshRenderedMessages();
    fixture.detectChanges();

    const buttons = Array.from<HTMLElement>(
      fixture.nativeElement.querySelectorAll('.mappable-actions p-button')
    ).filter((b) => b.textContent?.includes('Save as Fact'));

    expect(buttons.length).toBe(1);
    expect(buttons[0].closest('.message')!.id).toBe(component.chatMessageElementId('b'));
  });

  it('shows facts grouped by project in the Facts tab', () => {
    component.setActiveSidebarTab('facts');
    fixture.detectChanges();

    const headings = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.fact-group-heading'));

    expect(headings.length).toBe(component.factGroups.length);
    expect(fixture.nativeElement.querySelectorAll('.fact-card').length).toBe(component.facts.length);
  });
});
