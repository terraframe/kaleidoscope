import { StyleConfig } from "./style.model";
import { VectorLayer } from "./vector-layer.model";
import { ReportTemplate } from "./report.model";

export interface Configuration {
    styles: StyleConfig;
    layers: VectorLayer[];
    reportTemplates?: ReportTemplate[];
}

