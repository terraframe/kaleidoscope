import {
  createActionGroup,
  props,
  emptyProps,
  createReducer,
  on,
  createFeatureSelector,
  createSelector,
} from '@ngrx/store';
import ColorGen from '../util/color-generator';

import { GeoObject } from '../models/geoobject.model';
import { Style, StyleConfig } from '../models/style.model';
import { VectorLayer } from '../models/vector-layer.model';
import { Configuration } from '../models/configuration.model';
import { LocationPage } from '../models/chat.model';
import { defaultStyles } from '../explorer/defaultQueries';
import { ReportTemplate } from '../models/report.model';

export const ExplorerActions = createActionGroup({
  source: 'explorer',
  events: {
    'Add GeoObject': props<{ object: GeoObject }>(),
    'Set Pages': props<{ pages: LocationPage[]; zoomMap: boolean }>(),
    'Add Neighbor': props<{ object: GeoObject }>(),
    'Set Neighbors': props<{ objects: GeoObject[]; zoomMap: boolean }>(),
    'Highlight GeoObject': props<{ object: GeoObject } | null>(),
    'Add Style': props<{ typeUri: string; style: Style }>(),
    'Set Styles': props<{ styles: StyleConfig }>(),
    'Set Vector Layer': props<{ layer: VectorLayer }>(),
    'Set Configuration': props<Configuration>(),
    'Set Workflow Step': props<{ step: WorkflowStep; data?: any; zoomMap?: boolean }>(),
    'Append Workflow Step': props<{ step: WorkflowStep; data?: any; zoomMap?: boolean }>(),
    'Back Workflow Step': emptyProps(),
    'Clear Workflow History': emptyProps(),
    'Show Pages On Map': props<{
      pages: LocationPage[];
      zoomMap: boolean;
      step: WorkflowStep.MapAndResults | WorkflowStep.DisambiguateObject;
      data?: any;
    }>(),
  },
});

export interface WorkflowState {
  step: WorkflowStep;
  data?: any;
  zoomMap?: boolean;
}

export enum WorkflowStep {
  FullScreenChat = 'FullScreenChat',
  AiChatAndResults = 'AiChatAndResults',
  MapAndResults = 'MapAndResults',
  DisambiguateObject = 'DisambiguateObject',
  ViewNeighbors = 'ViewNeighbors',
  InspectObject = 'InspectObject',
  MinimizeChat = 'MinimizeChat',
}

export interface ExplorerStateModel {
  neighbors: GeoObject[];
  styles: StyleConfig;
  selectedObject: GeoObject | null;
  highlightedObject: GeoObject | null;
  zoomMap: boolean;
  vectorLayers: VectorLayer[];
  reportTemplates: ReportTemplate[];
  pages: LocationPage[];
  workflowStep: WorkflowStep;
  workflowData?: any;
  workflowHistory: WorkflowState[];
}

export const initialState: ExplorerStateModel = {
  neighbors: [],
  styles: {},
  selectedObject: null,
  zoomMap: false,
  highlightedObject: null,
  vectorLayers: [],
  reportTemplates: [],
  workflowStep: WorkflowStep.FullScreenChat,
  workflowHistory: [],
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
};

// Helper function for resolving missing styles based on the provided object types
const resolveMissingStyles = (styles: StyleConfig, objects: GeoObject[]): StyleConfig => {
  const types: string[] = objects
    .map((o) => o.properties.type)
    .filter((t) => t != null)
    .reduce((acc: string[], t: string) => {
      if (!acc.includes(t)) {
        acc.push(t);
      }
      return acc;
    }, [])
    .filter((type) => styles[type] == null);

  if (types.length === 0) {
    return styles;
  }

  const newStyles = { ...styles };

  types.forEach((type) => {
    const defaultStyle = defaultStyles[type];

    newStyles[type] = {
      order: defaultStyle?.order ?? 10,
      color: ColorGen().hexString(),
    };
  });

  return newStyles;
};

const resolveMissingStylesPages = (styles: StyleConfig, pages: LocationPage[]): StyleConfig => {
  return pages.reduce((resolvedStyles, page) => resolveMissingStyles(resolvedStyles, page.locations ?? []), styles);
};

