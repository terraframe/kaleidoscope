import { AfterViewInit, Component, ElementRef, inject, Input, OnDestroy, ViewChild } from '@angular/core';
import { ExplorerComponent, TypeLegend } from '../explorer/explorer.component';
import { CommonModule } from '@angular/common';
import { Edge, Node, GraphComponent, GraphModule, NgxGraphStates, NgxGraphStateChangeEvent } from '@swimlane/ngx-graph';
import { SELECTED_COLOR } from '../explorer/defaultQueries';
import { ExplorerService } from '../service/explorer.service';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
// @ts-ignore
import ColorGen from "color-generator";
import { GeoObject } from '../models/geoobject.model';
import { Store } from '@ngrx/store';
import { ExplorerActions, getSelectedObject, getStyles, getWorkflowState, getZoomMap, highlightedObject, WorkflowState, WorkflowStep } from '../state/explorer.state';
import { distinctUntilChanged, Observable, Subscription } from 'rxjs';
import { CheckboxModule } from 'primeng/checkbox';
import { FormsModule } from '@angular/forms';
import { StyleConfig } from '../models/style.model';


// export interface Relationship {
//   oid: string,
//   label: string,
//   layout: "VERTICAL" | "HORIZONTAL",
//   code: string,
//   type?: string
// }

// export interface Vertex {
//   label: string;
//   uri: string;
//   typeUri: string;
// }

// export interface Edge {
//   typeUri: string,
//   sourceUri: string,
//   targetUri: string
// }

// export interface RelatedType {
//   code: string,
//   label: string,
//   objectType: "BUSINESS" | "GEOOBJECT"
// }

export interface GprGraph {
  typeCount: { [key: string]: number },
  nodes: GeoObject[],
  edges: { source: string, target: string, type: string }[]
}

export interface TreeData {
  edges: Edge[],
  nodes: Node[],
  // relatedTypes: RelatedType[]
}

export const DRAW_SCALE_MULTIPLIER: number = 1.0;

export const GRAPH_GO_LABEL_COLOR: string = "black";
export const GRAPH_CIRCLE_FILL: string = "#999";
export const GRAPH_LINE_COLOR: string = "#999";

export const COLLAPSE_ANIMATION_TIME: number = 500; // in ms

export const DIMENSIONS = {
  NODE: { WIDTH: 30, HEIGHT: 30 },
  LABEL: { WIDTH: 100, HEIGHT: 60, FONTSIZE: 14 },
  PADDING: {
    BETWEEN_NODES: 0,
    NODE_LABEL: 5,
    NODE_EDGE: 5
  }
};

@Component({
  selector: 'graph-explorer',
  imports: [CommonModule, GraphModule, ProgressSpinnerModule, CheckboxModule, FormsModule],
  providers: [],
  templateUrl: './graph-explorer.component.html',
  styleUrl: './graph-explorer.component.scss'
})
export class GraphExplorerComponent implements AfterViewInit, OnDestroy {

  @Input() explorer!: ExplorerComponent;

  @ViewChild("graph") graph!: GraphComponent;

  @ViewChild("graphContainer") set graphContainerRef(ref: ElementRef<HTMLElement> | undefined) {
    this.graphContainer = ref;
    this.observeGraphContainer();
    this.scheduleResizeRefresh();
  }

  private store = inject(Store);

  private HASH_TAG_REPLACEMENT = "-!`~`!-";

  public DIMENSIONS = DIMENSIONS;

  public SELECTED_NODE_COLOR = SELECTED_COLOR;

  public loading: boolean = true;

  public svgHeight: number | null = null;
  public svgWidth: number | null = null;
  public graphView: [number, number] = [0, 0];

  // public geoObjects?: GeoObject[];
  public data?: TreeData;

  public relationship: any = { layout: "HORIZONTAL" }

  private extraColors: any = {};

  private gprGraph?: GprGraph;

  zoomMap$: Observable<boolean> = this.store.select(getZoomMap);

  styles$: Observable<StyleConfig> = this.store.select(getStyles);

  onStylesChange: Subscription;

  highlightedObject$: Observable<GeoObject | null> = this.store.select(highlightedObject);

  onHighlightedObjectChange: Subscription;

  workflowState$: Observable<WorkflowState> = this.store.select(getWorkflowState);
      
  onWorkflowStateChange: Subscription;

  selectedObject$: Observable<GeoObject | null> = this.store.select(getSelectedObject);

  onSelectedObjectChange: Subscription;

  private selectedObject: GeoObject | null = null;

