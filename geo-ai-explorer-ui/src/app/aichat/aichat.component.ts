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
  faTrash
} from '@fortawesome/free-solid-svg-icons';

import { ChatService } from '../service/chat-service.service';
import { initialState, parseText } from '../state/chat.state';
import { ChatMessage, MessageSection } from '../models/chat.model';
import { ErrorService } from '../service/error-service.service';
import {
  ExplorerActions,
  getWorkflowData,
  getWorkflowStep,
  WorkflowStep
} from '../state/explorer.state';
import { ExplorerService } from '../service/explorer.service';
import { GeoObject } from '../models/geoobject.model';
import { CachedExplorerPages, ExplorerSessionStateService } from '../service/explorer-session-state.service';

import { marked } from 'marked';

import { ConfirmationService, MessageService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';

interface ChatConversation {
  id: string;
  title: string;
  sessionId: string;
  messages: ChatMessage[];
  draft: string;
  loading: boolean;
  createdAt: number;
}

interface RenderedMessageSection extends MessageSection {
  renderedText: string;
  renderedHtml?: string;
}

interface RenderedChatMessage extends ChatMessage {
  hasLocationSections: boolean;
  renderedSections: RenderedMessageSection[];
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
    ConfirmDialogModule
  ],
  providers: [ConfirmationService],
  templateUrl: './aichat.component.html',
  styleUrl: './aichat.component.scss'
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

  private store = inject(Store);

  workflowStep$: Observable<WorkflowStep> = this.store.select(getWorkflowStep);
  workflowData$: Observable<any> = this.store.select(getWorkflowData);
  savedQueries$: Observable<CachedExplorerPages[]>;
  onWorkflowStepChange: Subscription;
  onSavedQueriesChange: Subscription;

  public conversations: ChatConversation[] = [];
  public activeConversationId: string | null = null;
  public renderedMessages: RenderedChatMessage[] = [];

  public mapLoading: boolean = false;
  public minimized: boolean = false;

  public editingConversationId: string | null = null;
  public editingConversationTitle = '';
  public activeSidebarTab: 'chat' | 'queries' = 'chat';
  public highlightSavedQueryId: string | null = null;
  public highlightChatMessageId: string | null = null;
  public editingSavedQueryId: string | null = null;
  public editingSavedQueryTitle = '';
  public sidebarWidthPx = this.loadSidebarWidth();
  private resizingSidebar = false;
  private markdownCache = new Map<string, string>();
  private savedQueryIndexById = new Map<string, number>();

  @ViewChild('chatContainer') chatContainer?: ElementRef<HTMLElement>;
  @ViewChild('messageInput') messageInput?: ElementRef<HTMLTextAreaElement>;

  constructor(
    private chatService: ChatService,
    private explorerService: ExplorerService,
    private errorService: ErrorService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    private explorerSessionState: ExplorerSessionStateService
  ) {
    this.savedQueries$ = this.explorerSessionState.savedQueries$;
    this.activeSidebarTab = this.loadActiveSidebarTab();
    this.onSavedQueriesChange = this.savedQueries$.subscribe(savedQueries => {
      this.savedQueryIndexById = new Map(savedQueries.map((query, index) => [query.id, index + 1]));
      this.refreshRenderedMessages();
    });

    this.loadConversations();

    this.onWorkflowStepChange = combineLatest([
      this.workflowStep$,
      this.workflowData$
    ]).subscribe(([step, data]) => {
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
  }

  ngOnDestroy(): void {
    this.onWorkflowStepChange.unsubscribe();
    this.onSavedQueriesChange.unsubscribe();
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
    return this.conversations.find(c => c.id === this.activeConversationId);
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

      this.conversations = conversations.map(c => ({
        id: c.id ?? uuidv4(),
        title: c.title ?? 'New chat',
        sessionId: c.sessionId ?? uuidv4(),
        messages: Array.isArray(c.messages) ? c.messages.map(message => parseText(message)) : [],
        draft: c.draft ?? '',
        loading: false,
        createdAt: c.createdAt ?? Date.now()
      }));

      const hasRealChatHistory = this.conversations.some(c =>
        c.messages.some(m => m.purpose === 'standard') || c.draft.trim().length > 0
      );

      if (!hasRealChatHistory) {
        this.resetStoredConversationsToDefault();
        return;
      }

      const activeStillExists = this.conversations.some(c => c.id === rawActiveConversationId);

      this.activeConversationId = activeStillExists
        ? rawActiveConversationId
        : this.conversations[0].id;

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
      gfm: true
    }) as string;

    this.markdownCache.set(source, rendered);

    return rendered;
  }

  private renderLocationText(text: string | undefined | null): string {
    return (text ?? '').replace(/(^|\n)[ \t]+(?=-\s)/g, '$1');
  }

  private refreshRenderedMessages(): void {
    const conversation = this.activeConversation;

    this.renderedMessages = conversation
      ? [...conversation.messages].reverse().map(message => this.buildRenderedMessage(conversation, message))
      : [];
  }

  private buildRenderedMessage(conversation: ChatConversation, message: ChatMessage): RenderedChatMessage {
    const sections = message.sections ?? [];
    const hasLocationSections = sections.some(section => section.type === 1);

    return {
      ...message,
      hasLocationSections,
      renderedSections: sections.map(section => ({
        ...section,
        renderedText: hasLocationSections && section.type === 0
          ? this.renderLocationText(section.text)
          : section.text,
        renderedHtml: !hasLocationSections && message.sender === 'system' && section.type === 0
          ? this.renderMarkdown(section.text)
          : undefined
      })),
      savedQueryIndex: message.mappable
        ? this.getSavedQueryIndexForConversation(conversation, message)
        : null
    };
  }

  newConversation(save = true): void {
    const id = uuidv4();

    const conversation: ChatConversation = {
      id,
      title: 'New chat',
      sessionId: uuidv4(),
      messages: [],
      draft: '',
      loading: false,
      createdAt: Date.now()
    };

    this.conversations.unshift(conversation);
    this.activeConversationId = id;
    this.refreshRenderedMessages();

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
      createdAt: Date.now()
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
    this.activeSidebarTab = 'chat';
    this.refreshRenderedMessages();
    this.saveActiveSidebarTab();
    this.saveConversations();
  }

  setActiveSidebarTab(tab: 'chat' | 'queries'): void {
    this.activeSidebarTab = tab;
    this.saveActiveSidebarTab();
  }

  private loadActiveSidebarTab(): 'chat' | 'queries' {
    return localStorage.getItem(this.ACTIVE_SIDEBAR_TAB_STORAGE_KEY) === 'queries'
      ? 'queries'
      : 'chat';
  }

  private saveActiveSidebarTab(): void {
    localStorage.setItem(this.ACTIVE_SIDEBAR_TAB_STORAGE_KEY, this.activeSidebarTab);
  }

  deleteConversation(event: Event, id: string): void {
    event.stopPropagation();

    const conversation = this.conversations.find(c => c.id === id);

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
      }
    });
  }

  private confirmDeleteConversation(id: string): void {
    this.conversations = this.conversations.filter(c => c.id !== id);

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

    const conversation = this.conversations.find(c => c.id === this.editingConversationId);

    if (!conversation) {
      this.cancelConversationTitleEdit(event);
      return;
    }

    const trimmed = this.editingConversationTitle.trim();

    conversation.title = trimmed.length > 0
      ? trimmed
      : 'New chat';

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

  private updateConversationTitle(conversation: ChatConversation, text: string): void {
    if (conversation.title !== 'New chat') {
      return;
    }

    const trimmed = text.trim();

    conversation.title = trimmed.length > 28
      ? trimmed.substring(0, 28) + '...'
      : trimmed;
  }

  private normalizeResponseSections(response: any): any[] {
    if (Array.isArray(response.sections)) {
      return response.sections;
    }

    return [{ type: 0, text: response.text ?? '' }];
  }

  sendMessage(): void {
    const conversation = this.activeConversation;

    if (!conversation || conversation.loading || !conversation.draft.trim()) {
      return;
    }

    if (this.minimized) {
      this.minimizeChat();
    }

    const text = conversation.draft.trim();

    const message: ChatMessage = {
      id: uuidv4(),
      sender: 'user',
      text,
      mappable: false,
      sections: [{ type: 0, text }],
      loading: false,
      purpose: 'standard'
    };

    conversation.draft = '';
    conversation.messages.push(message);
    this.resetMessageInputHeight();
    this.updateConversationTitle(conversation, text);

    const system: ChatMessage = {
      id: uuidv4(),
      sender: 'system',
      text: '',
      mappable: false,
      sections: [],
      loading: true,
      purpose: 'standard'
    };

    conversation.messages.push(system);
    conversation.loading = true;
    this.refreshRenderedMessages();

    this.saveConversations();

    const conversationId = conversation.id;
    const systemMessageId = system.id;
    const sessionId = conversation.sessionId;

    this.chatService.sendMessage(sessionId, message)
      .then(response => {
        const targetConversation = this.conversations.find(c => c.id === conversationId);

        if (!targetConversation) {
          return;
        }

        const index = targetConversation.messages.findIndex(m => m.id === systemMessageId);

        if (index !== -1) {

          targetConversation.messages[index] = parseText({
            ...system,
            text: response.text,
            sections: this.normalizeResponseSections(response),
            mappable: response.mappable,
            ambiguous: response.ambiguous,
            loading: false,
            location: response.location
          });

          this.refreshRenderedMessages();
          this.saveConversations();
        }
      })
      .catch(error => {
        this.errorService.handleError(error);

        const targetConversation = this.conversations.find(c => c.id === conversationId);

        if (!targetConversation) {
          return;
        }

        const index = targetConversation.messages.findIndex(m => m.id === systemMessageId);

        if (index !== -1) {
          targetConversation.messages[index] = {
            ...system,
            text: 'An error occurred',
            sections: [{ type: 0, text: 'An error occurred' }],
            loading: false,
            purpose: 'info'
          };

          this.refreshRenderedMessages();
          this.saveConversations();
        }
      })
      .finally(() => {
        const targetConversation = this.conversations.find(c => c.id === conversationId);

        if (targetConversation) {
          targetConversation.loading = false;
          this.refreshRenderedMessages();
          this.saveConversations();
        }
      });
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

  viewCachedQuery(query: CachedExplorerPages): void {
    this.store.dispatch(ExplorerActions.showPagesOnMap({
      pages: query.pages,
      zoomMap: true,
      step: WorkflowStep.MapAndResults,
      data: { pageCacheId: query.id }
    }));
  }

  openSavedQueryChat(query: CachedExplorerPages): void {
    if (this.editingSavedQueryId === query.id) {
      return;
    }

    const target = this.findMappableMessageForQueryId(query.id);

    if (!target) {
      this.viewCachedQuery(query);
      return;
    }

    this.activeConversationId = target.conversation.id;
    this.refreshRenderedMessages();
    this.saveConversations();
    this.scrollToChatMessage(target.message.id);
  }

  startEditingSavedQueryTitle(event: Event, query: CachedExplorerPages): void {
    event.stopPropagation();

    if (this.editingSavedQueryId === query.id) {
      this.cancelSavedQueryTitleEdit();
      return;
    }

    this.editingSavedQueryId = query.id;
    this.editingSavedQueryTitle = query.title;
  }

  saveSavedQueryTitle(event: Event, query: CachedExplorerPages): void {
    event.stopPropagation();

    const title = this.editingSavedQueryTitle.trim();

    if (!title) {
      return;
    }

    this.explorerSessionState.renameSavedPages(query.id, title);
    this.editingSavedQueryId = null;
    this.editingSavedQueryTitle = '';
  }

  cancelSavedQueryTitleEdit(event?: Event): void {
    event?.stopPropagation();

    this.editingSavedQueryId = null;
    this.editingSavedQueryTitle = '';
  }

  deleteCachedQuery(event: Event, query: CachedExplorerPages): void {
    event.stopPropagation();

    this.confirmationService.confirm({
      target: event.currentTarget as EventTarget,
      header: 'Delete query?',
      message: `Delete "${query.title}" from saved queries?`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-secondary p-button-text',
      accept: () => {
        this.explorerSessionState.deleteCachedPages(query.id);
      }
    });
  }

  formatQueryDate(query: CachedExplorerPages): string {
    return new Date(query.updatedAt).toLocaleString();
  }

  formatQueryMeta(query: CachedExplorerPages): string {
    const total = query.pages.reduce((sum, page) => sum + (page.count ?? page.locations?.length ?? 0), 0);

    return `${total} result${total === 1 ? '' : 's'}`;
  }

  getMessageQueryId(message: ChatMessage): string | null {
    const history = this.getMappableHistory(message);

    return history
      ? this.explorerSessionState.getOrCreateLocationsRequestId(history, 0, 100)
      : null;
  }

  private getSavedQueryIndexForConversation(conversation: ChatConversation, message: ChatMessage): number | null {
    const history = this.getMappableHistoryForConversation(conversation, message);

    if (!history) {
      return null;
    }

    const queryId = this.explorerSessionState.getOrCreateLocationsRequestId(history, 0, 100);

    return this.savedQueryIndexById.get(queryId) ?? null;
  }

  saveOrScrollQuery(event: Event, message: ChatMessage): void {
    event.stopPropagation();

    const queryId = this.getMessageQueryId(message);

    if (!queryId) {
      return;
    }

    const savedIndex = this.explorerSessionState.getSavedQueryIndex(queryId);

    if (savedIndex != null) {
      this.scrollToSavedQuery(queryId);
      return;
    }

    const cached = this.explorerSessionState.getCachedPages(queryId);

    if (cached) {
      this.explorerSessionState.saveCachedPages(queryId);
      this.scrollToSavedQuery(queryId);
      return;
    }

    const history = this.getMappableHistory(message);

    if (!history) {
      return;
    }

    this.mapLoading = true;

    this.chatService.getLocations(history, 0, 100)
      .then(pages => {
        if (!this.explorerSessionState.hasResults(pages)) {
          this.messageService.add({
            key: 'explorer',
            severity: 'info',
            summary: 'Info',
            detail: 'The query did not return any results!',
            sticky: true
          });
          return;
        }

        this.explorerSessionState.saveCachedPages(queryId);
        this.scrollToSavedQuery(queryId);
      })
      .catch(error => this.errorService.handleError(error))
      .finally(() => {
        this.mapLoading = false;
      });
  }

  scrollToSavedQuery(queryId: string): void {
    this.activeSidebarTab = 'queries';
    this.saveActiveSidebarTab();

    requestAnimationFrame(() => {
      const el = document.getElementById(this.savedQueryElementId(queryId));
      el?.scrollIntoView({ block: 'center', behavior: 'smooth' });

      if (el) {
        this.highlightSavedQueryId = queryId;
        window.setTimeout(() => {
          if (this.highlightSavedQueryId === queryId) {
            this.highlightSavedQueryId = null;
          }
        }, 1400);
      }
    });
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

  private findMappableMessageForQueryId(queryId: string): { conversation: ChatConversation; message: ChatMessage } | null {
    for (const conversation of this.conversations) {
      const mappableMessages = conversation.messages.filter(message => message.mappable);

      for (const message of mappableMessages) {
        const history = this.getMappableHistoryForConversation(conversation, message);

        if (!history) {
          continue;
        }

        const messageQueryId = this.explorerSessionState.getOrCreateLocationsRequestId(history, 0, 100);

        if (messageQueryId === queryId) {
          return { conversation, message };
        }
      }
    }

    return null;
  }

  private getMappableHistory(message: ChatMessage): ChatMessage[] | null {
    const conversation = this.activeConversation;

    if (!conversation) {
      return null;
    }

    return this.getMappableHistoryForConversation(conversation, message);
  }

  private getMappableHistoryForConversation(conversation: ChatConversation, message: ChatMessage): ChatMessage[] | null {

    const index = conversation.messages.findIndex(m => m.id === message.id);

    if (index === -1) {
      return null;
    }

    return conversation.messages
      .slice(0, index + 1)
      .filter(m => m.purpose === 'standard');
  }

  mapIt(message: ChatMessage): void {
    const conversation = this.activeConversation;

    if (!conversation) {
      return;
    }

    const history = this.getMappableHistory(message);

    if (!history) {
      return;
    }

    this.mapLoading = true;

    this.chatService.getLocations(history, 0, 100)
      .then(pages => {
        let total = pages.map(p => p.count).reduce((a,b) => a+b, 0);

        if (total === 0) {
          this.messageService.add({
            key: 'explorer',
            severity: 'info',
            summary: 'Info',
            detail: 'The query did not return any results!',
            sticky: true
          });
          return;
        }

        const step = message.ambiguous
          ? WorkflowStep.DisambiguateObject
          : WorkflowStep.MapAndResults;

        const pageCacheId = this.explorerSessionState.getOrCreateLocationsRequestId(history, 0, 100);

        this.store.dispatch(ExplorerActions.showPagesOnMap({
          pages,
          zoomMap: true,
          step,
          data: { pageCacheId }
        }));
      })
      .catch(error => this.errorService.handleError(error))
      .finally(() => {
        this.mapLoading = false;
      });
  }

  setWorkflowStepDisambiguate(message: ChatMessage): void {
    this.mapLoading = true;

    this.explorerService.fullTextLookup(message.location!)
      .then(page => {
        const pageCacheId = this.explorerSessionState.getOrCreateFullTextLookupId(message.location!);
        this.explorerSessionState.cachePages([page], 'full-text-disambiguation', pageCacheId, message.location);

        this.store.dispatch(ExplorerActions.setPages({
          pages: [page],
          zoomMap: true
        }));

        this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.DisambiguateObject, data: { pageCacheId } }));
      })
      .catch(error => this.errorService.handleError(error))
      .finally(() => {
        this.mapLoading = false;
      });
  }

  clear(): void {
    const conversation = this.activeConversation;

    this.store.dispatch(ExplorerActions.setPages({
      pages: [{
        locations: [],
        statement: '',
        type: null,
        limit: 100,
        offset: 0,
        count: 0
      }],
      zoomMap: false
    }));

    if (conversation) {
      conversation.messages = [];
      conversation.sessionId = uuidv4();
      conversation.draft = '';
      conversation.title = 'New chat';
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
  }

  private resetMessageInputHeight(): void {
    requestAnimationFrame(() => {
      if (this.messageInput?.nativeElement) {
        this.messageInput.nativeElement.style.height = 'auto';
      }
    });
  }

  select(event: Event, uri: string): void {
    event.stopPropagation();
    this.mapLoading = true;

    this.explorerService.getAttributes(uri, true)
      .then(geoObject => {
        this.store.dispatch(ExplorerActions.setWorkflowStep({
          step: WorkflowStep.InspectObject,
          data: geoObject,
          zoomMap: true
        }));
      })
      .catch(error => this.errorService.handleError(error))
      .finally(() => {
        this.mapLoading = false;
      });
  }
}
