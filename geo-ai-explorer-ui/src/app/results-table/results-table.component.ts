import { Component, EventEmitter, HostBinding, inject, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { LetDirective } from '@ngrx/component';
import { CommonModule } from '@angular/common';

import { TableModule } from 'primeng/table';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

import { GeoObject } from '../models/geoobject.model';
import { Observable, Subscription, take } from 'rxjs';
import { Store } from '@ngrx/store';
import { ExplorerActions, getPages, getWorkflowStep, highlightedObject, WorkflowStep } from '../state/explorer.state';
import { ChatService } from '../service/chat-service.service';
import { LocationPage, TypeSummary } from '../models/chat.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import {
    faThumbtack,
    faChevronDown,
    faChevronUp,
    faFileExport,
    faSort,
    faSortUp,
    faSortDown
} from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { MultiSelectModule } from 'primeng/multiselect';
import { TooltipModule } from 'primeng/tooltip';
import { TabsModule } from 'primeng/tabs';

interface PageDisplayColumn {
    field: string;
    header: string;
}

interface PageDisplayItem {
    page: LocationPage;
    index: number;
    columns: PageDisplayColumn[];
    loading?: boolean;
}

interface PageDisplayOption {
    label: string;
    value: string;
    count: number;
}

type SortDirection = 'asc' | 'desc';

interface PageSort {
    field: string;
    direction: SortDirection;
}

@Component({
    selector: 'results-table',
    imports: [TableModule, PaginatorModule, ProgressSpinnerModule, LetDirective, CommonModule, FontAwesomeModule, FormsModule, MultiSelectModule, TooltipModule, TabsModule],
    templateUrl: './results-table.component.html',
    styleUrl: './results-table.component.scss',
})
export class ResultsTableComponent implements OnInit, OnDestroy {
    public WorkflowStep = WorkflowStep;
    private store = inject(Store);

    @Output()
    resultsHeightChange = new EventEmitter<number>();

    @Output()
    resultsCollapsedChange = new EventEmitter<boolean>();

    pinIcon = faThumbtack;
    public collapseIcon = faChevronDown;
    public expandIcon = faChevronUp;
    public sortIcon = faSort;
    public sortUpIcon = faSortUp;
    public sortDownIcon = faSortDown;
    public exportIcon = faFileExport;

    pages$: Observable<LocationPage[]> = this.store.select(getPages);

    highlightedObject$: Observable<GeoObject | null> = this.store.select(highlightedObject);

    onHighlightedObjectChange: Subscription;

    public highlightedObjectUri: string | null | undefined;

    workflowStep$: Observable<WorkflowStep> = this.store.select(getWorkflowStep);
    
    onWorkflowStepChange: Subscription;
    onPagesChange: Subscription;

    public workflowStep: WorkflowStep = WorkflowStep.MapAndResults;

    private latestPages: LocationPage[] = [];
    pageDisplayOptions: PageDisplayOption[] = [];
    displayedPageItems: PageDisplayItem[] = [];
    
    maxPinnedPages = 3;

    activePageDisplayKey: string = '';
    pinnedPageDisplayKeys: string[] = [];
    private pageSortsByDisplayKey = new Map<string, PageSort>();
    private loadingPageDisplayKeys = new Set<string>();

    private _collapsed = false;
    private _resizable = true;

    private readonly expandedDefaultHeightPx = 360;
    private readonly collapsedHeightPx = 0;
    private readonly minExpandedHeightPx = 220;
    private readonly viewportTopPaddingPx = 96;

    private panelHeightPx = this.expandedDefaultHeightPx;

    private resizing = false;
    private resizeStartY = 0;
    private resizeStartHeightPx = 0;

    @HostBinding('style.height.px')
    get hostHeightPx(): number | null {
        if (!this.resizable) {
            return null;
        }

        return this.collapsed
            ? this.collapsedHeightPx
            : this.panelHeightPx;
    }

    @HostBinding('class.results-panel-fill')
    get hostFillClass(): boolean {
        return !this.resizable;
    }

    @HostBinding('class.results-panel-collapsed')
    get hostCollapsedClass(): boolean {
        return this.collapsed;
    }

    @HostBinding('class.results-panel-resizing')
    get hostResizingClass(): boolean {
        return this.resizing;
    }

    @Input()
    set collapsed(value: boolean) {
        const wasCollapsed = this._collapsed;
        this._collapsed = value;

        if (wasCollapsed && !value && this.workflowStep !== WorkflowStep.DisambiguateObject) {
            this.ensureActiveTypeLoaded();
        }
    }

    get collapsed(): boolean {
        return this._collapsed;
    }

