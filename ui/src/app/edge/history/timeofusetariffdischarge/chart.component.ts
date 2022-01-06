import { formatNumber } from '@angular/common';
import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { differenceInDays } from 'date-fns';
import { DefaultTypes } from 'src/app/shared/service/defaulttypes';
import { QueryHistoricTimeseriesDataResponse } from '../../../shared/jsonrpc/response/queryHistoricTimeseriesDataResponse';
import { ChannelAddress, Edge, EdgeConfig, Service } from '../../../shared/shared';
import { AbstractHistoryChart } from '../abstracthistorychart';
import { Data, TooltipItem } from '../shared';

@Component({
  selector: 'timeOfUseTariffDischargeChart',
  templateUrl: '../abstracthistorychart.html'
})
export class TimeOfUseTariffDischargeChartComponent extends AbstractHistoryChart implements OnInit, OnChanges {

  @Input() public period: DefaultTypes.HistoryPeriod;
  @Input() public componentId: string;

  ngOnChanges() {
    this.updateChart();
  };

  constructor(
    protected service: Service,
    protected translate: TranslateService,
    private route: ActivatedRoute,
  ) {
    super(service, translate);
  }

  ngOnInit() {
    this.spinnerId = "timeOfUseTariffDischarge-chart";
    this.service.startSpinner(this.spinnerId);
    this.service.setCurrentComponent('', this.route);
  }

  ngOnDestroy() {
    this.unsubscribeChartRefresh()
  }

  protected updateChart() {
    this.autoSubscribeChartRefresh();
    this.service.startSpinner(this.spinnerId);
    this.colors = [];
    this.loading = true;

    this.queryHistoricTimeseriesData(this.period.from, this.period.to, 900).then(response => {
      this.service.getConfig().then(config => {
        let result = (response as QueryHistoricTimeseriesDataResponse).result;

        // convert labels
        let labels: Date[] = [];
        for (let timestamp of result.timestamps) {
          // Only use full hours as a timestamp
          labels.push(new Date(timestamp));
        }
        this.labels = labels;

        // convert datasets
        let datasets = [];
        let quarterlyPrices = this.componentId + '/QuarterlyPrices';
        let TimeOfUseTariffState = this.componentId + '/StateMachine';
        // let predictedSocWithoutLogic = this.componentId + '/PredictedSocWithoutLogic';

        if (TimeOfUseTariffState in result.data && quarterlyPrices in result.data) {

          // Get only the 15 minute value
          let quarterlyPricesStandbyModeData = [];
          let quarterlyPricesNightData = [];
          let quarterlyPricesDelayedDischargeData = [];
          // let predictedSocWithoutLogicData = [];

          for (let i = 0; i < 96; i++) {
            let quarterlyPrice = this.formatPrice(result.data[quarterlyPrices][i]);
            let state = result.data[TimeOfUseTariffState][i];

            if (state == null) {
              quarterlyPricesDelayedDischargeData.push(null);
              quarterlyPricesNightData.push(null);
              quarterlyPricesStandbyModeData.push(null);
            } else {
              switch (state) {
                case 0:
                  // delayed
                  quarterlyPricesDelayedDischargeData.push(quarterlyPrice);
                  quarterlyPricesNightData.push(null);
                  quarterlyPricesStandbyModeData.push(null);
                  break;
                case 1:
                  // allowsDischarge
                  quarterlyPricesDelayedDischargeData.push(null);
                  quarterlyPricesNightData.push(quarterlyPrice)
                  quarterlyPricesStandbyModeData.push(null);
                  break;
                case -1:
                // notStarted
                case 2:
                  // standby
                  quarterlyPricesDelayedDischargeData.push(null);
                  quarterlyPricesNightData.push(null);
                  quarterlyPricesStandbyModeData.push(quarterlyPrice);
                  break;
              }
            }
          }


          // Set dataset for no limit
          datasets.push({
            type: 'bar',
            label: this.translate.instant('Edge.Index.Energymonitor.storageDischarge'),
            data: quarterlyPricesNightData,
            order: 3,
          });
          this.colors.push({
            // Dark Green
            backgroundColor: 'rgba(51,102,0,0.8)',
            borderColor: 'rgba(51,102,0,1)',
          })

          // Set dataset for buy from grid
          datasets.push({
            type: 'bar',
            label: this.translate.instant('General.gridBuy'),
            data: quarterlyPricesDelayedDischargeData,
            order: 4,
          });
          this.colors.push({
            // Black
            backgroundColor: 'rgba(0,0,0,0.8)',
            borderColor: 'rgba(0,0,0,0.9)',

          })

          // Set dataset for Quarterly Prices outside zone
          datasets.push({
            type: 'bar',
            label: this.translate.instant('Edge.Index.Widgets.TimeOfUseTariff.State.standby'),
            data: quarterlyPricesStandbyModeData,
            order: 3,
          });
          this.colors.push({
            // Dark Blue
            backgroundColor: 'rgba(0,0,200,0.7)',
            borderColor: 'rgba(0,0,200,0.9)',
          })

          // Predicted SoC is not shown for now, because it is not inteligent enough with the simple prediction
          // if (predictedSocWithoutLogic in result.data) {
          //   for (let i = 0; i < 96; i++) {
          //     let predictedSoc = result.data[predictedSocWithoutLogic][i];
          //     predictedSocWithoutLogicData.push(predictedSoc);
          //   }
          // }

          // let predictedSocLabel = "Predicted Soc without logic";
          // datasets.push({
          //   type: 'line',
          //   label: predictedSocLabel,
          //   data: predictedSocWithoutLogicData,
          //   hidden: false,
          //   yAxisID: 'yAxis2',
          //   position: 'right',
          //   borderDash: [10, 10],
          //   order: 2,
          // });
          // this.colors.push({
          //   backgroundColor: 'rgba(255,0,0,0.01)',
          //   borderColor: 'rgba(255,0,0,1)'
          // })
        }

        // State of charge data
        if ('_sum/EssSoc' in result.data) {
          let socData = result.data['_sum/EssSoc'].map(value => {
            if (value == null) {
              return null
            } else if (value > 100 || value < 0) {
              return null;
            } else {
              return value;
            }
          })
          datasets.push({
            type: 'line',
            label: this.translate.instant('General.soc'),
            data: socData,
            hidden: false,
            yAxisID: 'yAxis2',
            position: 'right',
            borderDash: [10, 10],
            order: 1,
          })
          this.colors.push({
            backgroundColor: 'rgba(189, 195, 199,0.2)',
            borderColor: 'rgba(189, 195, 199,1)',
          })
        }

        this.datasets = datasets;
        this.loading = false;
        this.service.stopSpinner(this.spinnerId);
      }).catch(reason => {
        console.error(reason); // TODO error message
        this.initializeChart();
        return;
      });
    }).catch(reason => {
      console.error(reason); // TODO error message
      this.initializeChart();
      return;
    });
  }

