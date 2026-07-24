import { Component, AfterViewInit, ElementRef, TemplateRef, ViewChild, inject, OnInit, OnDestroy } from '@angular/core';
import { Map, NavigationControl, AttributionControl, LngLatBounds, LngLat, GeoJSONSource, LngLatBoundsLike, MapGeoJSONFeature, Source, Popup } from "maplibre-gl";
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import JSON5 from 'json5'
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PanelModule } from 'primeng/panel';
import { ToastModule } from 'primeng/toast';
import { CheckboxModule } from 'primeng/checkbox';
import { combineLatest, combineLatestAll, distinctUntilChanged, Observable, Subscription, switchMap, take, withLatestFrom } from 'rxjs';
import { Store } from '@ngrx/store';
import { ActivatedRoute, Router } from '@angular/router';

import { GeoObject } from '../models/geoobject.model';
import { StyleConfig } from '../models/style.model';

import { AttributePanelComponent } from '../attribute-panel/attribute-panel.component';
import { AichatComponent } from '../aichat/aichat.component';
import { ResultsTableComponent } from '../results-table/results-table.component';
import { ConfigurationService } from '../service/configuration-service.service';
import { GraphExplorerComponent } from '../graph-explorer/graph-explorer.component';
import { defaultQueries, SELECTED_COLOR, HOVER_COLOR } from './defaultQueries';
import { AllGeoJSON, bbox, bboxPolygon, union } from '@turf/turf';
import { ExplorerService } from '../service/explorer.service';
import { ErrorService } from '../service/error-service.service';
import { ExplorerActions, getNeighbors, getObjects, getStyles, getVectorLayers, getZoomMap, highlightedObject, getWorkflowStep, WorkflowStep, getPages, getWorkflowState, getPreviousWorkflowStep, WorkflowState, getSelectedObject } from '../state/explorer.state';
import { TabsModule } from 'primeng/tabs';
import { debounce } from 'lodash';
import { VectorLayer } from '../models/vector-layer.model';
import { environment } from '../../environments/environment';
import { faAnglesLeft, faAnglesRight, faArrowLeft, faArrowRight, faDownLeftAndUpRightToCenter, faFloppyDisk, faShareNodes, faUpRightAndDownLeftFromCenter } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ButtonModule } from 'primeng/button';
import { LocationPage } from '../models/chat.model';
import { TooltipModule } from 'primeng/tooltip';
import { CachedExplorerPages, ExplorerSessionStateService } from '../service/explorer-session-state.service';
import { DialogModule } from 'primeng/dialog';

export interface TypeLegend { [key: string]: { label: string, color: string, visible: boolean, included: boolean } }

@Component({
    selector: 'app-explorer',
    imports: [
        CommonModule,
        FormsModule,
        GraphExplorerComponent,
        AichatComponent,
        AttributePanelComponent,
        DragDropModule,
        ResultsTableComponent,
        ProgressSpinnerModule,
        PanelModule,
        ToastModule,
        TabsModule,
        CheckboxModule,
        FontAwesomeModule,
        ButtonModule,
        TooltipModule,
        DialogModule
    ],
    templateUrl: './explorer.component.html',
    styleUrl: './explorer.component.scss'
})
export class ExplorerComponent implements OnInit, OnDestroy {
    @ViewChild('resultsTable') resultsTable?: ResultsTableComponent;
    @ViewChild('mapElement') set mapElementRef(ref: ElementRef<HTMLElement> | undefined) {
        const previousElement = this.mapElement?.nativeElement;
        this.mapElement = ref;

        if (ref && previousElement && previousElement !== ref.nativeElement && this.map) {
            this.destroyMap();
        }

        if (ref && this.shouldShowMapForWorkflow(this.workflowStep)) {
            this.syncMapToInspectorLayout();
        }
    }

    public WorkflowStep = WorkflowStep;
    public backIcon = faArrowLeft;
    public forwardIcon = faArrowRight;
    public minimizeIcon = faDownLeftAndUpRightToCenter;
    public upsizeIcon = faUpRightAndDownLeftFromCenter;
    public saveQueryIcon = faFloppyDisk;
    public hideAttributesIcon = faAnglesLeft;
    public showAttributesIcon = faAnglesRight;
    public relationshipIcon = faShareNodes;

    public static GEO = "http://www.opengis.net/ont/geosparql#";

    public static GEO_FEATURE = ExplorerComponent.GEO + "Feature";

    public static GEO_WKT_LITERAL = ExplorerComponent.GEO + "wktLiteral";

    private store = inject(Store);

    zoomMap$: Observable<boolean> = this.store.select(getZoomMap);

    geoObjects$: Observable<GeoObject[]> = this.store.select(getObjects);

    geoObjects: GeoObject[] = [];

    renderedObjects: string[] = [];

    onMapObjectsChange: Subscription;

    neighbors$: Observable<GeoObject[]> = this.store.select(getNeighbors);

    neighbors: GeoObject[] = [];

    styles$: Observable<StyleConfig> = this.store.select(getStyles);

    highlightedObject$: Observable<GeoObject | null> = this.store.select(highlightedObject);

    onHighlightedObjectChange: Subscription;

    selectedObject$: Observable<GeoObject | null> = this.store.select(getSelectedObject);

    onSelectedObjectChange: Subscription;

    workflowState$: Observable<WorkflowState> = this.store.select(getWorkflowState);

    onWorkflowStepChange: Subscription;

    previousWorkflowStep$: Observable<WorkflowStep | undefined> = this.store.select(getPreviousWorkflowStep);

    pages$: Observable<LocationPage[]> = this.store.select(getPages);

    onPageChange: Subscription;

    onSavedQueriesChange: Subscription;

    onUrlStateChange?: Subscription;

    resolvedStyles: StyleConfig = {};

    public inspectorTab = 0;

    map?: Map;

    // file?: string;

    importError?: string;

    public defaultQueries = defaultQueries;

    public loading: boolean = false;

    public typeLegend: TypeLegend = {};
    public legendCollapsed = true;

    public selectedObject?: GeoObject;

    public highlightedObject: GeoObject | null | undefined;

    private mapInitialized = false;
    private mapInitScheduled = false;
    private renderScheduled = false;
    private zoomMapToExtentOnNextRender = false;
    private zoomMapToExtentRetries = 0;
    private zoomRestoredLayersAfterBack = false;
    private mapResizeScheduled = false;
    private mapSyncScheduled = false;
    private mapElement?: ElementRef<HTMLElement>;
    private featurePopup?: Popup;
    private currentGeoObjects: GeoObject[] = [];
    private geoObjectsByUri = new globalThis.Map<string, GeoObject>();

    baseLayers: any[] = [
        {
            name: "Satellite",
            label: "Satellite",
            id: "satellite-v9",
            sprite: "mapbox://sprites/mapbox/satellite-v9",
            url: "mapbox://mapbox.satellite",
            selected: true
        }
    ];

    orderedTypes: string[] = [];

    initialized: boolean = false;

    vectorLayers$: Observable<VectorLayer[]> = this.store.select(getVectorLayers);

    onVectorLayersChange: Subscription;

    public workflowStep: WorkflowStep = WorkflowStep.FullScreenChat;

    private zoomMap: boolean = false;

    public activeTab: string = '0';

    public chatMinimized: boolean = false;

    public attributesPanelOpen = true;
    public graphPanelOpen = false;
    public inspectorSplitPercent = 30;
    public graphPanelWidthPercent = 50;
    private inspectorResizeStartX = 0;
    private inspectorResizeStartPercent = 30;
    private inspectorResizeContainerWidth = 0;
    private graphResizeStartX = 0;
    private graphResizeStartPercent = 50;
    private graphResizeContainerWidth = 0;

    public resultsPanelHeightPx = Math.round(window.innerHeight * 0.4);
    public resultsCollapsed = false;
    private readonly defaultResultsPanelHeightPx = 360;

    public pages: LocationPage[] = [{
        locations: [],
        statement: "",
        type: null,
        limit: 100,
        offset: 0,
        count: 0
    }];

    private currentPageCacheId?: string;