    @Input()
    set resizable(value: boolean) {
        this._resizable = value;

        // Handles the case where resizing is disabled during an active drag.
        if (!value) {
            this.stopResize();
        }
    }

    get resizable(): boolean {
        return this._resizable;
    }

    constructor(
        private chatService: ChatService
    ) {
        this.onHighlightedObjectChange = this.highlightedObject$.subscribe(object => {
            this.highlightObject(object == null ? undefined : object.properties.uri);
        });

        this.onWorkflowStepChange = this.workflowStep$.subscribe(step => {
            this.workflowStep = step;
            this.rebuildPageDisplayState();
        });

        this.onPagesChange = this.pages$.subscribe(pages => {
            this.latestPages = pages ?? [];
            this.rebuildPageDisplayState();

            if (!this.collapsed && this.workflowStep !== WorkflowStep.DisambiguateObject) {
                this.ensureActiveTypeLoaded();
            }
        });
    }

    ngOnInit(): void {

    }

    ngOnDestroy(): void {
        this.onHighlightedObjectChange?.unsubscribe();
        this.onWorkflowStepChange?.unsubscribe();
        this.onPagesChange?.unsubscribe();

        this.stopResize();
    }

    navigateToChat() {
        this.store.dispatch(ExplorerActions.setPages({ pages: [{ 
            locations: [],
            statement: "",
            type: null,
            limit: 100,
            offset: 0,
            count: 0
        }], zoomMap: false }));
        this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.FullScreenChat }));
    }

    calculateScrollHeight(): string {
        if (this.workflowStep == WorkflowStep.DisambiguateObject) {
            return "calc(100vh - 75px)"
        } else if (this.workflowStep === WorkflowStep.MinimizeChat) {
            // return "calc(100vh - 50px)";
            return "calc(100vh - 108px)";
        } else {
            return "calc(40vh - 3rem)";
        }
    }

    onClick(obj: GeoObject): void {
        this.store.dispatch(ExplorerActions.appendWorkflowStep({
            step: WorkflowStep.InspectObject,
            data: obj,
            zoomMap: true
        }));
    }

    onRowHover(obj: GeoObject): void {
        this.store.dispatch(ExplorerActions.highlightGeoObject({ object: obj }));
    }

    onMouseLeaveTable(): void {
        this.highlightedObjectUri = null;
    }

    highlightObject(uri?: string): void {
        this.highlightedObjectUri = uri;
    }

    private rebuildPageDisplayState(): void {
        if (this.workflowStep === WorkflowStep.DisambiguateObject) {
            this.pageDisplayOptions = [];
            this.displayedPageItems = this.getLoadedPageItems(this.latestPages)
                .filter(item => item.page.count > 0);
            return;
        }

        const pageItems = this.getLoadedTypePageItems(this.latestPages)
            .filter(item => item.page.count > 0);
        const availableTypes = this.getAvailableTypes(this.latestPages);

        this.ensureValidActiveAndPinnedPages(availableTypes);

        this.pageDisplayOptions = availableTypes.map(type => ({
            label: this.getTypeLabel(type.type),
            value: type.type,
            count: type.count
        }));

        const displayKeys = [
            this.activePageDisplayKey,
            ...this.pinnedPageDisplayKeys
        ].filter(key => !!key);

        const uniqueDisplayKeys = Array.from(new Set(displayKeys));

        const displayedItems = pageItems.filter(item =>
            uniqueDisplayKeys.includes(this.getPageDisplayKey(item.page, item.index))
        );

        const displayedKeys = new Set(displayedItems.map(item => this.getPageDisplayKey(item.page, item.index)));
        const loadingItems = uniqueDisplayKeys
            .filter(key => !displayedKeys.has(key) && this.loadingPageDisplayKeys.has(key))
            .map(key => this.createLoadingPageItem(key, availableTypes));

        this.displayedPageItems = [...displayedItems, ...loadingItems];
    }

    onPageChange(state: PaginatorState, pageIndex: number): void {
        this.pages$.pipe(take(1)).subscribe(pages => {
            const currentPage = pages[pageIndex];

            if (!currentPage?.type) {
                return;
            }

            const sort = this.getPageSort(currentPage, pageIndex);
            const key = this.getPageDisplayKey(currentPage, pageIndex);

            this.beginPageLoading(key);

            this.chatService
                .getPage(currentPage.statement, currentPage.type, state.first ?? 0, state.rows ?? currentPage.limit, [], sort?.field, sort?.direction)
                .then(nextPage => {
                    return this.updatePagesWithTypePage(pages, nextPage);
                })
                .catch(error => console.error('Failed to load results page.', error))
                .finally(() => this.endPageLoading(key));
        });
    }

    onColumnSort(item: PageDisplayItem, column: PageDisplayColumn): void {
        const key = this.getPageDisplayKey(item.page, item.index);
        const currentSort = this.pageSortsByDisplayKey.get(key);
        const nextDirection: SortDirection = currentSort?.field === column.field && currentSort.direction === 'asc'
            ? 'desc'
            : 'asc';

        this.pageSortsByDisplayKey.set(key, {
            field: column.field,
            direction: nextDirection
        });
        this.beginPageLoading(key);

        this.pages$.pipe(take(1)).subscribe(pages => {
            const currentPage = pages[item.index];

            if (!currentPage?.type) {
                this.endPageLoading(key);
                return;
            }

            this.chatService
                .getPage(currentPage.statement, currentPage.type, 0, currentPage.limit, [], column.field, nextDirection)
                .then(nextPage => {
                    return this.updatePagesWithTypePage(pages, nextPage);
                })
                .catch(error => console.error('Failed to sort results page.', error))
                .finally(() => this.endPageLoading(key));
        });
    }

    exportTable(item: PageDisplayItem, event: MouseEvent): void {
        event.stopPropagation();

        const sort = this.getPageSort(item.page, item.index);

        this.chatService
            .exportPage(item.page.statement, item.page.type, [], sort?.field, sort?.direction)
            .then(blob => this.downloadCsv(blob, this.exportFilename(item.page)))
            .catch(error => console.error('Failed to export results table.', error));
    }

    private downloadCsv(blob: Blob, filename: string): void {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');

        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    }

    private exportFilename(page: LocationPage): string {
        const label = this.shouldShowTypeColumn(page)
            ? 'results'
            : this.getPageTypeLabel(page);

        const safeLabel = label
            .replace(/^.*[#/]/, '')
            .replace(/[^A-Za-z0-9._-]+/g, '_')
            .replace(/_+/g, '_')
            .replace(/^_|_$/g, '');

        return `${safeLabel || 'results'}.csv`;
    }

    getSortIcon(item: PageDisplayItem, column: PageDisplayColumn) {
        const sort = this.getPageSort(item.page, item.index);

        if (sort?.field !== column.field) {
            return this.sortIcon;
        }

        return sort.direction === 'asc'
            ? this.sortUpIcon
            : this.sortDownIcon;
    }

    getColumnAriaSort(item: PageDisplayItem, column: PageDisplayColumn): 'ascending' | 'descending' | 'none' {
        const sort = this.getPageSort(item.page, item.index);

        if (sort?.field !== column.field) {
            return 'none';
        }

        return sort.direction === 'asc'
            ? 'ascending'
            : 'descending';
    }

    isColumnSorted(item: PageDisplayItem, column: PageDisplayColumn): boolean {
        return this.getPageSort(item.page, item.index)?.field === column.field;
    }

    isPageLoading(item: PageDisplayItem): boolean {
        return !!item.loading || this.loadingPageDisplayKeys.has(this.getPageDisplayKey(item.page, item.index));
    }

    selectActivePage(key: string | number): void {
        this.activePageDisplayKey = String(key);
        this.rebuildPageDisplayState();
        this.ensureTypeLoaded(this.activePageDisplayKey);
    }

    isPagePinned(key: string): boolean {
        return this.pinnedPageDisplayKeys.includes(key);
    }

    togglePinnedPage(key: string): void {
        if (this.isPagePinned(key)) {
            this.pinnedPageDisplayKeys = this.pinnedPageDisplayKeys.filter(k => k !== key);
            this.rebuildPageDisplayState();
            return;
        }

        if (this.pinnedPageDisplayKeys.length >= this.maxPinnedPages) {
            return;
        }

        this.pinnedPageDisplayKeys = [...this.pinnedPageDisplayKeys, key];
        this.rebuildPageDisplayState();
        this.ensureTypeLoaded(key);
    }

    trackByPageItem = (index: number, item: { page: LocationPage; index: number }): string | number => {
        return this.getPageDisplayKey(item.page, item.index);
    }

    public getPageDisplayKey(page: LocationPage, index: number): string {
        return page.type?.trim()
            ? page.type
            : `page-${index}`;
    }

    getPageItems(pages: LocationPage[]): PageDisplayItem[] {
        return pages.map((page, index) => ({
            page,
            index,
            columns: this.getColumnsForPage(page)
        }));
    }

    private getLoadedPageItems(pages: LocationPage[]): PageDisplayItem[] {
        return pages.map((page, index) => ({
            page,
            index,
            columns: this.getColumnsForPage(page)
        }));
    }

    private getLoadedTypePageItems(pages: LocationPage[]): PageDisplayItem[] {
        return this.getLoadedPageItems(pages)
            .filter(item => item.page.type != null && item.page.type.trim() !== '');
    }

    private getCombinedPage(pages: LocationPage[]): LocationPage | undefined {
        return pages.find(page => page.type == null || page.type.trim() === '');
    }

    private getAvailableTypes(pages: LocationPage[]): TypeSummary[] {
        const combinedPage = this.getCombinedPage(pages);
        const availableTypes = combinedPage?.availableTypes ?? [];

        if (availableTypes.length > 0) {
            return [...availableTypes].sort((a, b) => b.count - a.count);
        }

        return this.getLoadedTypePageItems(pages)
            .map(item => ({ type: item.page.type!, count: item.page.count }))
            .sort((a, b) => b.count - a.count);
    }

    private ensureActiveTypeLoaded(): void {
        if (!this.activePageDisplayKey) {
            return;
        }

        this.ensureTypeLoaded(this.activePageDisplayKey);
    }

    private ensureTypeLoaded(type: string): void {
        if (!type) {
            return;
        }

        this.pages$.pipe(take(1)).subscribe(pages => {
            if (pages.some(page => page.type === type)) {
                return;
            }

            const combinedPage = this.getCombinedPage(pages);

            if (!combinedPage) {
                return;
            }

            this.beginPageLoading(type);

            this.chatService
                .getPage(combinedPage.statement, type, 0, combinedPage.limit, [], this.pageSortsByDisplayKey.get(type)?.field, this.pageSortsByDisplayKey.get(type)?.direction)
                .then(typePage => this.updatePagesWithTypePage(pages, typePage))
                .catch(error => console.error('Failed to load results table.', error))
                .finally(() => this.endPageLoading(type));
        });
    }

    private getPageSort(page: LocationPage, index: number): PageSort | undefined {
        return this.pageSortsByDisplayKey.get(this.getPageDisplayKey(page, index));
    }

    private updatePagesWithTypePage(pages: LocationPage[], typePage: LocationPage): Promise<void> {
        const combinedPage = this.getCombinedPage(pages);

        if (!combinedPage) {
            return Promise.resolve();
        }

        const loadedTypePages = pages
            .filter(page => page.type != null && page.type.trim() !== '' && page.type !== typePage.type)
            .concat(typePage);
        const excludedTypes = loadedTypePages
            .map(page => page.type)
            .filter((type): type is string => type != null && type.trim() !== '');

        return this.chatService
            .getPage(combinedPage.statement, null, combinedPage.offset, combinedPage.limit, excludedTypes)
            .then(nextCombinedPage => {
                nextCombinedPage.availableTypes = combinedPage.availableTypes ?? nextCombinedPage.availableTypes;

                this.store.dispatch(ExplorerActions.setPages({
                    pages: [nextCombinedPage, ...loadedTypePages],
                    zoomMap: true
                }));
            })
            .catch(error => console.error('Failed to refresh combined results page.', error));
    }

    private beginPageLoading(key: string): void {
        this.loadingPageDisplayKeys.add(key);
        this.rebuildPageDisplayState();
    }

    private endPageLoading(key: string): void {
        this.loadingPageDisplayKeys.delete(key);
        this.rebuildPageDisplayState();
    }

    private createLoadingPageItem(key: string, availableTypes: TypeSummary[]): PageDisplayItem {
        const combinedPage = this.getCombinedPage(this.latestPages);
        const typeSummary = availableTypes.find(type => type.type === key);

        return {
            page: {
                statement: combinedPage?.statement ?? '',
                type: key,
                locations: [],
                limit: combinedPage?.limit ?? 100,
                offset: 0,
                count: typeSummary?.count ?? 0,
                availableTypes: combinedPage?.availableTypes
            },
            index: -1,
            columns: [{ field: 'label', header: 'Label' }],
            loading: true
        };
    }

    ensureValidActiveAndPinnedPages(availableTypes: TypeSummary[]): void {
        const validKeys = availableTypes.map(type => type.type);

        if (!this.activePageDisplayKey || !validKeys.includes(this.activePageDisplayKey)) {
            this.activePageDisplayKey = validKeys[0] ?? '';
        }

        this.pinnedPageDisplayKeys = this.pinnedPageDisplayKeys.filter(key =>
            validKeys.includes(key)
        );
    }

    hasResults(pages: LocationPage[] | null | undefined): boolean {
        return !!pages?.some(page => page.count > 0);
    }

    shouldShowTypeColumn(page: LocationPage): boolean {
        return page.type == null || page.type.trim() === '';
    }

    getPageTypeLabel(page: LocationPage): string {
        return this.getTypeLabel(page.type);
    }

    getObjectTypeLabel(object: GeoObject): string {
        return this.getTypeLabel(object.properties.type);
    }

    private getTypeLabel(type: string | null | undefined): string {
        if (type == null || type.trim() === '') {
            return 'Unknown';
        }

        return type.includes('rdfs#')
            ? type.split('rdfs#')[1]
            : type;
    }

    private getColumnsForPage(page: LocationPage): PageDisplayColumn[] {
        const excludedFields = new Set([
            'uri',
            'wkt',
            'geometry',
            'geom',
            'type',
            'the_geom',
            'bbox'
        ]);

        const preferredOrder = [
            'label',
            'code',
            'name',
            'description'
        ];

        const fieldSet = new Set<string>();

        for (const object of page.locations ?? []) {
            const properties = object?.properties ?? {};

            for (const key of Object.keys(properties)) {
                const value = properties[key];

                if (excludedFields.has(key)) {
                    continue;
                }

                if (value == null || value === '') {
                    continue;
                }

                if (typeof value === 'object') {
                    continue;
                }

                fieldSet.add(key);
            }
        }

        const fields = Array.from(fieldSet);

        fields.sort((a, b) => {
            const aPreferredIndex = preferredOrder.indexOf(a);
            const bPreferredIndex = preferredOrder.indexOf(b);

            const aPreferred = aPreferredIndex !== -1;
            const bPreferred = bPreferredIndex !== -1;

            if (aPreferred && bPreferred) {
                return aPreferredIndex - bPreferredIndex;
            }

            if (aPreferred) {
                return -1;
            }

            if (bPreferred) {
                return 1;
            }

            return a.localeCompare(b);
        });

        const columns = fields.map(field => ({
            field,
            header: this.getColumnHeader(field)
        }));

        if (this.shouldShowTypeColumn(page)) {
            columns.unshift({
                field: 'type',
                header: 'Type'
            });
        }

        if (columns.length === 0) {
            columns.push({
                field: 'label',
                header: 'Label'
            });
        }

        return columns;
    }

    private getColumnHeader(field: string): string {
        return field
            .replace(/^.*[#/]/, '')
            .replace(/([a-z])([A-Z])/g, '$1 $2')
            .replace(/[_-]+/g, ' ')
            .replace(/\s+/g, ' ')
            .trim()
            .replace(/\b\w/g, char => char.toUpperCase());
    }

    getColumnValue(object: GeoObject, column: PageDisplayColumn): string {
        const value = object?.properties?.[column.field];

        if (value == null) {
            return '';
        }

        if (typeof value === 'string') {
            return this.getDisplayValueForString(value);
        }

        if (typeof value === 'number' || typeof value === 'boolean') {
            return String(value);
        }

        return JSON.stringify(value);
    }

    private getDisplayValueForString(value: string): string {
        if (value.includes('rdfs#')) {
            return value.split('rdfs#')[1];
        }

        if (value.includes('#')) {
            return value.split('#').pop() ?? value;
        }

        return value;
    }

    toggleCollapsed(): void {
        this.collapsed = !this.collapsed;

        this.resultsCollapsedChange.emit(this.collapsed);
        this.resultsHeightChange.emit(
            this.collapsed ? this.collapsedHeightPx : this.panelHeightPx
        );
    }

    startResize(event: MouseEvent): void {
        if (!this.resizable || this.collapsed) {
            return;
        }

        event.preventDefault();

        this.resizing = true;
        this.resizeStartY = event.clientY;
        this.resizeStartHeightPx = this.panelHeightPx;

        document.addEventListener('mousemove', this.onResizeMove);
        document.addEventListener('mouseup', this.stopResize);
    }

    private onResizeMove = (event: MouseEvent): void => {
        if (!this.resizing) {
            return;
        }

        const deltaY = this.resizeStartY - event.clientY;
        const nextHeight = this.resizeStartHeightPx + deltaY;

        this.panelHeightPx = this.clampPanelHeight(nextHeight);
        this.resultsHeightChange.emit(this.panelHeightPx);
    };

    private stopResize = (): void => {
        this.resizing = false;

        document.removeEventListener('mousemove', this.onResizeMove);
        document.removeEventListener('mouseup', this.stopResize);
    };

    private clampPanelHeight(heightPx: number): number {
        const maxHeight = Math.max(
            this.minExpandedHeightPx,
            window.innerHeight - this.viewportTopPaddingPx
        );

        return Math.min(
            Math.max(heightPx, this.minExpandedHeightPx),
            maxHeight
        );
    }
}