  /**
   * Converts a value in €/MWh to €/kWh.
   * 
   * @param price the price value
   * @returns  the converted price
   */
  private formatPrice(price: number): number {
    if (price == null || price == NaN) {
      return null;
    } else if (price == 0) {
      return 0;
    } else {
      price = (price / 10.0);
      return Math.round(price * 10000) / 10000.0;
    }
  }

  protected getChannelAddresses(edge: Edge, config: EdgeConfig): Promise<ChannelAddress[]> {
    return new Promise((resolve) => {
      resolve(
        [
          new ChannelAddress(this.componentId, 'Delayed'),
          new ChannelAddress(this.componentId, 'QuarterlyPrices'),
          new ChannelAddress(this.componentId, 'StateMachine'),
          new ChannelAddress('_sum', 'EssSoc'),
          // new ChannelAddress(this.componentId, 'PredictedSocWithoutLogic'),
        ]);
    });
  }

  protected setLabel(config: EdgeConfig) {
    let options = this.createDefaultChartOptions();
    let translate = this.translate;

    console.log('options: ', options);

    // Adds second y-axis to chart
    options.scales.yAxes.push({
      id: 'yAxis2',
      position: 'right',
      scaleLabel: {
        display: true,
        labelString: "%",
        padding: -2,
        fontSize: 11
      },
      gridLines: {
        display: false
      },
      ticks: {
        beginAtZero: true,
        max: 100,
        padding: -5,
        stepSize: 20
      }
    })
    options.layout = {
      padding: {
        left: 2,
        right: 2,
        top: 0,
        bottom: 0
      }
    }

    options.scales.xAxes[0].stacked = true;

    //x-axis
    if (differenceInDays(this.service.historyPeriod.to, this.service.historyPeriod.from) >= 5) {
      options.scales.xAxes[0].time.unit = "day";
    } else {
      options.scales.xAxes[0].time.unit = "hour";
    }

    //y-axis
    options.scales.yAxes[0].id = "yAxis1"
    options.scales.yAxes[0].scaleLabel.labelString = "Cent / kWh";
    options.scales.yAxes[0].scaleLabel.padding = -2;
    options.scales.yAxes[0].scaleLabel.fontSize = 11;
    options.scales.yAxes[0].ticks.padding = -5;
    options.tooltips.callbacks.label = function (tooltipItem: TooltipItem, data: Data) {
      let label = data.datasets[tooltipItem.datasetIndex].label;
      let value = tooltipItem.yLabel;

      if (!value) {
        return;
      }
      if (label == translate.instant('General.soc')) {
        return label + ": " + formatNumber(value, 'de', '1.0-0') + " %";
        // } else if (label == 'Predicted Soc without logic') {
        //   return label + ": " + formatNumber(value, 'de', '1.0-0') + " %";
      } else {
        return label + ": " + formatNumber(value, 'de', '1.0-4') + " Cent/kWh";
      }
    }
    this.options = options;
  }

  public getChartHeight(): number {
    return window.innerHeight / 1.3;
  }
}