    public saveQueryDialogVisible = false;
    public saveQueryName = '';
    public savedQueryDialogVisible = false;
    public savedQueryName = '';
    public savedQueryDetails: CachedExplorerPages | null = null;
    public canSaveCurrentQueryValue = false;
    public currentSavedQueryIndexValue: number | null = null;
    public currentSavedQueryValue: CachedExplorerPages | null = null;

    private restoringFromUrl = false;

    private urlStateReady = false;
    private initialUrlRestorePending = true;

    private lastUrlSignature = "";

    constructor(
        private configurationService: ConfigurationService,
        private explorerService: ExplorerService,
        private errorService: ErrorService,
        private explorerSessionState: ExplorerSessionStateService,
        private router: Router,
        private route: ActivatedRoute
    ) {

        /*
         * The map should reload when the geo objects change, the styles change, or the neighbors change
         */
        this.onMapObjectsChange = combineLatest([this.geoObjects$, this.neighbors$])
            .pipe(withLatestFrom(this.styles$, this.zoomMap$))
            .subscribe(([[geoObjects, neighbors], styles, zoomMap]) => {
                this.geoObjects = geoObjects;
                this.neighbors = neighbors;
                this.resolvedStyles = styles;
                this.zoomMap = zoomMap;
                this.updateGeoObjectIndex();

                if (this.isInspectorWorkflowStep()) {
                    this.requestZoomMapToExtent();
                }

                this.scheduleRender();
            });

        this.onVectorLayersChange = this.vectorLayers$.subscribe(() => {
            this.renderVectorLayers();
        });

        this.onHighlightedObjectChange = this.highlightedObject$.subscribe(object => {
            this.highlightObject(object == null ? undefined : object.properties.uri);
        });

        this.onSelectedObjectChange = this.selectedObject$
            .pipe(withLatestFrom(this.zoomMap$))
            .subscribe(([object, zoomMap]) => {
                this.activeTab = "0";
                this.selectObject(object, zoomMap);
                if (this.isInspectorWorkflowStep()) {
                    this.requestZoomMapToExtent();
                }
                this.resizeMapAfterLayoutChange();
                this.updateGeoObjectIndex();
                this.writeUrlState(this.workflowStep, { pageCacheId: this.currentPageCacheId });
            });

        this.onWorkflowStepChange = this.workflowState$
            .pipe(
                distinctUntilChanged((a, b) =>
                a.step === b.step &&
                a.data === b.data
                )
            )
            .pipe(withLatestFrom(this.styles$, this.zoomMap$))
            .subscribe(([{ step, data, }, styles, zoomMap]) => {
                const previousStep = this.workflowStep;
                this.activeTab = "0";
                this.resolvedStyles = styles;
                this.workflowStep = step;
                this.zoomMap = zoomMap;
                this.chatMinimized = step === WorkflowStep.MinimizeChat;
                if (step === WorkflowStep.InspectObject) {
                    this.resultsCollapsed = true;
                    this.resultsPanelHeightPx = 0;
                }
                else if (step === WorkflowStep.MapAndResults) {
                    this.resultsCollapsed = true;
                    this.resultsPanelHeightPx = 0;
                }
                else if (this.resultsCollapsed) {
                    this.resultsCollapsed = false;
                    this.resultsPanelHeightPx = Math.round(window.innerHeight * 0.4);
                }

                this.updateGeoObjectIndex();

                if (this.shouldShowMapForWorkflow(step)) {
                    const shouldZoomRestoredLayers =
                        this.zoomRestoredLayersAfterBack &&
                        (step === WorkflowStep.MapAndResults || step === WorkflowStep.MinimizeChat || step === WorkflowStep.DisambiguateObject);

                    if (
                        (step === WorkflowStep.InspectObject || step === WorkflowStep.ViewNeighbors) &&
                        previousStep !== WorkflowStep.InspectObject &&
                        previousStep !== WorkflowStep.ViewNeighbors
                    ) {
                        this.requestZoomMapToExtent();
                    }

                    this.ensureMapInitialized();

                    if (shouldZoomRestoredLayers) {
                        this.requestZoomMapToExtent(24);
                    }

                    if (data?.pageCacheId) {
                        this.currentPageCacheId = data.pageCacheId;
                        this.updateCurrentQueryState();
                    }

                    this.scheduleRender();
                } else {
                    this.geoObjects = [];
                    this.destroyMap();
                }

                this.writeUrlState(step, data);
            });

        this.onPageChange = this.pages$
            .pipe(withLatestFrom(this.styles$, this.zoomMap$))
            .subscribe(([pages, styles, zoomMap]) => {
            this.activeTab = "0";
            this.resolvedStyles = styles;
            this.zoomMap = zoomMap;
            this.pages = pages;
            this.updateGeoObjectIndex();

            if (this.hasResults(pages)) {
                this.currentPageCacheId = this.explorerSessionState.cachePages(pages, 'unknown', this.currentPageCacheId);
            }

            this.updateCurrentQueryState();
            this.scheduleRender();
        });

        this.onSavedQueriesChange = this.explorerSessionState.savedQueries$.subscribe(() => {
            this.updateCurrentQueryState();
        });
    }

    ngOnInit(): void {
        this.urlStateReady = true;
        this.onUrlStateChange = this.route.queryParamMap.subscribe(params => {
            const allowRestoreZoom = this.initialUrlRestorePending;
            this.initialUrlRestorePending = false;

            this.restoreWorkflowFromUrl(
                params.get('view'),
                params.get('state'),
                params.get('inspect') ?? params.get('uri'),
                allowRestoreZoom
            );
        });

        this.configurationService.get().then(configuration => {
            this.store.dispatch(ExplorerActions.setConfiguration(configuration));
        }).catch(error => this.errorService.handleError(error));
    }

    ngOnDestroy(): void {
        this.onMapObjectsChange.unsubscribe();
        this.onVectorLayersChange.unsubscribe();
        this.onHighlightedObjectChange.unsubscribe();
        this.onSelectedObjectChange.unsubscribe();
        this.onWorkflowStepChange.unsubscribe();
        this.onPageChange.unsubscribe();
        this.onSavedQueriesChange.unsubscribe();
        this.onUrlStateChange?.unsubscribe();
    }