  private highlightedObject: GeoObject | null = null;

  public typeLegend: TypeLegend = {};

  private resolvedStyles: StyleConfig = {};

  private graphContainer?: ElementRef<HTMLElement>;
  private resizeObserver?: ResizeObserver;
  private resizeRefreshFrame = 0;

  constructor(
    private queryService: ExplorerService
  ) {

    this.onHighlightedObjectChange = this.highlightedObject$.subscribe(object => {
      if (object) {
        this.highlightedObject = object;
      }
    });

    this.onStylesChange = this.styles$.subscribe(styles => {
      this.resolvedStyles = styles;
    });

    this.onSelectedObjectChange = this.selectedObject$
      .pipe(distinctUntilChanged((a, b) => a?.properties.uri === b?.properties.uri))
      .subscribe(object => {
        if (object) {
          this.renderGeoObjectAndNeighbors(object);
        } else {
          this.store.dispatch(ExplorerActions.setNeighbors({ objects: [], zoomMap: false }));
        }
      });

    this.onWorkflowStateChange = this.workflowState$
        .pipe(
            distinctUntilChanged((a, b) =>
            a.step === b.step &&
            a.data === b.data
            )
        )
        .subscribe(({ step, data }) => {
          if (this.gprGraph != null && step === WorkflowStep.ViewNeighbors) {
            let that = this;
            window.setTimeout(() => {
              if (this.gprGraph != null && step === WorkflowStep.ViewNeighbors)
                this.renderGraph(that.gprGraph!, false)
            }, 100);
          } else if (step !== WorkflowStep.MapAndResults &&
            step !== WorkflowStep.MinimizeChat &&
            step !== WorkflowStep.InspectObject) {
            // Hmm.. seems kinda sketch
            this.store.dispatch(ExplorerActions.setNeighbors({ objects: [], zoomMap: false }));
          }
        });
  }

  ngOnDestroy(): void {
    this.store.dispatch(ExplorerActions.setNeighbors({ objects: [], zoomMap: false }));

    this.resizeObserver?.disconnect();

    if (this.resizeRefreshFrame > 0) {
      cancelAnimationFrame(this.resizeRefreshFrame);
    }

    this.onHighlightedObjectChange.unsubscribe();
    this.onStylesChange.unsubscribe();
    this.onWorkflowStateChange.unsubscribe();
    this.onSelectedObjectChange.unsubscribe();
  }

  ngAfterViewInit(): void {
    this.observeGraphContainer();
    this.scheduleResizeRefresh();
  }

  public renderGeoObjectAndNeighbors(geoObject: GeoObject, forceRefresh: boolean = false) {
    if (this.selectedObject != null && (this.selectedObject.properties.uri === geoObject.properties.uri && !forceRefresh)) return;

    this.loading = true;

    this.selectedObject = geoObject;

    // let sparql = defaultQueries[2].sparql.replace("{{uri}}", geoObject.properties.uri);
    // const result: SPARQLResultSet = await this.queryService.query(sparql);
    // this.geoObjects = this.queryService.convert(result);

    // if (this.geoObjects.length === 0) {
    //   console.error('The query did not return any results!');
    //   return;
    // }

    // this.renderGeoObjects(explorer, this.geoObjects);

    const excludedTypes = Object.entries(this.getTypeLegend())
      .filter(([_, value]) => !value.included)
      .map(([key, _]) => key);

    let graph = this.queryService.neighborQuery(geoObject.properties.uri, excludedTypes).then((graph) => {
      this.renderGraph(graph, true);

      // setTimeout(() => { this.zoomToUri(geoObject.properties.uri); }, 500);
    }).finally(() => { this.loading = false; });
  }

