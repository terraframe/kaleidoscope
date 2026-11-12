import { Component, ElementRef, HostListener, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { Store } from '@ngrx/store';
import { combineLatest, Observable, Subscription } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import {
  faEraser,
  faUpRightAndDownLeftFromCenter,
  faUser,
  faPlus,
  faXmark,
  faPencil,
  faMapLocation,
  faFloppyDisk,
  faTrash,
  faBrain,
  faMagnifyingGlassChart,
  faFileLines,
  faLightbulb,
  faChevronDown,
  faChevronRight,
  faNoteSticky,
} from '@fortawesome/free-solid-svg-icons';

import { ChatService } from '../service/chat-service.service';
import { initialState, parseText } from '../state/chat.state';
import { ChatMessage, Fact, LocationPage, Project } from '../models/chat.model';
import { ErrorService } from '../service/error-service.service';
import {
  ExplorerActions,
  getReportTemplates,
  getWorkflowData,
  getWorkflowStep,
  WorkflowStep,
} from '../state/explorer.state';
import { ExplorerService } from '../service/explorer.service';
import { GeoObject } from '../models/geoobject.model';
import { CachedExplorerPages, ExplorerSessionStateService } from '../service/explorer-session-state.service';
import { ReportService } from '../service/report.service';
import { ChatType, ReportSection, ReportSelection, ReportTemplate } from '../models/report.model';

import { marked } from 'marked';

import { ConfirmationService, MessageService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { FactService } from '../service/fact.service';
import { ProjectService } from '../service/project.service';

interface ChatConversation {
  id: string;
  title: string;
  sessionId: string;
  messages: ChatMessage[];
  draft: string;
  loading: boolean;
  createdAt: number;
  type: ChatType;
  /** Only set when type is 'report'. */
  report?: ReportSelection;
}

type SidebarTab = 'chat' | 'queries' | 'facts';

interface FactGroup {
  projectId: number;
  projectName: string;
  facts: Fact[];
}

/** Where the fact dialog will save a fact. `id` is null for a project the save will create. */
interface ProjectChoice {
  id: number | null;
  name: string;
}

interface RenderedChatMessage extends ChatMessage {
  renderedHtml: string;
  savedQueryIndex: number | null;
}

@Component({
  selector: 'aichat',
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    ProgressSpinnerModule,
    FontAwesomeModule,
    TooltipModule,
    ConfirmDialogModule,
    DialogModule,
    AutoCompleteModule,
  ],
  providers: [ConfirmationService, FactService],
  templateUrl: './aichat.component.html',
  styleUrl: './aichat.component.scss',
})
export class AichatComponent {
  private readonly STORAGE_KEY = 'aichat.conversations.v1';
  private readonly ACTIVE_CONVERSATION_STORAGE_KEY = 'aichat.activeConversationId.v1';
  private readonly ACTIVE_SIDEBAR_TAB_STORAGE_KEY = 'aichat.activeSidebarTab.v1';
  private readonly SIDEBAR_WIDTH_STORAGE_KEY = 'aichat.sidebarWidth.v1';
  private readonly minSidebarWidthPx = 280;
  private readonly maxSidebarWidthPx = 520;

  icon = faEraser;
  edit = faPencil;
  mapIcon = faMapLocation;
  public newConversationIcon = faPlus;
  public closeConversationIcon = faXmark;
  public saveQueryIcon = faFloppyDisk;
  public deleteQueryIcon = faTrash;
  public viewQueryIcon = faMapLocation;

  public messageUserIcon = faUser;
  public messageSenderIcon = faUpRightAndDownLeftFromCenter;
  public reasoningIcon = faBrain;
  public explorationChatIcon = faMagnifyingGlassChart;
  public reportChatIcon = faFileLines;
  public factIcon = faLightbulb;
  public expandIcon = faChevronDown;
  public sectionCollapsedIcon = faChevronRight;
  public sectionNotesIcon = faNoteSticky;

  private store = inject(Store);

  workflowStep$: Observable<WorkflowStep> = this.store.select(getWorkflowStep);
  workflowData$: Observable<any> = this.store.select(getWorkflowData);
  savedQueries$: Observable<CachedExplorerPages[]>;
  onWorkflowStepChange: Subscription;
  onSavedQueriesChange: Subscription;
  onReportTemplatesChange: Subscription;

  public conversations: ChatConversation[] = [];
  public activeConversationId: string | null = null;
  public renderedMessages: RenderedChatMessage[] = [];

  public mapLoading: boolean = false;
  public minimized: boolean = false;

  public editingConversationId: string | null = null;
  public editingConversationTitle = '';
  public activeSidebarTab: SidebarTab = 'chat';
  public choosingChatType = false;

  public templates: ReportTemplate[] = [];
  public projects: Project[] = [];

  // Current selected fact
  fact: Fact | null = null;
  visible: boolean = false;
  /** The fact dialog's "Save to" box: a picked project, or the text typed into it. */
  factProjectChoice: ProjectChoice | string | null = null;
  factProjectSuggestions: ProjectChoice[] = [];
  public facts: Fact[] = [];
  public factGroups: FactGroup[] = [];
  public expandedFactId: number | null = null;

  /** The report wizard's "+ New project…" option, and its name box. */
  readonly NEW_PROJECT = 'new' as const;
  creatingProject = false;
  creatingProjectRequest = false;
  newProjectName = '';
  /** True once the projects have loaded, so an empty list really means none. */
  projectsLoaded = false;
  /** The project Save as Fact defaults to; set when exploring for a report's facts. */
  factProjectId: number | null = null;
  /** The fact whose full query is open in the query dialog. */
  queryFact: Fact | null = null;
  queryVisible = false;
  queryCopied = false;
  /** Names of the expanded sections in the report's Facts by section step. */
  public expandedSections = new Set<string>();
  public highlightSavedQueryId: string | null = null;
  public highlightChatMessageId: string | null = null;
  public editingSavedQueryId: string | null = null;
  public editingSavedQueryTitle = '';
  public sidebarWidthPx = this.loadSidebarWidth();
  private resizingSidebar = false;
  private markdownCache = new Map<string, string>();
  private savedQueryIndexById = new Map<string, number>();

  /**
   * Matches the `#/internalLocationInspect/<uri>` links that parseText()
   * (chat.state.ts) generates for <location> tags in a chat response. Those
   * links are rendered through [innerHTML] (see
   * buildRenderedMessage/renderMarkdown), so Angular never compiles them and
   * a (click) binding on them would never fire -- the browser just treats
   * them as plain same-page anchor links. Instead we listen for the hash
   * they leave behind and react to that. The name is deliberately not
   * "explorer" or anything else that might collide with a real route added
   * to app.routes.ts down the line -- this hash is never meant to be routed,
   * only intercepted here and cleared.
   */
  private static readonly LOCATION_HASH_PATTERN = /^#\/internalLocationInspect\/(.+)$/;

  @ViewChild('chatContainer') chatContainer?: ElementRef<HTMLElement>;
  @ViewChild('messageInput') messageInput?: ElementRef<HTMLTextAreaElement>;

  constructor(
    private chatService: ChatService,
    private explorerService: ExplorerService,
    private errorService: ErrorService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    private explorerSessionState: ExplorerSessionStateService,
    private reportService: ReportService,
    private factService: FactService,
    private projectService: ProjectService
  ) {
    this.savedQueries$ = this.explorerSessionState.savedQueries$;
    this.activeSidebarTab = this.loadActiveSidebarTab();
    this.onSavedQueriesChange = this.savedQueries$.subscribe((savedQueries) => {
      this.savedQueryIndexById = new Map(savedQueries.map((query, index) => [query.id, index + 1]));
      this.refreshRenderedMessages();
    });

    this.loadConversations();
    this.reconcilePendingJobs();

    this.onWorkflowStepChange = combineLatest([this.workflowStep$, this.workflowData$]).subscribe(([step, data]) => {
      if (step === WorkflowStep.FullScreenChat && data != null) {
        const go = data as GeoObject;
        const conversation = this.activeConversation;

        if (conversation) {
          conversation.draft = go.properties.uri;
          this.saveConversations();
          this.sendMessage();
        }
      }

      this.minimized = step === WorkflowStep.MinimizeChat;
    });

    // Handle the case where a location link's hash is already on the URL
    // when this component is (re)constructed, e.g. a hard refresh.
    this.handleLocationHashNavigation();

    // Report templates arrive with the app's configuration.
    this.onReportTemplatesChange = this.store.select(getReportTemplates).subscribe((templates) => {
      this.templates = templates;
    });

    this.loadProjectsAndFacts();
  }

  ngOnDestroy(): void {
    this.onWorkflowStepChange.unsubscribe();
    this.onSavedQueriesChange.unsubscribe();
    this.onReportTemplatesChange.unsubscribe();
  }

  @HostListener('window:hashchange')
  onLocationHashChange(): void {
    this.handleLocationHashNavigation();
  }

  private handleLocationHashNavigation(): void {
    const match = AichatComponent.LOCATION_HASH_PATTERN.exec(window.location.hash);

    if (!match) {
      return;
    }

    // Clear the hash right away: it's not a real route (app.routes.ts has no
    // "/internalLocationInspect/:uri" path), it's only ever meant to carry
    // this one click, and leaving it in place would stop a second click on
    // the same link from ever firing another hashchange event.
    history.replaceState(null, '', window.location.pathname + window.location.search);

    this.inspectLocationUri(decodeURIComponent(match[1]));
  }

  startSidebarResize(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();

    this.resizingSidebar = true;
  }

  @HostListener('document:mousemove', ['$event'])
  onSidebarResize(event: MouseEvent): void {
    if (!this.resizingSidebar || !this.chatContainer) {
      return;
    }

    const rect = this.chatContainer.nativeElement.getBoundingClientRect();
    const nextWidth = event.clientX - rect.left;
    this.sidebarWidthPx = Math.min(this.maxSidebarWidthPx, Math.max(this.minSidebarWidthPx, nextWidth));
  }

  @HostListener('document:mouseup')
  stopSidebarResize(): void {
    if (!this.resizingSidebar) {
      return;
    }

    this.resizingSidebar = false;
    localStorage.setItem(this.SIDEBAR_WIDTH_STORAGE_KEY, this.sidebarWidthPx.toString());
  }

  private loadSidebarWidth(): number {
    const raw = Number(localStorage.getItem(this.SIDEBAR_WIDTH_STORAGE_KEY));

    if (!Number.isFinite(raw)) {
      return 300;
    }

    return Math.min(this.maxSidebarWidthPx, Math.max(this.minSidebarWidthPx, raw));
  }

  get activeConversation(): ChatConversation | undefined {
    return this.conversations.find((c) => c.id === this.activeConversationId);
  }

  get message(): string {
    return this.activeConversation?.draft ?? '';
  }

  set message(value: string) {
    const conversation = this.activeConversation;

    if (conversation) {
      conversation.draft = value;
      this.saveConversations();
    }
  }

  get loading(): boolean {
    return this.activeConversation?.loading ?? false;
  }

  private saveConversations(): void {
    try {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(this.conversations));

      if (this.activeConversationId) {
        localStorage.setItem(this.ACTIVE_CONVERSATION_STORAGE_KEY, this.activeConversationId);
      } else {
        localStorage.removeItem(this.ACTIVE_CONVERSATION_STORAGE_KEY);
      }
    } catch (error) {
      console.warn('Failed to save AI chat conversations to localStorage', error);
    }
  }

  private loadConversations(): void {
    const rawConversations = localStorage.getItem(this.STORAGE_KEY);
    const rawActiveConversationId = localStorage.getItem(this.ACTIVE_CONVERSATION_STORAGE_KEY);

    if (!rawConversations) {
      this.newDefaultConversation();
      return;
    }

    try {
      const conversations = JSON.parse(rawConversations) as ChatConversation[];

      if (!Array.isArray(conversations) || conversations.length === 0) {
        this.resetStoredConversationsToDefault();
        return;
      }

      this.conversations = conversations.map((c) => ({
        id: c.id ?? uuidv4(),
        title: c.title ?? 'New chat',
        sessionId: c.sessionId ?? uuidv4(),
        // Messages saved before `statements` existed don't have it
        messages: Array.isArray(c.messages)
          ? c.messages.map((message) => parseText({ ...message, statements: message.statements ?? [] }))
          : [],
        draft: c.draft ?? '',
        loading: false,
        createdAt: c.createdAt ?? Date.now(),
        type: c.type === 'report' ? 'report' : 'exploration',
        report: c.type === 'report' ? this.normalizeReportSelection(c.report) : undefined,
      }));

      const hasRealChatHistory = this.conversations.some(
        (c) => c.messages.some((m) => m.purpose === 'standard') || c.draft.trim().length > 0 || c.type === 'report'
      );

      if (!hasRealChatHistory) {
        this.resetStoredConversationsToDefault();
        return;
      }

      const activeStillExists = this.conversations.some((c) => c.id === rawActiveConversationId);

      this.activeConversationId = activeStillExists ? rawActiveConversationId : this.conversations[0].id;

      this.refreshRenderedMessages();
      this.saveConversations();
    } catch (error) {
      console.warn('Failed to load AI chat conversations from localStorage', error);
      this.resetStoredConversationsToDefault();
    }
  }

  private resetStoredConversationsToDefault(): void {
    localStorage.removeItem(this.STORAGE_KEY);
    localStorage.removeItem(this.ACTIVE_CONVERSATION_STORAGE_KEY);

    this.conversations = [];
    this.activeConversationId = null;

    this.newDefaultConversation();
  }

  private renderMarkdown(text: string | undefined | null): string {
    const source = text ?? '';
    const cached = this.markdownCache.get(source);

    if (cached != null) {
      return cached;
    }

    const rendered = marked.parse(source, {
      breaks: true,
      gfm: true,
    }) as string;

    this.markdownCache.set(source, rendered);

    return rendered;
  }

  private refreshRenderedMessages(): void {
    const conversation = this.activeConversation;

    this.renderedMessages = conversation
      ? [...conversation.messages].reverse().map((message) => this.buildRenderedMessage(conversation, message))
      : [];
  }

  // buildRenderedMessage() spreads each message into a brand-new object every time
  // refreshRenderedMessages() runs (which happens on nearly every chat interaction),
  // so without trackBy, *ngFor's default identity-based diffing sees every message as
  // removed-and-re-added and tears down/rebuilds the whole list. On the reversed
  // (column-reverse) message list that we rely on to stay pinned to the newest
  // message with no manual scrolling, that full rebuild is what makes the view
  // intermittently land back at the oldest message (the top) instead of staying on
  // the newest one (the bottom) after sending a message.
  trackByMessageId(_index: number, msg: RenderedChatMessage): string {
    return msg.id;
  }

  private buildRenderedMessage(conversation: ChatConversation, message: ChatMessage): RenderedChatMessage {
    // parsedText (see chat.model.ts) is only set by parseText() (chat.state.ts);
    // messages that skip it -- the user's own text, or a loading/error
    // placeholder -- fall back to the raw text.
    const parsedText = message.parsedText ?? message.text ?? '';

    return {
      ...message,
      renderedHtml: message.sender === 'system' ? this.renderMarkdown(parsedText) : parsedText,
      savedQueryIndex: null,
    };
  }

  /** Opens or closes the chooser for what kind of chat to create. */
  toggleChatTypeChooser(): void {
    this.choosingChatType = !this.choosingChatType;
  }

  newConversation(save = true, type: ChatType = 'exploration'): void {
    const id = uuidv4();

    const conversation: ChatConversation = {
      id,
      title: type === 'report' ? 'New report' : 'New chat',
      sessionId: uuidv4(),
      messages: [],
      draft: '',
      loading: false,
      createdAt: Date.now(),
      type,
      report: type === 'report' ? this.normalizeReportSelection(undefined) : undefined,
    };

    this.conversations.unshift(conversation);
    this.activeConversationId = id;
    this.activeSidebarTab = 'chat';
    this.choosingChatType = false;
    this.refreshRenderedMessages();
    this.saveActiveSidebarTab();

    if (save) {
      this.saveConversations();
    }
  }

  private newDefaultConversation(save = true): void {
    const id = uuidv4();

    const conversation: ChatConversation = {
      id,
      title: 'New chat',
      sessionId: uuidv4(),
      messages: this.createDefaultMessages(),
      draft: '',
      loading: false,
      createdAt: Date.now(),
      type: 'exploration',
    };

    this.conversations.unshift(conversation);
    this.activeConversationId = id;
    this.refreshRenderedMessages();

    if (save) {
      this.saveConversations();
    }
  }

  selectConversation(id: string): void {
    this.activeConversationId = id;
    this.expandedSections.clear();
    this.activeSidebarTab = 'chat';
    this.refreshRenderedMessages();
    this.saveActiveSidebarTab();
    this.saveConversations();
  }

  setActiveSidebarTab(tab: SidebarTab): void {
    this.activeSidebarTab = tab;
    this.saveActiveSidebarTab();
  }

  private loadActiveSidebarTab(): SidebarTab {
    const stored = localStorage.getItem(this.ACTIVE_SIDEBAR_TAB_STORAGE_KEY);

    return stored === 'queries' || stored === 'facts' ? stored : 'chat';
  }

  private saveActiveSidebarTab(): void {
    localStorage.setItem(this.ACTIVE_SIDEBAR_TAB_STORAGE_KEY, this.activeSidebarTab);
  }

  deleteConversation(event: Event, id: string): void {
    event.stopPropagation();

    const conversation = this.conversations.find((c) => c.id === id);

    if (!conversation) {
      return;
    }

    this.confirmationService.confirm({
      target: event.currentTarget as EventTarget,
      header: 'Delete conversation?',
      message: `Delete "${conversation.title}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-secondary p-button-text',
      accept: () => {
        this.confirmDeleteConversation(id);
      },
    });
  }

  private confirmDeleteConversation(id: string): void {
    this.conversations = this.conversations.filter((c) => c.id !== id);

    if (this.activeConversationId === id) {
      this.activeConversationId = this.conversations[0]?.id ?? null;
    }

    if (this.conversations.length === 0) {
      this.newConversation(false);
    }

    this.refreshRenderedMessages();
    this.saveConversations();
  }

  startEditingConversationTitle(event: Event, conversation: ChatConversation): void {
    event.stopPropagation();

    this.editingConversationId = conversation.id;
    this.editingConversationTitle = conversation.title;
  }

  saveConversationTitle(event?: Event): void {
    event?.stopPropagation();

    if (!this.editingConversationId) {
      return;
    }

    const conversation = this.conversations.find((c) => c.id === this.editingConversationId);

    if (!conversation) {
      this.cancelConversationTitleEdit(event);
      return;
    }

    const trimmed = this.editingConversationTitle.trim();

    conversation.title = trimmed.length > 0 ? trimmed : this.defaultTitle(conversation);

    this.editingConversationId = null;
    this.editingConversationTitle = '';

    this.saveConversations();
  }

  cancelConversationTitleEdit(event?: Event): void {
    event?.stopPropagation();

    this.editingConversationId = null;
    this.editingConversationTitle = '';
  }

  private createDefaultMessages(): ChatMessage[] {
    // return initialState.messages;

    return [];
  }

  private defaultTitle(conversation: ChatConversation): string {
    return conversation.type === 'report' ? 'New report' : 'New chat';
  }

  private updateConversationTitle(conversation: ChatConversation, text: string): void {
    if (conversation.title !== this.defaultTitle(conversation)) {
      return;
    }

    const trimmed = text.trim();

    conversation.title = trimmed.length > 28 ? trimmed.substring(0, 28) + '...' : trimmed;
  }

  /**
   * Sends the active conversation's draft, or `text` when given (which
   * leaves the draft alone).
   */
  sendMessage(text?: string): void {
    const conversation = this.activeConversation;
    const fromDraft = text == null;

    text = (text ?? conversation?.draft ?? '').trim();

    if (!conversation || conversation.loading || !text) {
      return;
    }

    if (this.minimized) {
      this.minimizeChat();
    }

    const message: ChatMessage = {
      id: uuidv4(),
      sender: 'user',
      text,
      mappable: false,
      loading: false,
      purpose: 'standard',
      statements: [],
    };

    if (fromDraft) {
      conversation.draft = '';
      this.resetMessageInputHeight();
    }

    conversation.messages.push(message);
    this.updateConversationTitle(conversation, text);

    const system: ChatMessage = {
      id: uuidv4(),
      sender: 'system',
      text: '',
      mappable: false,
      loading: true,
      purpose: 'standard',
      statements: [],
    };

    conversation.messages.push(system);
    conversation.loading = true;
    this.refreshRenderedMessages();

    this.saveConversations();

    const conversationId = conversation.id;
    const systemMessageId = system.id;
    const sessionId = conversation.sessionId;

    const request =
      conversation.type === 'report'
        ? this.reportService.sendMessage(
            sessionId,
            message,
            conversation.report ?? this.normalizeReportSelection(undefined),
            this.selectedTemplate,
            this.selectedProject,
            this.selectedFacts
          )
        : this.chatService.sendMessage(sessionId, message, (statusUrl) => {
            // Persist the job's status URL onto the placeholder message so that
            // if the page is closed or reloaded before the job settles,
            // reconcilePendingJobs() can resume polling it (or fail it cleanly)
            // on the next boot instead of leaving a permanent busy spinner.
            system.pendingStatusUrl = statusUrl;
            this.saveConversations();
          });

    request
      .then((response) => {
        const targetConversation = this.conversations.find((c) => c.id === conversationId);

        if (!targetConversation) {
          return;
        }

        const index = targetConversation.messages.findIndex((m) => m.id === systemMessageId);

        if (index !== -1) {
          targetConversation.messages[index] = parseText({
            ...system,
            text: response.text,
            mappable: response.mappable,
            ambiguous: response.ambiguous,
            loading: false,
            location: response.location,
            reasoning: response.reasoning,
            statements: response.statements ?? [],
            mapStatement: response.mapStatement ?? null,
            pendingStatusUrl: undefined,
          });

          this.refreshRenderedMessages();
          this.saveConversations();
        }
      })
      .catch((error) => {
        this.errorService.handleError(error);

        const targetConversation = this.conversations.find((c) => c.id === conversationId);

        if (!targetConversation) {
          return;
        }

        const index = targetConversation.messages.findIndex((m) => m.id === systemMessageId);

        if (index !== -1) {
          targetConversation.messages[index] = {
            ...system,
            text: 'An error occurred',
            loading: false,
            purpose: 'info',
            pendingStatusUrl: undefined,
          };

          this.refreshRenderedMessages();
          this.saveConversations();
        }
      })
      .finally(() => {
        const targetConversation = this.conversations.find((c) => c.id === conversationId);

        if (targetConversation) {
          targetConversation.loading = false;
          this.refreshRenderedMessages();
          this.saveConversations();
        }
      });
  }

  /**
   * Runs once on startup, after conversations are loaded from localStorage.
   *
   * A conversation's "AI is thinking" placeholder message (`loading: true`)
   * is persisted as part of the conversation, but the poll driving it lives
   * only in memory -- so if the page was closed or reloaded while a
   * chat/prompt job was in flight, that placeholder is loaded back with no
   * request actually running behind it, and would otherwise sit there
   * forever as a stuck busy spinner (loadConversations() resets the
   * conversation-level `loading` flag, but not an individual message's).
   *
   * For each conversation left with such a message: if it has a
   * `pendingStatusUrl`, resume polling that job -- this picks up right
   * where things left off whether the job already finished while the page
   * was away, is still running, or is no longer recognized by the server
   * (e.g. it restarted), in which case it fails fast. If there's no
   * `pendingStatusUrl` to check (an older message from before this was
   * tracked, or one that never made it past being created), there's no way
   * to ask the server about it, so it's failed closed instead of being
   * left stuck.
   */
  private reconcilePendingJobs(): void {
    let hadUnresolvableStuckMessage = false;

    for (const conversation of this.conversations) {
      const stuckMessage = conversation.messages.find((m) => m.loading);

      if (!stuckMessage) {
        continue;
      }

      if (stuckMessage.pendingStatusUrl) {
        this.resumePendingMessage(conversation, stuckMessage, stuckMessage.pendingStatusUrl);
      } else {
        conversation.loading = false;
        this.failStuckMessage(conversation, stuckMessage);
        hadUnresolvableStuckMessage = true;
      }
    }

    if (hadUnresolvableStuckMessage) {
      this.refreshRenderedMessages();
      this.saveConversations();
    }
  }

  private resumePendingMessage(conversation: ChatConversation, message: ChatMessage, statusUrl: string): void {
    conversation.loading = true;
    this.refreshRenderedMessages();

    this.chatService
      .resumePrompt(statusUrl)
      .then((response) => {
        const index = conversation.messages.findIndex((m) => m.id === message.id);

        if (index !== -1) {
          conversation.messages[index] = parseText({ ...response, id: message.id });
        }
      })
      .catch((error) => {
        // The job either explicitly failed, the server no longer
        // recognizes it (most likely it restarted while this message was
        // pending), or it didn't finish within the polling deadline -- in
        // every case, stop waiting on it.
        this.errorService.handleError(error);
        this.failStuckMessage(conversation, message);
      })
      .finally(() => {
        conversation.loading = false;
        this.refreshRenderedMessages();
        this.saveConversations();
      });
  }

  private failStuckMessage(conversation: ChatConversation, message: ChatMessage): void {
    const index = conversation.messages.findIndex((m) => m.id === message.id);

    if (index === -1) {
      return;
    }

    conversation.messages[index] = {
      ...message,
      text: 'This request was interrupted and could not be completed. Please try again.',
      loading: false,
      purpose: 'info',
      pendingStatusUrl: undefined,
    };
  }

  minimizeChat(): void {
    if (!this.minimized) {
      this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.MinimizeChat }));
      this.minimized = true;
    } else {
      this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.MapAndResults }));
      this.minimized = false;
    }
  }

  askNewQuestion(): void {
    this.newConversation();
  }

  formatQueryDate(query: CachedExplorerPages): string {
    return new Date(query.updatedAt).toLocaleString();
  }

  formatQueryMeta(query: CachedExplorerPages): string {
    const total = query.pages.reduce((sum, page) => sum + (page.count ?? page.locations?.length ?? 0), 0);

    return `${total} result${total === 1 ? '' : 's'}`;
  }

  getMessageQueryId(message: ChatMessage): string | null {
    return this.getQueryIdForConversation(message);
  }

  /**
   * True when the agent flagged the message as mappable. Mappable messages
   * carry a map statement, which mapIt runs directly; older messages without
   * one fall back to the map-it agent.
   */
  isMappable(message: ChatMessage): boolean {
    return message.mappable;
  }

  /** Cache id of the pages a message maps to. */
  private getQueryIdForConversation(message: ChatMessage): string | null {
    if (message.mapStatement) {
      return this.explorerSessionState.getOrCreatePageRequestId(message.mapStatement, null, 0, 100);
    }

    return null;
  }

  /**
   * Fetches the pages to draw on the map for a message. Messages with a map
   * statement run it directly. Older messages without one fall back to having
   * the map-it agent generate SPARQL from the conversation history.
   */
  private fetchMapPages(message: ChatMessage): Promise<LocationPage[]> | null {
    const statement = message.mapStatement;

    if (statement) {
      return this.chatService.getPage(statement, null, 0, 100).then((page) => {
        const pages = [page];

        // Re-cache as a mapped chat query so it is labelled as one if saved
        this.explorerSessionState.cachePages(
          pages,
          'chat-mapit',
          this.explorerSessionState.getOrCreatePageRequestId(statement, null, 0, 100)
        );

        return pages;
      });
    }

    return Promise.resolve([]);
  }

  scrollToChatMessage(messageId: string): void {
    requestAnimationFrame(() => {
      const el = document.getElementById(this.chatMessageElementId(messageId));
      el?.scrollIntoView({ block: 'center', behavior: 'smooth' });

      if (el) {
        this.highlightChatMessageId = messageId;
        window.setTimeout(() => {
          if (this.highlightChatMessageId === messageId) {
            this.highlightChatMessageId = null;
          }
        }, 1400);
      }
    });
  }

  savedQueryElementId(queryId: string): string {
    return `saved-query-${queryId}`;
  }

  chatMessageElementId(messageId: string): string {
    return `chat-message-${messageId}`;
  }

  private findMappableMessageForQueryId(
    queryId: string
  ): { conversation: ChatConversation; message: ChatMessage } | null {
    // for (const conversation of this.conversations) {
    //   const mappableMessages = conversation.messages.filter((message) => this.isMappable(message));

    //   for (const message of mappableMessages) {
    //     const messageQueryId = this.getQueryIdForConversation(conversation, message);

    //     if (messageQueryId === queryId) {
    //       return { conversation, message };
    //     }
    //   }
    // }

    return null;
  }

  mapIt(message: ChatMessage): void {
    const request = this.fetchMapPages(message);
    const pageCacheId = this.getQueryIdForConversation(message);

    if (!request || !pageCacheId) {
      return;
    }

    this.mapLoading = true;

    request
      .then((pages) => {
        let total = pages.map((p) => p.count).reduce((a, b) => a + b, 0);

        if (total === 0) {
          this.messageService.add({
            key: 'explorer',
            severity: 'info',
            summary: 'Info',
            detail: 'The query did not return any results!',
            sticky: true,
          });
          return;
        }

        const step = message.ambiguous ? WorkflowStep.DisambiguateObject : WorkflowStep.MapAndResults;

        this.store.dispatch(
          ExplorerActions.showPagesOnMap({
            pages,
            zoomMap: true,
            step,
            data: { pageCacheId },
          })
        );
      })
      .catch((error) => this.errorService.handleError(error))
      .finally(() => {
        this.mapLoading = false;
      });
  }

  setWorkflowStepDisambiguate(message: ChatMessage): void {
    this.mapLoading = true;

    this.explorerService
      .fullTextLookup(message.location!)
      .then((page) => {
        const pageCacheId = this.explorerSessionState.getOrCreateFullTextLookupId(message.location!);
        this.explorerSessionState.cachePages([page], 'full-text-disambiguation', pageCacheId, message.location);

        this.store.dispatch(
          ExplorerActions.setPages({
            pages: [page],
            zoomMap: true,
          })
        );

        this.store.dispatch(
          ExplorerActions.setWorkflowStep({ step: WorkflowStep.DisambiguateObject, data: { pageCacheId } })
        );
      })
      .catch((error) => this.errorService.handleError(error))
      .finally(() => {
        this.mapLoading = false;
      });
  }

  clear(): void {
    const conversation = this.activeConversation;

    this.store.dispatch(
      ExplorerActions.setPages({
        pages: [
          {
            locations: [],
            statement: '',
            type: null,
            limit: 100,
            offset: 0,
            count: 0,
          },
        ],
        zoomMap: false,
      })
    );

    if (conversation) {
      conversation.messages = [];
      conversation.sessionId = uuidv4();
      conversation.draft = '';
      conversation.title = this.defaultTitle(conversation);
      conversation.loading = false;
    }

    this.refreshRenderedMessages();
    this.saveConversations();
  }

  onMessageKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey) {
      return;
    }

    event.preventDefault();

    if (!this.loading) {
      this.sendMessage();
    }
  }

  resizeMessageInput(textarea: HTMLTextAreaElement): void {
    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 180)}px`;
    textarea.style.overflowY = textarea.scrollHeight > 180 ? 'auto' : 'hidden';
  }

  private resetMessageInputHeight(): void {
    requestAnimationFrame(() => {
      if (this.messageInput?.nativeElement) {
        this.messageInput.nativeElement.style.height = 'auto';
      }
    });
  }

  private inspectLocationUri(uri: string): void {
    this.mapLoading = true;

    this.explorerService
      .getAttributes(uri, true)
      .then((geoObject) => {
        this.store.dispatch(
          ExplorerActions.setWorkflowStep({
            step: WorkflowStep.InspectObject,
            data: geoObject,
            zoomMap: true,
          })
        );
      })
      .catch((error) => this.errorService.handleError(error))
      .finally(() => {
        this.mapLoading = false;
      });
  }

  /*
   * Report generation chats
   */

  get isReportChat(): boolean {
    return this.activeConversation?.type === 'report';
  }

  get activeReport(): ReportSelection | undefined {
    return this.activeConversation?.report;
  }

  get selectedTemplate(): ReportTemplate | undefined {
    const templateId = this.activeReport?.templateId;

    return this.templates.find((t) => t.id === templateId);
  }

  get selectedProject(): Project | undefined {
    const projectId = this.activeReport?.projectId;

    return projectId == null ? undefined : this.projects.find((p) => p.id === projectId);
  }

  /** Facts that can be picked for the active report: those in its project. */
  get availableReportFacts(): Fact[] {
    const projectId = this.selectedProject?.id;

    return projectId == null ? [] : this.facts.filter((f) => f.projectId === projectId);
  }

  get selectedFacts(): Fact[] {
    const ids = this.activeReport?.factIds ?? [];

    return this.facts.filter((f) => f.id != null && ids.includes(f.id));
  }

  get messagePlaceholder(): string {
    if (!this.isReportChat) {
      return 'Type a message...';
    }

    return this.isReportSetupIncomplete(this.activeConversation)
      ? 'Describe your report...'
      : 'Ask the report assistant...';
  }

  private normalizeReportSelection(selection: Partial<ReportSelection> | undefined): ReportSelection {
    return {
      templateId: selection?.templateId ?? null,
      projectId: selection?.projectId ?? null,
      factIds: Array.isArray(selection?.factIds) ? [...selection!.factIds] : [],
      sectionFacts: { ...(selection?.sectionFacts ?? {}) },
      sectionNotes: { ...(selection?.sectionNotes ?? {}) },
    };
  }

  private isReportSetupIncomplete(conversation: ChatConversation | undefined): boolean {
    if (conversation?.type !== 'report') {
      return false;
    }

    const report = conversation.report;

    return !report?.templateId || this.selectedProject == null || conversation.messages.length === 0;
  }

  selectTemplate(templateId: string): void {
    const report = this.activeReport;

    if (!report) {
      return;
    }

    report.templateId = report.templateId === templateId ? null : templateId;

    // Keep assignments to sections the new template shares by name. Facts in
    // the other sections stay in the report, unassigned.
    const sectionNames = new Set(this.selectedTemplate?.sections.map((s) => s.name) ?? []);
    report.sectionFacts = Object.fromEntries(
      Object.entries(report.sectionFacts).filter(([name]) => sectionNames.has(name))
    );
    // Notes are the user's own writing, so keep them even for sections the
    // new template doesn't have; switching back brings them back.
    this.expandedSections.clear();

    this.saveConversations();
  }

  /** The report wizard's project step shows a name box instead of the dropdown. */
  get showNewProjectForm(): boolean {
    return this.creatingProject || (this.projectsLoaded && this.projects.length === 0);
  }

  onReportProjectSelect(value: number | typeof this.NEW_PROJECT | null): void {
    if (value === this.NEW_PROJECT) {
      this.creatingProject = true;
      this.newProjectName = '';
    } else {
      this.selectProject(value);
    }
  }

  cancelNewProject(): void {
    this.creatingProject = false;
    this.newProjectName = '';
  }

  /**
   * Creates a project from the report wizard and selects it. A name that
   * matches an existing project (ignoring case) selects that one instead.
   */
  createReportProject(): void {
    const name = this.newProjectName.trim();

    if (!name || this.creatingProjectRequest) {
      return;
    }

    const existing = this.projects.find((p) => p.id != null && p.name.toLowerCase() === name.toLowerCase());

    if (existing) {
      this.selectProject(existing.id!);
      this.cancelNewProject();
      return;
    }

    this.creatingProjectRequest = true;
    this.projectService
      .create(name)
      .then((project) => {
        this.projects = [project, ...this.projects];
        this.selectProject(project.id!);
        this.cancelNewProject();
      })
      .catch((error) => this.errorService.handleError(error))
      .finally(() => (this.creatingProjectRequest = false));
  }

  /**
   * Starts a data exploration chat to find facts for the report's project.
   * Save as Fact then defaults to that project.
   */
  exploreForFacts(): void {
    this.factProjectId = this.selectedProject?.id ?? null;
    this.newConversation(true, 'exploration');
  }

  selectProject(projectId: number | null): void {
    const report = this.activeReport;

    if (!report || report.projectId === projectId) {
      return;
    }

    report.projectId = projectId;
    // Facts belong to a project, so a different project clears the picks.
    report.factIds = [];
    report.sectionFacts = {};
    this.saveConversations();
  }

  /** Removes a fact from the report and from every section it was in. */
  removeFactFromReport(fact: Fact): void {
    const report = this.activeReport;

    if (!report || fact.id == null) {
      return;
    }

    this.removeFactFromSelection(report, fact.id);
    this.saveConversations();
  }

  private removeFactFromSelection(report: ReportSelection, factId: number): void {
    report.factIds = report.factIds.filter((id) => id !== factId);

    for (const name of Object.keys(report.sectionFacts)) {
      report.sectionFacts[name] = report.sectionFacts[name].filter((id) => id !== factId);
    }
  }

  factsInSection(section: ReportSection): Fact[] {
    const ids = this.activeReport?.sectionFacts[section.name] ?? [];

    return ids.map((id) => this.facts.find((f) => f.id === id)).filter((f): f is Fact => f != null);
  }

  /** Project facts that can still be added to a section. */
  factsAssignableToSection(section: ReportSection): Fact[] {
    const ids = this.activeReport?.sectionFacts[section.name] ?? [];

    return this.availableReportFacts.filter((f) => f.id != null && !ids.includes(f.id));
  }

  /** Facts in the report that aren't assigned to any section of the template. */
  get unassignedFacts(): Fact[] {
    const report = this.activeReport;
    const template = this.selectedTemplate;

    if (!report) {
      return [];
    }

    const assigned = new Set((template?.sections ?? []).flatMap((s) => report.sectionFacts[s.name] ?? []));

    return this.selectedFacts.filter((f) => f.id != null && !assigned.has(f.id));
  }

  get requiredSectionCoverage(): { covered: number; total: number } {
    const required = this.selectedTemplate?.sections.filter((s) => s.required) ?? [];

    return {
      covered: required.filter((s) => this.sectionHasContent(s)).length,
      total: required.length,
    };
  }

  /** A section has content once it has a fact or some notes. */
  sectionHasContent(section: ReportSection): boolean {
    const report = this.activeReport;

    return (
      (report?.sectionFacts[section.name]?.length ?? 0) > 0 ||
      (report?.sectionNotes[section.name]?.trim().length ?? 0) > 0
    );
  }

  isSectionExpanded(section: ReportSection): boolean {
    return this.expandedSections.has(section.name);
  }

  toggleSection(section: ReportSection): void {
    if (this.expandedSections.has(section.name)) {
      this.expandedSections.delete(section.name);
    } else {
      this.expandedSections.add(section.name);
    }
  }

  sectionNotes(section: ReportSection): string {
    return this.activeReport?.sectionNotes[section.name] ?? '';
  }

  setSectionNotes(section: ReportSection, notes: string): void {
    const report = this.activeReport;

    if (!report) {
      return;
    }

    if (notes.length > 0) {
      report.sectionNotes[section.name] = notes;
    } else {
      delete report.sectionNotes[section.name];
    }

    this.saveConversations();
  }

  /** Generate needs a template and a project; the rest is up to the user. */
  get canGenerateReport(): boolean {
    const report = this.activeReport;

    return !!report?.templateId && this.selectedProject != null && !this.loading;
  }

  /** Asks the report agent to write the report from the current setup. */
  generateReport(): void {
    if (!this.canGenerateReport) {
      return;
    }

    this.sendMessage('Generate the report from the selected template, facts and section notes.');
  }

  sectionExampleHtml(section: ReportSection): string {
    return this.renderMarkdown(section.example);
  }

  assignFactToSection(section: ReportSection, factId: number): void {
    const report = this.activeReport;

    if (!report || !Number.isFinite(factId)) {
      return;
    }

    const ids = report.sectionFacts[section.name] ?? [];

    if (!ids.includes(factId)) {
      report.sectionFacts[section.name] = [...ids, factId];
    }

    if (!report.factIds.includes(factId)) {
      report.factIds = [...report.factIds, factId];
    }

    this.saveConversations();
  }

  /** Handles the "Add fact" dropdown on a section, then resets it. */
  onAssignFactSelect(event: Event, section: ReportSection): void {
    const select = event.target as HTMLSelectElement;

    this.assignFactToSection(section, Number(select.value));
    select.value = '';
  }

  /**
   * Takes a fact out of one section. A fact that ends up in no section leaves
   * the report, so the report's facts are always the ones placed somewhere
   * (apart from those left over when a template change drops their section).
   */
  unassignFactFromSection(section: ReportSection, fact: Fact): void {
    const report = this.activeReport;
    const factId = fact.id;

    if (!report || factId == null) {
      return;
    }

    report.sectionFacts[section.name] = (report.sectionFacts[section.name] ?? []).filter((id) => id !== factId);

    const stillAssigned = Object.values(report.sectionFacts).some((ids) => ids.includes(factId));

    if (!stillAssigned) {
      report.factIds = report.factIds.filter((id) => id !== factId);
    }

    this.saveConversations();
  }

  toggleFactDetails(fact: Fact): void {
    this.expandedFactId = this.expandedFactId === fact.id ? null : (fact.id ?? null);
  }

  /** A fact's answer text, rendered from markdown like the chat message it came from. */
  factHtml(fact: Fact): string {
    return this.renderMarkdown(fact.text);
  }

  /** Opens a fact's full query in a dialog; the card shows only its first lines. */
  showFactQuery(event: Event, fact: Fact): void {
    event.stopPropagation();

    this.queryFact = fact;
    this.queryCopied = false;
    this.queryVisible = true;
  }

  copyFactQuery(): void {
    if (this.queryFact != null) {
      navigator.clipboard
        .writeText(this.factStatements(this.queryFact))
        .then(() => (this.queryCopied = true))
        .catch((error) => this.errorService.handleError(error));
    }
  }

  factStatements(fact: Fact): string {
    return (fact.statements ?? []).join('\n\n');
  }

  deleteFact(event: Event, fact: Fact): void {
    event.stopPropagation();

    this.confirmationService.confirm({
      target: event.currentTarget as EventTarget,
      header: 'Delete fact?',
      message: `Delete "${fact.name}"? Reports will no longer be able to use it.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-secondary p-button-text',
      accept: () => {
        const id = fact.id;

        if (id == null) {
          return;
        }

        this.factService
          .delete(id)
          .then(() => {
            this.facts = this.facts.filter((f) => f.id !== id);
            this.factGroups = this.groupFactsByProject(this.facts);

            for (const conversation of this.conversations) {
              if (conversation.report) {
                this.removeFactFromSelection(conversation.report, id);
              }
            }

            this.saveConversations();
          })
          .catch((error) => this.errorService.handleError(error));
      },
    });
  }

  private groupFactsByProject(facts: Fact[]): FactGroup[] {
    const groups = new Map<number, FactGroup>();

    for (const fact of facts) {
      const projectId = fact.projectId ?? -1;
      let group = groups.get(projectId);

      if (!group) {
        group = { projectId, projectName: fact.projectName ?? '', facts: [] };
        groups.set(projectId, group);
      }

      group.facts.push(fact);
    }

    return [...groups.values()];
  }

  createFact(event: Event, message: ChatMessage): void {
    event.stopPropagation();

    this.fact = {
      name: '',
      description: '',
      projectId: null,
      projectName: '',
      statements: message.statements,
      mapStatement: message.mapStatement ?? null,
      text: message.text,
    };

    const preferred =
      this.projects.find((p) => p.id != null && p.id === this.factProjectId) ?? this.projects.find((p) => p.id != null);
    this.factProjectChoice = preferred ? { id: preferred.id!, name: preferred.name } : null;
    this.visible = true;
  }

  /** The answer the fact dialog's fact was saved from, as HTML. */
  get factTextHtml(): string {
    return this.renderMarkdown(this.fact?.text);
  }

  /**
   * Fills the "Save to" suggestions: the projects whose names contain the
   * query, plus an option to create a project with that name if none has it.
   */
  searchFactProjects(query: string): void {
    const name = query.trim();
    const lower = name.toLowerCase();
    const matches: ProjectChoice[] = this.projects
      .filter((p) => p.id != null && p.name.toLowerCase().includes(lower))
      .map((p) => ({ id: p.id!, name: p.name }));
    const exists = this.projects.some((p) => p.name.toLowerCase() === lower);

    this.factProjectSuggestions = name && !exists ? [...matches, { id: null, name }] : matches;
  }

  /**
   * The project the fact dialog will save to. Typed text that names an
   * existing project means that project; other text means a new project.
   */
  get factProject(): ProjectChoice | null {
    const choice = this.factProjectChoice;

    if (choice == null || typeof choice !== 'string') {
      return choice;
    }

    const name = choice.trim();
    const existing = this.projects.find((p) => p.id != null && p.name.toLowerCase() === name.toLowerCase());

    return existing ? { id: existing.id!, name: existing.name } : name ? { id: null, name } : null;
  }

  /** True when saving the fact dialog will also create a project. */
  get factCreatesProject(): boolean {
    return this.fact?.id == null && this.factProject != null && this.factProject.id == null;
  }

  /**
   * Opens the fact dialog to rename a fact or change its description. The
   * dialog edits a copy, and hides the project; the text and statements the
   * fact was saved from are sent back unchanged.
   */
  editFact(event: Event, fact: Fact): void {
    event.stopPropagation();

    this.fact = { ...fact, statements: [...(fact.statements ?? [])] };
    this.visible = true;
  }

  viewFact(event: Event, fact: Fact): void {
    event.stopPropagation();

    const message: ChatMessage = {
      id: fact.id!.toString(),
      sender: 'system',
      text: fact.text,
      mappable: true,
      ambiguous: false,
      parsedText: fact.text,
      purpose: 'standard',
      statements: fact.statements,
      mapStatement: fact.mapStatement,
    };

    this.mapIt(message);
  }

  saveFact(): void {
    if (this.fact != null && this.fact.id == null) {
      const project = this.factProject;

      if (project == null) {
        return;
      }

      this.fact.projectId = project.id;
      this.fact.projectName = project.id == null ? project.name : '';
    }

    if (this.fact != null) {
      this.factService
        .save(this.fact)
        .then(() => {
          this.fact = null;
          this.visible = false;

          this.loadProjectsAndFacts();
        })
        .catch((error) => this.errorService.handleError(error));
    }
  }

  /**
   * Loads the projects and every project's facts. Both the report wizard and
   * the Facts tab use these; the Save as Fact dialog uses the projects.
   */
  loadProjectsAndFacts(): Promise<void> {
    return this.projectService
      .getAll()
      .then((projects) => {
        this.projects = projects;
        this.projectsLoaded = true;

        return Promise.all(
          projects
            .filter((p) => p.id != null)
            .map((p) =>
              this.factService
                .getByProject(p.id!)
                .then((facts) =>
                  facts.map((f) => ({ ...f, projectId: f.projectId ?? p.id, projectName: f.projectName ?? p.name }))
                )
            )
        );
      })
      .then((factsByProject) => {
        this.facts = factsByProject.flat();
        this.factGroups = this.groupFactsByProject(this.facts);
      })
      .catch((error) => this.errorService.handleError(error));
  }
}