    private restoreWorkflowFromUrl(view: string | null, pageCacheId: string | null, inspectUri: string | null, allowRestoreZoom = false): void {
        const signature = this.urlSignature(view, pageCacheId, inspectUri);

        if (signature === this.lastUrlSignature) {
            return;
        }

        this.lastUrlSignature = signature;

        this.restoringFromUrl = true;

        if (!view) {
            this.currentPageCacheId = undefined;
            this.updateCurrentQueryState();
            this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.FullScreenChat }));
            this.restoringFromUrl = false;
            return;
        }

        if (pageCacheId) {
            this.currentPageCacheId = pageCacheId;
            this.updateCurrentQueryState();
        }

        if (view === 'chat') {
            this.currentPageCacheId = undefined;
            this.updateCurrentQueryState();
            this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.FullScreenChat }));
            this.restoringFromUrl = false;
            return;
        }

        if (view === 'inspect' && inspectUri) {
            this.restoreInspectObjectFromUrl(inspectUri, pageCacheId ?? undefined, allowRestoreZoom);
            return;
        }

        const cachedPages = this.explorerSessionState.getPages(pageCacheId);

        if (!cachedPages && !inspectUri) {
            this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.FullScreenChat }));
            this.restoringFromUrl = false;
            return;
        }

        const step = this.workflowStepForUrlView(view);

        if (!step) {
            this.restoringFromUrl = false;
            return;
        }

        if (cachedPages && step === WorkflowStep.MinimizeChat) {
            if (allowRestoreZoom) {
                this.requestZoomMapToExtent();
            }

            this.store.dispatch(ExplorerActions.setPages({
                pages: cachedPages,
                zoomMap: false
            }));

            this.store.dispatch(ExplorerActions.setWorkflowStep({
                step,
                data: { pageCacheId }
            }));
        }
        else if (cachedPages && (step === WorkflowStep.MapAndResults || step === WorkflowStep.DisambiguateObject)) {
            if (allowRestoreZoom) {
                this.requestZoomMapToExtent();
            }

            this.store.dispatch(ExplorerActions.showPagesOnMap({
                pages: cachedPages,
                zoomMap: false,
                step,
                data: { pageCacheId }
            }));
        }

        if (inspectUri) {
            this.restoreInspectedObject(inspectUri, false);
            return;
        }

        this.restoringFromUrl = false;
    }

    private restoreInspectObjectFromUrl(uri: string, pageCacheId?: string, allowRestoreZoom = false): void {
        const cachedPages = this.explorerSessionState.getPages(pageCacheId);

        if (cachedPages) {
            if (allowRestoreZoom) {
                this.requestZoomMapToExtent();
            }

            this.store.dispatch(ExplorerActions.setPages({
                pages: cachedPages,
                zoomMap: false
            }));
        }

        this.explorerService.getAttributes(uri, true, uri.startsWith(environment.basePrefix))
            .then(geoObject => {
                if (!cachedPages) {
                    this.store.dispatch(ExplorerActions.setWorkflowStep({
                        step: WorkflowStep.InspectObject,
                        data: geoObject,
                        zoomMap: true
                    }));
                    return;
                }

                this.store.dispatch(ExplorerActions.setWorkflowStep({
                    step: WorkflowStep.InspectObject,
                    data: geoObject,
                    zoomMap: !cachedPages
                }));
            })
            .catch(error => {
                this.errorService.handleError(error);
                this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.FullScreenChat }));
            })
            .finally(() => {
                this.restoringFromUrl = false;
            });
    }

    private restoreInspectedObject(uri: string, zoomMap: boolean): void {
        this.explorerService.getAttributes(uri, true, uri.startsWith(environment.basePrefix))
            .then(geoObject => {
                this.store.dispatch(ExplorerActions.appendWorkflowStep({
                    step: WorkflowStep.InspectObject,
                    data: geoObject,
                    zoomMap
                }));
            })
            .catch(error => this.errorService.handleError(error))
            .finally(() => {
                this.restoringFromUrl = false;
            });
    }

    private workflowStepForUrlView(view: string): WorkflowStep.MapAndResults | WorkflowStep.DisambiguateObject | WorkflowStep.MinimizeChat | undefined {
        if (view === 'map') return WorkflowStep.MapAndResults;
        if (view === 'disambiguate') return WorkflowStep.DisambiguateObject;
        if (view === 'min-chat') return WorkflowStep.MinimizeChat;

        return undefined;
    }

    private writeUrlState(step: WorkflowStep, data?: any): void {
        if (!this.urlStateReady || this.restoringFromUrl) {
            return;
        }

        const queryParams = this.queryParamsForWorkflow(step, data);
        const signature = this.urlSignature(queryParams.view ?? null, queryParams.state ?? null, queryParams.inspect ?? null);

        if (signature === this.lastUrlSignature) {
            return;
        }

        this.lastUrlSignature = signature;

        this.router.navigate([], {
            relativeTo: this.route,
            queryParams,
            replaceUrl: step === WorkflowStep.FullScreenChat,
        });
    }

    private queryParamsForWorkflow(step: WorkflowStep, data?: any): { view?: string; state?: string; inspect?: string } {
        if (step === WorkflowStep.FullScreenChat) {
            return { view: 'chat' };
        }

        const pageCacheId = data?.pageCacheId ?? this.currentPageCacheId;
        const inspect = this.selectedObject?.properties?.uri;

        if (step === WorkflowStep.MapAndResults) {
            return { view: 'map', state: pageCacheId };
        }

        if (step === WorkflowStep.DisambiguateObject) {
            return { view: 'disambiguate', state: pageCacheId, inspect };
        }

        if (step === WorkflowStep.MinimizeChat) {
            return { view: 'min-chat', state: pageCacheId };
        }

        if (step === WorkflowStep.InspectObject && data?.properties?.uri) {
            return { view: 'inspect', inspect: data.properties.uri };
        }

        if (step === WorkflowStep.ViewNeighbors && data?.properties?.uri) {
            return { view: 'map', state: pageCacheId, inspect: data.properties.uri };
        }

        return { view: 'chat' };
    }

    private urlSignature(view: string | null, pageCacheId: string | null, inspectUri: string | null): string {
        return JSON.stringify({ view, state: pageCacheId, inspect: inspectUri });
    }

    hasResults(pages: LocationPage[] | null | undefined): boolean {
        return (pages ?? []).some(page => (page.locations?.length ?? 0) > 0 || page.count > 0);
    }

    canSaveCurrentQuery(): boolean {
        return this.currentPageCacheId != null && this.hasResults(this.pages);
    }

    currentSavedQueryIndex(): number | null {
        return this.explorerSessionState.getSavedQueryIndex(this.currentPageCacheId);
    }

    private updateCurrentQueryState(): void {
        this.canSaveCurrentQueryValue = this.canSaveCurrentQuery();
        this.currentSavedQueryIndexValue = this.currentPageCacheId
            ? this.explorerSessionState.getSavedQueryIndex(this.currentPageCacheId)
            : null;
        this.currentSavedQueryValue = this.currentSavedQueryIndexValue != null && this.currentPageCacheId
            ? this.explorerSessionState.getCachedPages(this.currentPageCacheId)
            : null;
    }

    currentSavedQuery(): CachedExplorerPages | null {
        return this.currentSavedQueryValue;
    }

    openSaveQueryDialog(): void {
        if (!this.currentPageCacheId) {
            return;
        }

        if (this.currentSavedQueryIndexValue != null) {
            return;
        }

        this.saveQueryName = this.explorerSessionState.getCachedPages(this.currentPageCacheId)?.title ?? '';
        this.saveQueryDialogVisible = true;
    }

    openSavedQueryDialog(): void {
        const savedQuery = this.currentSavedQuery();

        if (!savedQuery) {
            return;
        }

        this.savedQueryDetails = savedQuery;
        this.savedQueryName = savedQuery.title;
        this.savedQueryDialogVisible = true;
    }

    saveCurrentQuery(): void {
        if (!this.currentPageCacheId || !this.saveQueryName.trim()) {
            return;
        }

        if (this.explorerSessionState.saveCachedPages(this.currentPageCacheId, this.saveQueryName)) {
            this.updateCurrentQueryState();
            this.saveQueryDialogVisible = false;
        }
    }

    renameCurrentSavedQuery(): void {
        if (!this.savedQueryDetails || !this.savedQueryName.trim()) {
            return;
        }

        this.explorerSessionState.renameSavedPages(this.savedQueryDetails.id, this.savedQueryName);
        this.savedQueryDetails = this.explorerSessionState.getCachedPages(this.savedQueryDetails.id);
        this.updateCurrentQueryState();
    }

    unsaveCurrentQuery(): void {
        if (!this.savedQueryDetails) {
            return;
        }

        this.explorerSessionState.unsaveCachedPages(this.savedQueryDetails.id);
        this.savedQueryDialogVisible = false;
        this.savedQueryDetails = null;
        this.savedQueryName = '';
        this.updateCurrentQueryState();
    }

    formatSavedQueryDate(query: CachedExplorerPages | null): string {
        if (!query) {
            return '';
        }

        return new Date(query.savedAt ?? query.updatedAt).toLocaleString();
    }

    onResultsHeightChange(heightPx: number): void {
        this.resultsPanelHeightPx = heightPx;

        this.resizeMapAfterLayoutChange();
    }

    onResultsCollapsedChange(collapsed: boolean): void {
        this.resultsCollapsed = collapsed;
        this.resizeMapAfterLayoutChange();

        if (this.workflowStep === WorkflowStep.InspectObject) {
            this.scheduleRender();
        }
    }

    openResultsForType(type: string, event?: Event): void {
        event?.stopPropagation();

        this.resultsTable?.selectActivePage(type);
        this.resultsCollapsed = false;
        this.resultsPanelHeightPx = this.getExpandedResultsPanelHeight();
        this.resizeMapAfterLayoutChange();

        if (this.workflowStep === WorkflowStep.InspectObject) {
            this.scheduleRender();
        }
    }

    onLegendPanelClick(event: MouseEvent): void {
        const target = event.target as HTMLElement | null;

        if (!target?.closest('.p-panel-header')) {
            return;
        }

        if (target.closest('button, .p-panel-header-icon, .p-panel-toggle-button')) {
            return;
        }

        this.legendCollapsed = !this.legendCollapsed;
    }

    private getExpandedResultsPanelHeight(): number {
        if (this.resultsPanelHeightPx > 0) {
            return this.resultsPanelHeightPx;
        }

        return Math.round(Math.min(
            this.defaultResultsPanelHeightPx,
            Math.max(220, window.innerHeight - 96)
        ));
    }

    private resizeMapAfterLayoutChange(): void {
        if (this.mapResizeScheduled) {
            return;
        }

        this.mapResizeScheduled = true;

        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                this.mapResizeScheduled = false;
                this.map?.resize();
            });
        });
    }

    private shouldShowMapForWorkflow(step: WorkflowStep): boolean {
        return step === WorkflowStep.MapAndResults ||
            step === WorkflowStep.DisambiguateObject ||
            step === WorkflowStep.MinimizeChat ||
            step === WorkflowStep.InspectObject ||
            step === WorkflowStep.ViewNeighbors;
    }

    public isInspectorWorkflowStep(): boolean {
        return this.workflowStep === WorkflowStep.InspectObject ||
            this.workflowStep === WorkflowStep.ViewNeighbors;
    }

    public toggleAttributesPanel(): void {
        this.attributesPanelOpen = !this.attributesPanelOpen;
        this.syncMapToInspectorLayout();
    }

    public toggleGraphPanel(): void {
        this.graphPanelOpen = !this.graphPanelOpen;
        this.syncMapToInspectorLayout();
    }

    public startInspectorResize(event: PointerEvent): void {
        if (!this.attributesPanelOpen) {
            return;
        }

        const container = (event.currentTarget as HTMLElement).closest('.inspector-workspace') as HTMLElement | null;

        if (!container) {
            return;
        }

        this.inspectorResizeStartX = event.clientX;
        this.inspectorResizeStartPercent = this.inspectorSplitPercent;
        this.inspectorResizeContainerWidth = container.getBoundingClientRect().width;
        (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
        event.preventDefault();
    }

    public onInspectorResize(event: PointerEvent): void {
        if (!this.attributesPanelOpen || this.inspectorResizeContainerWidth <= 0) {
            return;
        }

        const deltaPercent = ((event.clientX - this.inspectorResizeStartX) / this.inspectorResizeContainerWidth) * 100;
        this.inspectorSplitPercent = this.clampInspectorSplit(this.inspectorResizeStartPercent + deltaPercent);
        this.resizeMapAfterLayoutChange();
    }

    public endInspectorResize(event: PointerEvent): void {
        this.inspectorResizeContainerWidth = 0;
        (event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId);
        this.resizeMapAfterLayoutChange();
    }

    private clampInspectorSplit(value: number): number {
        return Math.min(75, Math.max(25, value));
    }

    public startGraphResize(event: PointerEvent): void {
        if (!this.graphPanelOpen || this.attributesPanelOpen) {
            return;
        }

        const container = (event.currentTarget as HTMLElement).closest('.inspector-workspace') as HTMLElement | null;

        if (!container) {
            return;
        }

        this.graphResizeStartX = event.clientX;
        this.graphResizeStartPercent = this.graphPanelWidthPercent;
        this.graphResizeContainerWidth = container.getBoundingClientRect().width;
        (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
        event.preventDefault();
    }

    public onGraphResize(event: PointerEvent): void {
        if (!this.graphPanelOpen || this.attributesPanelOpen || this.graphResizeContainerWidth <= 0) {
            return;
        }

        const deltaPercent = ((event.clientX - this.graphResizeStartX) / this.graphResizeContainerWidth) * 100;
        this.graphPanelWidthPercent = this.clampGraphPanelWidth(this.graphResizeStartPercent + deltaPercent);
        this.resizeMapAfterLayoutChange();
    }

    public endGraphResize(event: PointerEvent): void {
        this.graphResizeContainerWidth = 0;
        (event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId);
        this.resizeMapAfterLayoutChange();
    }

    private clampGraphPanelWidth(value: number): number {
        return Math.min(85, Math.max(30, value));
    }

    private syncMapToInspectorLayout(): void {
        if (this.mapSyncScheduled) {
            return;
        }

        this.mapSyncScheduled = true;

        requestAnimationFrame(() => {
            this.mapSyncScheduled = false;

            if (this.shouldShowMapForWorkflow(this.workflowStep)) {
                this.ensureMapInitialized();
                this.scheduleRender();
                this.resizeMapAfterLayoutChange();
            }
        });
    }

    private destroyMap(): void {
        this.featurePopup?.remove();
        this.featurePopup = undefined;

        if (this.map) {
            this.map.remove();
            this.map = undefined;
        }

        this.mapInitialized = false;
        this.initialized = false;
        this.renderedObjects = [];
        this.typeLegend = {};
        this.orderedTypes = [];
        this.highlightedObject = null;
    }

    private runWhenMapReady(callback: () => void, maxFrames = 120): void {
        this.ensureMapInitialized();

        let frames = 0;

        const check = () => {
            frames++;

            const mapReady =
                !!this.map &&
                this.initialized &&
                !!this.map.getStyle();

            if (mapReady) {
                callback();
                return;
            }

            if (frames >= maxFrames) {
                console.warn('Map was not ready after waiting.', {
                    hasMap: !!this.map,
                    initialized: this.initialized,
                    loaded: this.map?.loaded(),
                    style: this.map?.getStyle()
                });
                return;
            }

            requestAnimationFrame(check);
        };

        requestAnimationFrame(check);
    }

    cancelDisambiguation() {
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

    goBack() {
        const shouldZoomToRestoredLayers = this.isInspectorWorkflowStep();
        let shouldResetInspectorPanelState = false;

        this.previousWorkflowStep$.pipe(take(1)).subscribe(previousStep => {
            shouldResetInspectorPanelState =
                this.isInspectorWorkflowStep() &&
                previousStep !== WorkflowStep.InspectObject &&
                previousStep !== WorkflowStep.ViewNeighbors;
        });

        this.zoomRestoredLayersAfterBack = shouldZoomToRestoredLayers;
        this.store.dispatch(ExplorerActions.backWorkflowStep());

        if (shouldResetInspectorPanelState) {
            this.resetInspectorPanelState();
        }
    }

    private resetInspectorPanelState(): void {
        this.attributesPanelOpen = true;
        this.graphPanelOpen = false;
    }

    onMapBack() {
        if (this.workflowStep === WorkflowStep.DisambiguateObject) {
            this.cancelDisambiguation();
        }
        else if (this.isInspectorWorkflowStep()) {
            this.previousWorkflowStep$.pipe(take(1)).subscribe(previousStep => {
                if (previousStep === WorkflowStep.DisambiguateObject) {
                    this.goBack();
                }
                else {
                    this.resetInspectorPanelState();
                    this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.MapAndResults }));
                }
            });
        }
        else if (this.workflowStep === WorkflowStep.MapAndResults) {
            this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.FullScreenChat }));
        }
        else {
            this.goBack();
        }
    }

    disambiguate() {
        this.store.dispatch(ExplorerActions.setPages({ pages: [{ 
            locations: [],
            statement: "",
            type: null,
            limit: 100,
            offset: 0,
            count: 0
        }], zoomMap: false }));
        this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.FullScreenChat, data: this.selectedObject }));
    }

    minimizeChat() {
        if (!this.chatMinimized) {
            this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.MinimizeChat }));
            this.chatMinimized = true;
        }
        else {
            this.store.dispatch(ExplorerActions.setWorkflowStep({ step: WorkflowStep.MapAndResults }));
            this.chatMinimized = false;
        }
    }

    onTabChange(event: any) {
        this.activeTab = event;
    }

    private ensureMapInitialized(): void {
        if (this.mapInitScheduled) {
            return;
        }

        this.mapInitScheduled = true;

        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                this.mapInitScheduled = false;
                const el = this.mapElement?.nativeElement;

                if (!el) return;

                const rect = el.getBoundingClientRect();

                if (rect.width === 0 || rect.height === 0) {
                    console.warn('Map container has zero size. Skipping map init.', rect);
                    return;
                }

                if (!this.mapInitialized || !this.map) {
                    this.initializeMap();
                    this.mapInitialized = true;
                    return;
                }

                this.map.resize();
            });
        });
    }

    render(): void {
        this.renderScheduled = false;

        if (this.initialized) {
            // Clear the map
            this.clearAllMapData();

            // Handle the geo objects
            const types = Object.keys(this.geoObjectsByType());

            // Order the types by the order defined in their style config
            this.orderedTypes = types.sort((a, b) => {
                return (this.resolvedStyles[a]?.order ?? 999) - (this.resolvedStyles[b]?.order ?? 999);
            });

            this.calculateTypeLegend();

            this.mapGeoObjects();

            if (this.zoomMap || this.zoomMapToExtentOnNextRender) {
                const zoomed = this.zoomToAll();

                if (zoomed) {
                    this.zoomMapToExtentOnNextRender = false;
                    this.zoomMapToExtentRetries = 0;
                    this.zoomRestoredLayersAfterBack = false;
                }
                else if (this.zoomMapToExtentOnNextRender && this.zoomMapToExtentRetries > 0) {
                    this.zoomMapToExtentRetries--;
                    this.scheduleRender();
                }
            }

            this.renderHighlights();

            this.renderedObjects = this.allGeoObjects().map(obj => obj.properties.uri);
        }
    }

    private scheduleRender(): void {
        if (this.renderScheduled) {
            return;
        }

        this.renderScheduled = true;

        requestAnimationFrame(() => {
            this.render();
        });
    }

    private requestZoomMapToExtent(retries = 12): void {
        this.zoomMapToExtentOnNextRender = true;
        this.zoomMapToExtentRetries = Math.max(this.zoomMapToExtentRetries, retries);
    }


    renderVectorLayers(): void {
        if (this.initialized) {
            // Clear the map
            this.clearVectorLayers();

            // Handle the vector layers
            this.mapVectorLayers();
        }
    }

    calculateTypeLegend() {
        var oldTypeLegend = JSON.parse(JSON.stringify(this.typeLegend));
        this.typeLegend = {};

        this.orderedTypes.forEach(type => {
            this.typeLegend[type] = {
                label: this.labelForType(type),
                color: this.resolvedStyles[type].color,
                visible: (oldTypeLegend[type] == null ? true : oldTypeLegend[type].visible),
                included: (oldTypeLegend[type] == null ? true : oldTypeLegend[type].included)
            }
        });
    }

    toggleTypeLegend(type: string, legend: any): void {
        legend.visible = !legend.visible;
        this.setTypeLayerVisibility(type, legend.visible);
    }

    private setTypeLayerVisibility(type: string, visible: boolean): void {
        if (!this.map) {
            return;
        }

        const visibility = visible ? 'visible' : 'none';
        [type, `${type}-LABEL`, `hover-${type}`].forEach(layerId => {
            if (this.map?.getLayer(layerId)) {
                this.map.setLayoutProperty(layerId, 'visibility', visibility);
            }
        });

        if (!visible && this.highlightedObject?.properties.type === type) {
            this.highlightObject(undefined);
        }
    }

    labelForType(typeUri: string): string {
        if (this.resolvedStyles && this.resolvedStyles[typeUri] && this.resolvedStyles[typeUri].label) {
            return this.resolvedStyles[typeUri].label as string;
        } else {
            return ExplorerComponent.uriToLabel(typeUri);
        }
    }

    public static uriToLabel(uri: string): string {
        let i = uri.lastIndexOf("#");
        if (i == -1) return uri;

        return uri.substring(i + 1);
    }

    getTypeLegend() { return this.typeLegend; }

    clearAllMapData(): void {
        if (!this.map) return;

        const map = this.map;
        const style = map.getStyle();

        if (!style) return;

        const baseLayerIds = new Set(this.baseLayers.map(layer => layer.id));
        const baseSourceIds = new Set<string>(['mapbox']);

        /*
        * Remove layers first.
        *
        * Important:
        * - Remove in reverse order because MapLibre layer ordering can matter.
        * - Keep base layers.
        * - Keep vector-source layers, because those are handled separately by clearVectorLayers().
        * - Remove source-less non-base layers defensively, because they are not tied to vector sources.
        */
        const layers = [...(style.layers ?? [])].reverse();

        for (const layer of layers) {
            if (!map.getLayer(layer.id)) {
                continue;
            }

            if (baseLayerIds.has(layer.id)) {
                continue;
            }

            const sourceId = (layer as any).source as string | undefined;

            if (sourceId) {
                const source = map.getSource(sourceId);

                if (source?.type === 'vector') {
                    continue;
                }
            }

            try {
                map.removeLayer(layer.id);
            } catch (error) {
                console.warn('Failed to remove map layer', {
                    layerId: layer.id,
                    sourceId,
                    error
                });
            }
        }

        /*
        * Remove non-vector sources after their layers are gone.
        *
        * Important:
        * - Sources cannot be removed while layers still reference them.
        * - Keep base sources.
        * - Keep vector sources, because vector layer cleanup is separate.
        */
        const sourceIds = Object.keys(map.getStyle().sources ?? {});

        for (const sourceId of sourceIds) {
            if (baseSourceIds.has(sourceId)) {
                continue;
            }

            const source = map.getSource(sourceId);

            if (!source) {
                continue;
            }

            if (source.type === 'vector') {
                continue;
            }

            try {
                map.removeSource(sourceId);
            } catch (error) {
                console.warn('Failed to remove map source', {
                    sourceId,
                    sourceType: source.type,
                    error
                });
            }
        }

        /*
        * These are render-local bookkeeping values, not NgRx-owned data.
        * Do not clear this.geoObjects or this.neighbors here.
        */
        this.renderedObjects = [];
        this.orderedTypes = [];
        // this.typeLegend = {}; // Wipes layer visible state if we clear this here
    }

    clearVectorLayers() {
        if (!this.map) return;

        this.map!.getStyle().layers.forEach(layer => {
            if (this.map!.getLayer(layer.id) && this.baseLayers[0].id !== layer.id) {
                if (this.map!.getSource((layer as any).source)?.type === "vector") {
                    this.map!.removeLayer(layer.id);
                }
            }
        });

        Object.keys(this.map!.getStyle().sources).forEach(source => {
            if (this.map!.getSource(source) && source !== 'mapbox' && this.map!.getSource(source)?.type === "vector") {
                this.map!.removeSource(source);
            }
        });
    }

    mapVectorLayers() {

        if (!this.map) return;

        const layers = this.map!.getStyle().layers;

        const baseLayer = layers.length > 1 ? layers[1].id : null;

        // Assuming the base layer is the first layer on the map
        this.vectorLayers$.pipe(take(1)).subscribe(layers => {
            [...layers].filter(l => l.enabled).forEach(layer => {

                this.map!.addSource(layer.id, {
                    type: "vector",
                    tiles: [
                        layer.url
                    ],
                    promoteId: layer.codeProperty
                });

                // Add the hierarchy label layer
                this.map!.addLayer({
                    "id": layer.id + "-label",
                    "source": layer.id,
                    "source-layer": layer.sourceLayer,
                    "type": "symbol",
                    "paint": {
                        "text-color": "black",
                        "text-halo-color": "#fff",
                        "text-halo-width": 2
                    },
                    "layout": {
                        "text-field": ["get", layer.labelProperty],
                        "text-font": ["NotoSansRegular"],
                        "text-offset": [0, 0.6],
                        "text-anchor": "top",
                        "text-size": 12,
                    },
                }, baseLayer as string);

                if (layer.geometryType === "Polygon") {
                    // Add the hierarchy polygon layer
                    this.map!.addLayer({
                        "id": layer.id + "-shape",
                        "source": layer.id,
                        "source-layer": layer.sourceLayer,
                        "type": "fill",
                        "paint": {
                            'fill-color': [
                                "case",
                                ["boolean", ["feature-state", "selected"], false],
                                SELECTED_COLOR,
                                layer.color
                            ],
                            "fill-opacity": 0.8,
                            "fill-outline-color": "black"
                        }
                    }, layer.id + "-label");
                }
                else if (layer.geometryType === "Line") {
                    // Add the hierarchy polygon layer
                    this.map!.addLayer({
                        "id": layer.id + "-shape",
                        "source": layer.id,
                        "source-layer": layer.sourceLayer,
                        "type": "line",
                        "paint": {
                            'line-color': [
                                "case",
                                ["boolean", ["feature-state", "selected"], false],
                                SELECTED_COLOR,
                                layer.color
                            ]
                        }
                    }, layer.id + "-label");
                }
                else if (layer.geometryType === "Point") {
                    // Add the hierarchy polygon layer
                    this.map!.addLayer({
                        "id": layer.id + "-shape",
                        "source": layer.id,
                        "source-layer": layer.sourceLayer,
                        "type": "circle",
                        "paint": {
                            "circle-radius": 10,
                            "circle-color": [
                                "case",
                                ["boolean", ["feature-state", "selected"], false],
                                SELECTED_COLOR,
                                layer.color
                            ],
                            "circle-stroke-width": 2,
                            "circle-stroke-color": "#FFFFFF"
                        }
                    }, layer.id + "-label");
                }
                else {
                    console.log('Unknown geometry type', layer)
                }

            });
        });

    }

    mapGeoObjects() {
        // setTimeout(() => {
        // Find the index of the first symbol layer in the map style
        const layers = this.map?.getStyle().layers;
        let firstSymbolId;
        for (let i = 0; i < layers!.length; i++) {
            if (layers![i].type === 'symbol') {
                firstSymbolId = layers![i].id;
                break;
            }
        }

        // The layers are organized by the type, so we have to group geoObjects by type and create a layer for each type
        let gosByType = this.geoObjectsByType();

        let allGeoObjects = this.allGeoObjects();
        for (let i = this.orderedTypes.length - 1; i >= 0; --i) {
            let type = this.orderedTypes[i];
            let geoObjects = gosByType[type];

            if (geoObjects.length == 0) continue;
            if (geoObjects[0].geometry == null) continue; // TODO : Find this out at the type level...
            if (!this.typeLegend[type].visible) continue;

            let geojson: any = {
                type: "FeatureCollection",
                features: []
            }

            for (let i = 0; i < allGeoObjects.length; ++i) {
                if (allGeoObjects[i].properties.type !== type) continue;

                let geoObject = allGeoObjects[i];

                geojson.features.push(geoObject);
            }

            this.map?.addSource(type, {
                type: "geojson",
                data: geojson,
                promoteId: 'uri' // A little surprised at mapbox here, but without this param it won't use the id property for the feature id
            });

            this.map?.addLayer(this.layerConfig(type, geoObjects[0].geometry.type.toUpperCase()),
                firstSymbolId);

            // Label layer
            this.map?.addLayer({
                id: type + "-LABEL",
                source: type,
                type: "symbol",
                paint: {
                    "text-color": "black",
                    "text-halo-color": "#fff",
                    "text-halo-width": 2
                },
                layout: {
                    "text-field": ["get", "label"],
                    "text-font": ["NotoSansRegular"],
                    "text-offset": [0, 0.6],
                    "text-anchor": "top",
                    "text-size": 12
                }
            });

            this.addHighlightLayers(type, geoObjects[0].geometry.type.toUpperCase());
        }
        // },10);
    }

    private addHighlightLayers(type: string, geometryType: string)
    {
        if (geometryType === "MULTIPOLYGON" || geometryType === "POLYGON") {
            this.map!.addLayer({
                "id": "hover-" + type,
                "type": "fill",
                "source": type,
                "paint": {
                    'fill-color': [
                        "case",
                        ["boolean", ["feature-state", "selected"], false],
                        SELECTED_COLOR,
                        HOVER_COLOR
                    ],
                    'fill-opacity': 0.5
                },
                filter: ["all",
                    ["==", "uri", "NONE"] // start with a filter that doesn"t select anything
                ]
            });
        } else if (geometryType === "POINT" || geometryType === "MULTIPOINT") {
            this.map!.addLayer({
                "id": "hover-" + type,
                "type": "circle",
                "source": type,
                "paint": {
                    "circle-radius": 10,
                    "circle-color": [
                        "case",
                        ["boolean", ["feature-state", "selected"], false],
                        SELECTED_COLOR,
                        HOVER_COLOR
                    ],
                    "circle-stroke-width": 2,
                    "circle-stroke-color": "#FFFFFF"
                },
                filter: ["all",
                    ["==", "uri", "NONE"] // start with a filter that doesn"t select anything
                ]
            });
        } else if (geometryType === "LINE" || geometryType === "MULTILINE" || geometryType === "MULTILINESTRING") {
            this.map!.addLayer({
                "id": "hover-" + type,
                "type": "line",
                "source": type,
                "paint": {
                    "line-color": [
                        "case",
                        ["boolean", ["feature-state", "selected"], false],
                        SELECTED_COLOR,
                        HOVER_COLOR
                    ],
                    "line-width": 3,
                },
                filter: ["all",
                    ["==", "uri", "NONE"] // start with a filter that doesn"t select anything
                ]
            });
        }
    }

    private layerConfig(type: string, geometryType: string): any {
        let layerConfig: any = {
            id: type,
            source: type
        };

        if (geometryType === "MULTIPOLYGON" || geometryType === "POLYGON") {
            layerConfig.paint = {
                'fill-color': [
                    "case",
                    ["boolean", ["feature-state", "selected"], false],
                    SELECTED_COLOR,
                    this.typeLegend[type].color
                ],
                'fill-opacity': 0.8
            };
            layerConfig.type = "fill";
        } else if (geometryType === "POINT" || geometryType === "MULTIPOINT") {
            layerConfig.paint = {
                "circle-radius": 10,
                "circle-color": [
                    "case",
                    ["boolean", ["feature-state", "selected"], false],
                    SELECTED_COLOR,
                    this.typeLegend[type].color
                ],
                "circle-stroke-width": 2,
                "circle-stroke-color": "#FFFFFF"
            };
            layerConfig.type = "circle";
        } else if (geometryType === "LINE" || geometryType === "MULTILINE" || geometryType === "MULTILINESTRING") {
            layerConfig.layout = {
                "line-join": "round",
                "line-cap": "round"
            }
            layerConfig.paint = {
                "line-color": [
                    "case",
                    ["boolean", ["feature-state", "selected"], false],
                    SELECTED_COLOR,
                    this.typeLegend[type].color
                ],
                "line-width": 3
            }
            layerConfig.type = "line";
        } else {
            // eslint-disable-next-line no-console
            console.log("Unexpected geometry type [" + geometryType + "]");
        }

        return layerConfig;
    }

    allGeoObjects(): GeoObject[] {
        return this.currentGeoObjects;
    }

    private updateGeoObjectIndex(): void {
        this.currentGeoObjects = this.buildCurrentGeoObjects();
        this.geoObjectsByUri = new globalThis.Map(
            this.currentGeoObjects.map(obj => [obj.properties.uri, obj])
        );
    }

    private buildCurrentGeoObjects(): GeoObject[] {
        if (this.workflowStep === WorkflowStep.InspectObject && this.resultsCollapsed) {
            return this.uniqueGeoObjects(this.neighbors.concat(this.selectedObject ? [this.selectedObject] : []));
        }

        let all = this.geoObjects.concat(this.neighbors);

        if (this.selectedObject)
            all.push(this.selectedObject);

        return this.uniqueGeoObjects(all);
    }

    private getGeoObjectByUri(uri: string | null | undefined): GeoObject | undefined {
        if (uri == null) {
            return undefined;
        }

        return this.geoObjectsByUri.get(uri);
    }

    private uniqueGeoObjects(objects: GeoObject[]): GeoObject[] {
        // Enforce each GeoObject only occurs once
        const seen = new Set<string>();
        return objects.filter(obj => seen.has(obj.properties.uri) ? false : seen.add(obj.properties.uri));
    }

    geoObjectsByType(): { [key: string]: GeoObject[] } {
        let gos: { [key: string]: GeoObject[] } = {};
        var allGeoObjects = this.allGeoObjects();

        for (let i = 0; i < allGeoObjects.length; ++i) {
            let geoObject = allGeoObjects[i];

            if (gos[geoObject.properties.type] === undefined) {
                gos[geoObject.properties.type] = [];
            }

            gos[geoObject.properties.type].push(geoObject);
        }

        return gos;
    }


    public getUsaceUri(go: GeoObject): string { return ExplorerComponent.getUsaceUri(go); }

    public static getUsaceUri(go: GeoObject): string {
        if (go.properties.uri.indexOf('dime.usace.mil') !== -1) {
            return go.properties.uri;
        } else if (go.properties.uri.indexOf('georegistry') !== -1) {
            // Program
            // http://dime.usace.mil/data/program#010180
            // http://dime.usace.mil/data/program%23000510

            // Channel Reach
            // https://dev-georegistry.geoprism.net/lpg/deliverable2024/0#ChannelReach-CESWL_AR_06_TER_5
            // http://dime.usace.mil/data/channelReach%23CESWT_AR_16_WBF_13

            // Project
            // https://dev-georegistry.geoprism.net/lpg/deliverable2024/0#Project-30000574
            // http://dime.usace.mil/data/remis_project%23PROJ644

            let uri = go.properties.uri
                .replace("https://dev-georegistry.geoprism.net/lpg/deliverable2024/0#", "http://dime.usace.mil/data/");

            if (uri.indexOf("Project-") !== -1) {
                uri = uri.replace("Project-", "remis_project%23");
            } else {
                uri = uri.replace("-", "%23");
            }

            if (uri.indexOf("ChannelReach") !== -1) {
                uri = uri.replace("ChannelReach", "channelReach");
            }

            return uri;
        } else {
            return go.properties.uri;
        }
    }

    public getObjectUrl(go: GeoObject): string {
        return ExplorerComponent.getObjectUrl(go);
    }

    public static getObjectUrl(go: GeoObject): string {
        if (go.properties.type.indexOf("Program") != -1
            || go.properties.type.indexOf("ChannelReach") != -1
            || go.properties.uri.indexOf("Project") != -1
            || go.properties.uri.indexOf("usace.mil") != -1
        ) {
            return "https://prism.usace-dime.net/view?uri=" + this.getUsaceUri(go);
        } else {
            return go.properties.uri;
        }
    }

    /*
     * Fit the map to the bounds of all of the layers
     */
    zoomToAll(): boolean {
        if (!this.map || this.allGeoObjects().length === 0) {
            return false;
        }

        const layerBounds = this.orderedTypes.map(type => {
            // TODO: Is there a better way to get the layer data from the map?
            const source = this.map?.getSource(type);

            if (source instanceof GeoJSONSource) {
                const data = ((source as GeoJSONSource)._data) as AllGeoJSON;

                if ((data as any)?.features?.length > 0) {
                    return bboxPolygon(bbox(data));
                }
            }

            return null;
        }).filter(a => a != null)

        if (layerBounds.length === 0) {
            return false;
        }

        try {
            const allBounds = bbox(layerBounds.reduce((a: any, b: any) => {
                if (a == null) {
                    return b;
                }

                if (b == null) {
                    return a;
                }

                try {
                    return union(a.geometry, b.geometry) as any
                }
                catch (e) {
                    return b.geometry
                }
            }, null)) as LngLatBoundsLike

            this.map.fitBounds(allBounds, { padding: 50 });

            return true;
        }
        catch (error) {
            console.warn('Unable to zoom to layer extent yet.', error);
            return false;
        }
    }

    /*
     * Zooms to a specific GeoObject
     */
    zoomTo(uri: string) {
        let geoObject = this.allGeoObjects().find(go => go.properties.uri === uri);
        if (geoObject == null) return;

        let geojson = geoObject.geometry as any;

        const geometryType = geojson.type.toUpperCase();

        if (geometryType === "MULTIPOINT" || geometryType === "POINT") {
            let coords = geojson.coordinates;

            if (coords) {
                let bounds = new LngLatBounds();
                coords.forEach((coord: any) => {
                    bounds.extend(coord);
                });

                let center = bounds.getCenter();
                let pt = new LngLat(center.lng, center.lat);

                this.map?.flyTo({
                    center: pt,
                    zoom: 9,
                    essential: true
                });
            }
        } else if (geometryType === "MULTIPOLYGON" || geometryType === "MIXED") {
            let coords = geojson.coordinates;

            if (coords) {
                let bounds = new LngLatBounds();
                coords.forEach((polys: any) => {
                    polys.forEach((subpoly: any) => {
                        subpoly.forEach((coord: any) => {
                            bounds.extend(coord);
                        });
                    });
                });

                this.map?.fitBounds(bounds, {
                    padding: 20
                });
            }
        } else if (geometryType === "POLYGON") {
            let coords = geojson.coordinates;

            if (coords) {
                let bounds = new LngLatBounds();
                coords.forEach((polys: any) => {
                    polys.forEach((coord: any) => {
                        bounds.extend(coord);
                    });
                });

                this.map?.fitBounds(bounds, {
                    padding: 20
                });
            }
        } else if (geometryType === "LINE" || geometryType === "MULTILINE") {
            let coords = geojson.coordinates;

            if (coords) {
                let bounds = new LngLatBounds();
                coords.forEach((lines: any) => {
                    lines.forEach((subline: any) => {
                        subline.forEach((coord: any) => {
                            bounds.extend(coord);
                        });
                    });
                });

                this.map?.fitBounds(bounds, {
                    padding: 20
                });
            }
        } else if (geometryType === "MULTILINESTRING") {
            let coords = geojson.coordinates;

            if (coords) {
                let bounds = new LngLatBounds();
                coords.forEach((lines: any) => {
                    lines.forEach((lngLat: any) => {
                        bounds.extend(lngLat);
                    });
                });

                this.map?.fitBounds(bounds, {
                    padding: 20
                });
            }
        }
    }

    drop(event: CdkDragDrop<string[]>) {
        moveItemInArray(this.orderedTypes, event.previousIndex, event.currentIndex);

        for (let i = 0; i < this.orderedTypes.length; ++i) {
            this.map?.moveLayer(this.orderedTypes[i], i > 0 ? this.orderedTypes[i - 1] : undefined);
            this.map?.moveLayer(this.orderedTypes[i] + "-LABEL", i > 0 ? this.orderedTypes[i - 1] + "-LABEL" : undefined);
        }
    }

    /*
      async onFileChange(e: any) {
        const file:File = e.target.files[0];
     
        if (file != null)
        {
            this.loadRdf(file);
        }
      }
     
      async loadRdf(file: File) {
        this.loading = true;
     
        let text = await file.text();
        this.tripleStore = new Store();
     
        const parser = new Parser();
        parser.parse(text, (error, quad, prefixes) => {
            if (error)
            {
                console.log(error);
                this.importError = error.message;
                this.loading = false;
            }
            else if (quad) {
                this.tripleStore?.add(quad);
            }
            else {
                console.log("Successfully loaded " + this.tripleStore?.size + " quads into memory.");
                this.loading = false;
                this.modalRef?.hide();
            }
        });
      }
      */

    initializeMap() {
        const layer = this.baseLayers[0];

        const mapConfig: any = {
            container: this.mapElement?.nativeElement ?? "map",
            bounds: [[-125.0011, 24.9493], [-66.9326, 49.5904] ], // USA
            fitBoundsOptions: { padding: 100 },
            style: {
                version: 8,
                name: layer.name,
                metadata: {
                    "mapbox:autocomposite": true
                },
                sources: {
                    mapbox: {
                        'type': 'raster',
                        'tiles': [
                            environment.apiUrl + "api/mapbox/v4/mapbox.satellite/{z}/{x}/{y}@2x.jpg90"
                        ],
                        'tileSize': 512
                    }
                },
                glyphs: environment.apiUrl + "glyphs/{fontstack}/{range}.pbf",
                layers: [
                    {
                        id: layer.id,
                        type: "raster",
                        source: "mapbox",
                        'minzoom': 0,
                        'maxzoom': 22
                        // "source-layer": "mapbox_satellite_full"
                    }
                ]
            },
            attributionControl: false
        };

        mapConfig.logoPosition = "bottom-right";

        this.map = new Map(mapConfig);

        this.map!.on("load", () => {
            this.initMap();

            this.initialized = true;

            this.render();
            this.renderVectorLayers();
        });

        this.map.on('mousemove', this.highlightSelectedLayerOnMouseMove);
    }

    highlightSelectedLayerOnMouseMove = debounce((e: any) => {
        const features = this.getSortedFeature(e);
        const feature = features.find(f => f.properties['uri'] != null);

        if (feature) {
            const uri = feature.properties['uri'];
            const highlightedObject = this.getGeoObjectByUri(uri);
            this.store.dispatch(ExplorerActions.highlightGeoObject({ object: highlightedObject! }));
            this.map!.getCanvas().style.cursor = 'pointer';
        } else {
            // Reset if no valid feature is found
            if (this.highlightedObject) {
                this.store.dispatch(ExplorerActions.highlightGeoObject(null));
                this.map!.getCanvas().style.cursor = '';
            }
        }
    }, 5);

    getSortedFeature(e: any): MapGeoJSONFeature[] {
        const features = this.map!.queryRenderedFeatures(e.point);

        // Get the map's layer order
        const layerOrder = this.map!.getStyle().layers!.map(layer => layer.id);

        // Sort features based on layer order, but push label layers to the bottom
        features.sort((a, b) => {
            const aIsLabel = a.layer.id.endsWith('-LABEL');
            const bIsLabel = b.layer.id.endsWith('-LABEL');

            if (aIsLabel && !bIsLabel) return 1;  // Move labels down
            if (!aIsLabel && bIsLabel) return -1; // Move non-labels up

            // Otherwise, sort by layer order (higher index = top-most)
            return layerOrder.indexOf(b.layer.id) - layerOrder.indexOf(a.layer.id);
        });

        return features;
    }



    initMap(): void {
        // Add zoom and rotation controls to the map.
        this.map!.addControl(new AttributionControl({ compact: true }), "bottom-right");
        this.map!.addControl(new NavigationControl({ visualizePitch: true }), "bottom-right");

        this.map!.on('click', (e) => {
            this.handleMapClickEvent(e);
        });
    }

    handleMapClickEvent(e: any): void {
        this.vectorLayers$.pipe(take(1)).subscribe(vectorLayers => {

            // Clear the feature state of all vector layers
            vectorLayers.forEach(layer => {
                if (layer.enabled) {
                    this.map!.removeFeatureState({ source: layer.id, sourceLayer: layer.sourceLayer });
                }
            })

            const features = this.getSortedFeature(e);

            if (features.length > 0) {
                const feature = features[0];

                const source = this.map!.getSource(feature.source);

                // Get the layer definition
                if (source?.type === 'vector') {

                    const layer = vectorLayers.find(l => l.id === feature.source);

                    if (layer != null) {
                        const uri = layer.prefix + feature.properties[layer.codeProperty];

                        this.explorerService.getAttributes(uri, true)
                            .then(geoObject => {
                                this.map!.setFeatureState({ source: layer.id, sourceLayer: layer.sourceLayer, id: feature.properties[layer.codeProperty] }, { selected: true });
                                this.openFeaturePopup(geoObject, e.lngLat);
                            })
                            .catch(error => this.errorService.handleError(error))
                    }
                }
                else {
                    // Take the highest non-label feature
                    const feature = features.find(f => f.properties['uri'] != null);
                    const uri = feature?.properties["uri"];

                    let selectedObject = this.getGeoObjectByUri(uri);

                    if (selectedObject) {
                        this.explorerService.getAttributes(selectedObject.properties.uri, true)
                            .then(geoObject => this.openFeaturePopup(geoObject, e.lngLat))
                            .catch(error => this.errorService.handleError(error));
                    }
                }
            } else {
                this.featurePopup?.remove();
                this.featurePopup = undefined;
                // this.store.dispatch(ExplorerActions.selectGeoObject(null));
            }
        })
    }

    private openFeaturePopup(geoObject: GeoObject, lngLat: LngLat): void {
        if (!this.map) {
            return;
        }

        this.featurePopup?.remove();

        const content = this.buildFeaturePopupContent(geoObject);
        this.featurePopup = new Popup({
            closeButton: true,
            closeOnClick: true,
            maxWidth: '420px',
            className: 'map-feature-popup'
        })
            .setLngLat(lngLat)
            .setDOMContent(content)
            .addTo(this.map);
    }

    private buildFeaturePopupContent(geoObject: GeoObject): HTMLElement {
        const content = document.createElement('div');
        content.className = 'map-feature-popup-content';

        const title = document.createElement('h3');
        title.className = 'map-feature-popup-title';
        title.textContent = geoObject.properties.label ?? geoObject.properties.code ?? 'Map feature';
        content.appendChild(title);

        const tableWrap = document.createElement('div');
        tableWrap.className = 'map-feature-popup-attributes';

        const table = document.createElement('table');

        Object.entries(geoObject.properties)
            .filter(([, value]) => value == null || typeof value !== 'object')
            .forEach(([key, value]) => {
                const row = document.createElement('tr');

                const keyCell = document.createElement('th');
                keyCell.scope = 'row';
                keyCell.textContent = key;

                const valueCell = document.createElement('td');
                valueCell.textContent = String(value ?? '');

                row.appendChild(keyCell);
                row.appendChild(valueCell);
                table.appendChild(row);
            });

        tableWrap.appendChild(table);
        content.appendChild(tableWrap);

        const actions = document.createElement('div');
        actions.className = 'map-feature-popup-actions';

        const inspectButton = document.createElement('button');
        inspectButton.type = 'button';
        inspectButton.className = 'map-feature-popup-inspect';
        inspectButton.textContent = 'Inspect';
        inspectButton.addEventListener('click', () => this.inspectMapPopupObject(geoObject));

        actions.appendChild(inspectButton);
        content.appendChild(actions);

        return content;
    }

    private inspectMapPopupObject(geoObject: GeoObject): void {
        this.featurePopup?.remove();
        this.featurePopup = undefined;

        this.store.dispatch(ExplorerActions.appendWorkflowStep({
            step: WorkflowStep.InspectObject,
            data: geoObject,
            zoomMap: false
        }));
    }

    renderHighlights() {
        if (!this.map) {
            return;
        }

        if (this.selectedObject != null) {
            const index = this.orderedTypes.findIndex(t => t === this.selectedObject!.properties.type);

            if (index !== -1) {
                this.map!.setFeatureState({ source: this.selectedObject.properties.type, id: this.selectedObject.id }, { selected: true });
            }
        }
    }

    highlightObject(uri?: string) {
        if (uri != null && this.selectedObject != null && uri == this.selectedObject.properties.uri) return;

        let oldHighlight = this.highlightedObject;
        let newHighlight = this.getGeoObjectByUri(uri) ?? null;

        if (this.map && oldHighlight != null) {
            this.map!.setFilter("hover-" + oldHighlight.properties.type, ["all", ["==", "uri", "NONE"] ]);
        }

        if (this.map && newHighlight != null) {
            this.map!.setFilter("hover-" + newHighlight.properties.type, ["all", ["==", "uri", newHighlight.id] ]);
        }

        this.highlightedObject = newHighlight;
    }

    selectObject(geoObject: GeoObject | null, zoomTo = false): void {

        let previousSelected = this.selectedObject;

        if (geoObject != null) {
            // If its already selected do nothing
            if (this.selectedObject != null && this.selectedObject.properties.uri === geoObject.properties.uri) return;

            let go = this.getGeoObjectByUri(geoObject.properties.uri);

            this.selectedObject = geoObject;
            this.updateGeoObjectIndex();

            if (go == null) {
                this.render();
            }

            this.highlightObject();

            // The geo object does exist on the map
            // if (go != null) {
            if (this.shouldShowMapForWorkflow(this.workflowStep)) {
                if (zoomTo) {
                    this.runWhenMapReady(() => {
                        this.render();
                        this.zoomTo(geoObject!.properties.uri);
                        this.renderHighlights();
                    });
                } else {
                    this.runWhenMapReady(() => {
                        this.render();
                        this.renderHighlights();
                    });
                }
            }
            // }
        } else {
            this.selectedObject = undefined;
            this.updateGeoObjectIndex();
        }

        if (this.map && previousSelected != null) {
            this.map!.setFeatureState({ source: previousSelected.properties.type, id: previousSelected.id }, { selected: false });
        }
    }

    toggleVectorLayer(layer: VectorLayer): void {
        const newLayer = { ...layer };
        newLayer.enabled = !newLayer.enabled

        this.store.dispatch(ExplorerActions.setVectorLayer({ layer: newLayer }));
    }
}