  public renderGraph(graph: GprGraph | null, zoom: boolean = false) {
    if (graph == null) return;

    this.loading = true;
    this.store.dispatch(ExplorerActions.setNeighbors({ objects: graph.nodes, zoomMap: false }));
    this.gprGraph = graph;

    this.calculateTypeLegend();

    let data: any = {
      edges: [],
      nodes: []
    };

    graph.nodes.forEach(go => {
      if (!data.nodes.some((n:any) => this.idToUri(n.id) === go.properties.uri)) {
        let node = {
          label: (go.properties.label != null && go.properties.label !== "")
            ? go.properties.label
            : go.properties.uri.substring(go.properties.uri.lastIndexOf("#") + 1),
          id: this.uriToId(go.properties.uri),
          relation: graph.edges.some(edge => edge.source === go.properties.uri) ? "PARENT" : "CHILD",
          type: go.properties.type
        };
        data.nodes.push(node);
      }
    });

    graph.edges.forEach(edge => {
      let formattedEdge = {
        id: this.uriToId(Math.random().toString(16).slice(2)),
        source: this.uriToId(edge.source),
        target: this.uriToId(edge.target),
        label: ExplorerComponent.uriToLabel(edge.type)
      };
      data.edges.push(formattedEdge);
    });

    // window.setTimeout(() => {
      this.data = data;
      // this.resizeDimensions();
      // this.calculateTypeLegend(this.data.relatedTypes);
      // this.addLayers(this.data.relatedTypes);
    // }, 100);

    this.resizeDimensions();

    if (zoom)
      window.setTimeout(() => {
        try {
          this.zoomToUri(this.selectedObject!.properties.uri);
        } finally {
          window.setTimeout(() => { this.loading = false; }, 5);
        }
      }, 1000); // We're very much guessing how long the graph can take to render here. On very slow computers it's possible this might not be long enough. Unfortunately ngx-graph does not provide a callback to listen to when the graph is finished.
    else
      this.loading = false;
  }

  calculateTypeLegend() {
    var oldTypeLegend = JSON.parse(JSON.stringify(this.typeLegend));
    this.typeLegend = {};

    Object.entries(this.gprGraph!.typeCount).forEach(kv => {
        const style = this.resolvedStyles[kv[0]];
        this.typeLegend[kv[0]] = {
            label: style?.label ?? ExplorerComponent.uriToLabel(kv[0]),
            color: style?.color ?? ColorGen().hexString(),
            visible: (oldTypeLegend[kv[0]] == null ? true : oldTypeLegend[kv[0]].visible),
            included: (oldTypeLegend[kv[0]] == null ? true : oldTypeLegend[kv[0]].included)
        }
    });
  }

  public getTypeLegend() {
    return this.typeLegend;
  }

  public legendCheckboxChange(e: any) {
    this.renderGeoObjectAndNeighbors(this.selectedObject!, true);
  }

  public getColor(node: any) {
    if (this.highlightedObject != null && this.highlightedObject.properties.uri === this.idToUri(node.id))
      return SELECTED_COLOR;

    let legend = this.getTypeLegend();

    if (legend[node.type] != null)
      return legend[node.type].color;
    else if (this.extraColors[node.type] != null)
      return this.extraColors[node.type];
    else {
      this.extraColors[node.type] = ColorGen().hexString();
      return this.extraColors[node.type];
    }
  }

  public getSelectedId() {
    if (this.selectedObject == null) return null;

    return this.uriToId(this.selectedObject.properties.uri);
  }

  uriToId(uri: string): string {
    return "a" + uri;
  }

  idToUri(id: string): string {
    return id.substring(1);
  }

  resizeDimensions(): void {
    const graphContainer = this.graphContainer?.nativeElement;

    if (graphContainer) {
      this.svgHeight = Math.max(0, graphContainer.clientHeight - 50);
      this.svgWidth = graphContainer.clientWidth;
      this.graphView = [this.svgWidth, this.svgHeight];
    }
  }

  public scheduleResizeRefresh(): void {
    if (this.resizeRefreshFrame > 0) {
      cancelAnimationFrame(this.resizeRefreshFrame);
    }

    this.resizeRefreshFrame = requestAnimationFrame(() => {
      this.resizeRefreshFrame = 0;
      this.refreshAfterResize();
    });
  }

  private refreshAfterResize(): void {
    this.resizeDimensions();

    if (!this.graph || !this.data) {
      return;
    }

    this.graph.update();
    this.graph.updateGraphDims();

    requestAnimationFrame(() => {
      if (!this.graph) {
        return;
      }

      this.graph.updateGraphDims();
      this.enforceBounds();
    });
  }

  private observeGraphContainer(): void {
    this.resizeObserver?.disconnect();

    const graphContainer = this.graphContainer?.nativeElement;

    if (!graphContainer) {
      return;
    }

    this.resizeObserver = new ResizeObserver(() => this.scheduleResizeRefresh());
    this.resizeObserver.observe(graphContainer);
  }