export const explorerReducer = createReducer(
  initialState,

  // Set all neighbors
  on(ExplorerActions.setNeighbors, (state, { objects, zoomMap }) => {
    const styles = resolveMissingStyles(state.styles, objects);

    return {
      ...state,
      styles,
      neighbors: objects,
      zoomMap: zoomMap,
    };
  }),

  // Set all geo objects
  on(ExplorerActions.setPages, (state, { pages, zoomMap }) => {
    const styles = resolveMissingStylesPages(state.styles, pages);

    return {
      ...state,
      styles,
      pages,
      zoomMap,
    };
  }),

  // Highlight geo object
  on(ExplorerActions.highlightGeoObject, (state, { object }) => ({
    ...state,
    highlightedObject: object,
  })),

  // Add Neighbor
  on(ExplorerActions.addNeighbor, (state, { object }) => ({
    ...state,
    neighbors: [...state.neighbors, object],
  })),

  // Add geo object
  // on(ExplorerActions.addGeoObject, (state, { object }) => ({
  //     ...state,
  //     pages: {
  //         ...state.page,
  //         locations: [...state.page.locations, object]
  //     }
  // })),

  // Add style
  on(ExplorerActions.addStyle, (state, { typeUri, style }) => ({
    ...state,
    styles: { ...state.styles, [typeUri]: style },
  })),

  // Set the vector layers & styles
  on(ExplorerActions.setConfiguration, (state, configuration) => {
    return {
      ...state,
      vectorLayers: configuration.layers,
      styles: configuration.styles,
      reportTemplates: configuration.reportTemplates ?? [],
    };
  }),

  // Set the vector layer
  on(ExplorerActions.setVectorLayer, (state, { layer }) => {
    const vectorLayers = [...state.vectorLayers];
    const index = vectorLayers.findIndex((v) => v.id === layer.id);

    if (index !== -1) {
      vectorLayers[index] = layer;
    } else {
      vectorLayers.push(layer);
    }

    return { ...state, vectorLayers };
  }),

  // Set styles
  on(ExplorerActions.setStyles, (state, { styles }) => ({
    ...state,
    styles: styles,
  })),

  // Forcibly sets the workflow step, clearing all history
  on(ExplorerActions.setWorkflowStep, (state, { step, data, zoomMap }) => {
    let styles = state.styles;
    if (step === WorkflowStep.InspectObject && data) styles = resolveMissingStyles(state.styles, [data]);

    return {
      ...state,
      styles,
      zoomMap: zoomMap ?? state.zoomMap,
      workflowStep: step,
      workflowData: data,
      workflowHistory: [],
      selectedObject: step === WorkflowStep.InspectObject && data ? data : null,
      neighbors: step === WorkflowStep.InspectObject ? state.neighbors : [],
    };
  }),

  // Append Workflow Step
  on(ExplorerActions.appendWorkflowStep, (state, { step, data, zoomMap }) => {
    const current: WorkflowState = {
      step: state.workflowStep,
      data: state.workflowData,
      zoomMap: state.zoomMap,
    };

    let styles = state.styles;
    if (step === WorkflowStep.InspectObject && data) styles = resolveMissingStyles(state.styles, [data]);

    return {
      ...state,
      styles,
      zoomMap: zoomMap ?? state.zoomMap,
      workflowStep: step,
      workflowData: data,
      workflowHistory: [...state.workflowHistory, current],
      selectedObject: step === WorkflowStep.InspectObject && data ? data : null,
    };
  }),

  // Back Workflow Step
  on(ExplorerActions.backWorkflowStep, (state) => {
    if (state.workflowHistory.length === 0) {
      return {
        ...state,
        workflowStep: WorkflowStep.FullScreenChat,
        workflowData: undefined,
      };
    }

    const workflowHistory = [...state.workflowHistory];
    const previous = workflowHistory.pop()!;

    return {
      ...state,
      zoomMap: previous.zoomMap ?? false,
      workflowStep: previous.step,
      workflowData: previous.data,
      workflowHistory,
      selectedObject: previous.step === WorkflowStep.InspectObject && previous.data ? previous.data : null,
    };
  }),

  // Clear Workflow History
  on(ExplorerActions.clearWorkflowHistory, (state) => ({
    ...state,
    workflowHistory: [],
  })),

  on(ExplorerActions.showPagesOnMap, (state, { pages, zoomMap, step, data }) => {
    const styles = resolveMissingStylesPages(state.styles, pages);

    return {
      ...state,
      styles: styles != null ? styles : state.styles,
      pages,
      zoomMap,
      workflowStep: step,
      workflowData: data,
      workflowHistory: [],
      selectedObject: null,
    };
  })
);

const selector = createFeatureSelector<ExplorerStateModel>('explorer');

export const getPages = createSelector(selector, (s) => {
  return s.pages;
});

export const getObjects = createSelector(getPages, (pages): GeoObject[] => {
  return pages.flatMap((page) => page.locations ?? []);
});

export const getNeighbors = createSelector(selector, (s) => {
  return s.neighbors;
});

export const getZoomMap = createSelector(selector, (s) => {
  return s.zoomMap;
});

export const getStyles = createSelector(selector, (s) => {
  return s.styles;
});

export const getReportTemplates = createSelector(selector, (s) => {
  return s.reportTemplates;
});

export const highlightedObject = createSelector(selector, (s) => {
  return s.highlightedObject;
});

export const getSelectedObject = createSelector(selector, (s) => {
  return s.selectedObject;
});

export const getVectorLayers = createSelector(selector, (s) => {
  return s.vectorLayers;
});

export const getWorkflowStep = createSelector(selector, (s) => s.workflowStep);

export const getWorkflowData = createSelector(selector, (s) => s.workflowData);

export const getWorkflowState = createSelector(
  getWorkflowStep,
  getWorkflowData,
  (step, data): WorkflowState => ({
    step,
    data,
  })
);

export const getWorkflowHistory = createSelector(selector, (s) => s.workflowHistory);

export const getPreviousWorkflowState = createSelector(selector, (s): WorkflowState | undefined => {
  return s.workflowHistory.length > 0 ? s.workflowHistory[s.workflowHistory.length - 1] : undefined;
});

export const getPreviousWorkflowStep = createSelector(selector, (s): WorkflowStep | undefined => {
  return s.workflowHistory.length > 0 ? s.workflowHistory[s.workflowHistory.length - 1].step : undefined;
});

export const getPreviousWorkflowData = createSelector(selector, (s): any | undefined => {
  return s.workflowHistory.length > 0 ? s.workflowHistory[s.workflowHistory.length - 1].data : undefined;
});
