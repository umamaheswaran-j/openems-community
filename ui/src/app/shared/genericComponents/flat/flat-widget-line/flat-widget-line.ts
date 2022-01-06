import { Component, Input } from "@angular/core";
import { AbstractFlatWidgetLine } from "../abstract-flat-widget-line";

@Component({
    selector: 'oe-flat-widget-line',
    templateUrl: './flat-widget-line.html'
})
export class FlatWidgetLine extends AbstractFlatWidgetLine {

    /** Name for parameter, displayed on the left side */
    @Input()
    name: string;

    /** Width of left Column, right Column is (100 - width of left Column) */
    @Input()
    leftColumnWidth: number;

}