  // Thanks to https://stackoverflow.com/questions/52172067/create-svg-hexagon-points-with-only-only-a-length
  public getHexagonPoints(node: { dimension: { width: number, height: number }, relation: string }): string {
    let y = (this.DIMENSIONS.LABEL.HEIGHT / 2) - this.DIMENSIONS.NODE.HEIGHT / 2;
    let x = (this.relationship.layout === "VERTICAL")
      ? (node.relation === "CHILD" ? (this.DIMENSIONS.LABEL.WIDTH / 2) - this.DIMENSIONS.NODE.WIDTH / 2 : (this.DIMENSIONS.LABEL.WIDTH + DIMENSIONS.PADDING.NODE_LABEL + this.DIMENSIONS.NODE.WIDTH) / 2 - this.DIMENSIONS.NODE.WIDTH / 2)
      : node.relation === "PARENT" ? (this.DIMENSIONS.LABEL.WIDTH + this.DIMENSIONS.PADDING.NODE_LABEL + this.DIMENSIONS.PADDING.NODE_EDGE) : 0;

    let radius = this.DIMENSIONS.NODE.WIDTH / 2;
    let height = this.DIMENSIONS.NODE.HEIGHT;
    let width = this.DIMENSIONS.NODE.WIDTH;

    let points = [0, 1, 2, 3, 4, 5, 6].map((n, i) => {
      let angleDeg = 60 * i - 30;
      let angleRad = Math.PI / 180 * angleDeg;
      return [(width / 2 + radius * Math.cos(angleRad)) + x, (height / 2 + radius * Math.sin(angleRad)) + y];
    }).map((p) => p.join(","))
      .join(" ");

    return points;
  }

  public onClickNode(node: any) {
    let selectedObject = this.gprGraph!.nodes.find(n => n.properties.uri === this.idToUri(node.id));

    if (selectedObject) {
      this.store.dispatch(ExplorerActions.appendWorkflowStep({
        step: WorkflowStep.InspectObject,
        data: selectedObject,
        zoomMap: true
      }));
      this.renderGeoObjectAndNeighbors(selectedObject);
    }
  }

  public zoomToUri(uri: string) {
    if (!this.graph) {
      return;
    }

    const desiredZoomLevel = 0.6;

    this.graph.zoom(desiredZoomLevel / this.graph.zoomLevel);

    this.graph.panToNodeId(this.uriToId(uri));
  }

  readonly PAN_LIMIT = 500;
  private ignoreRecursive: boolean = false;

  private zoomJustChanged = false;

  // @HostListener('window:scroll', [])
  // onWindowScroll(): void {
  // onZoomChanged(newZoom: number) {
  //   this.zoomJustChanged = true;
  //   console.log("Zoom changed");
  
  //   setTimeout(() => {
  //     this.zoomJustChanged = false;
  //   }, 100);
  // }

  onGraphStateChange(event: NgxGraphStateChangeEvent) {
    if (!this.ignoreRecursive && this.graph && event.state === NgxGraphStates.Transform) {
      // if we’re coming out of a zoom, defer until the next frame…
      
      if (!this.graph.isPanning) {
        setTimeout(() => this.enforceBounds(),0);
      }
      // …otherwise (i.e. on a pan) run it *right now*
      else {
        this.enforceBounds();
        // setTimeout(() => this.enforceBounds(),0);
      }



      // setTimeout(() => this.enforceBounds(),0);
      // this.enforceBounds()
    }
  }
  
  private enforceBounds() {
    this.graph.updateGraphDims();
    const zoom = this.graph.zoomLevel;
    const viewW = this.graph.dims.width;
    const viewH = this.graph.dims.height;
  
    const graphW = this.graph.graphDims.width * zoom;
    const graphH = this.graph.graphDims.height * zoom;
  
    const centerX = graphW <= viewW;
    const centerY = graphH <= viewH;
  
    let clampedX = this.graph.panOffsetX;
    let clampedY = this.graph.panOffsetY;
  
    if (!centerX) {
      clampedX = this.clamp(clampedX, viewW - graphW, 0);
    }
    if (!centerY) {
      clampedY = this.clamp(clampedY, viewH - graphH, 0);
    }
  
    if (clampedX !== this.graph.panOffsetX || clampedY !== this.graph.panOffsetY || centerX || centerY) {
      this.ignoreRecursive = true;
  
      const worldX = centerX
        ? this.graph.graphDims.width / 2
        : (viewW / 2 - clampedX) / zoom;
      const worldY = centerY
        ? this.graph.graphDims.height / 2
        : (viewH / 2 - clampedY) / zoom;
  
      this.graph.panTo(worldX, worldY);
      this.ignoreRecursive = false;
      this.graph.updateGraphDims();
    }
  }

  clamp(val: number, min: number, max: number): number {
    return Math.max(min, Math.min(max, val));
  }
  
}
