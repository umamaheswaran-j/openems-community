import { Component } from '@angular/core';
import { AbstractFlatWidget } from 'src/app/shared/genericComponents/flat/abstract-flat-widget';
import { DefaultTypes } from 'src/app/shared/service/defaulttypes';
import { ChannelAddress, CurrentData, Utils } from 'src/app/shared/shared';
import { Modal } from '../modal/modal';

@Component({
  selector: 'Controller_Ess_FixActivePower',
  templateUrl: './flat.html'
})
export class Flat extends AbstractFlatWidget {

  public readonly CONVERT_WATT_TO_KILOWATT = Utils.CONVERT_WATT_TO_KILOWATT;
  public readonly CONVERT_MANUAL_ON_OFF = Utils.CONVERT_MANUAL_ON_OFF(this.translate);

  public chargeDischargePower: { name: string, value: number };
  public propertyMode: DefaultTypes.ManualOnOff = null;

  protected override getChannelAddresses(): ChannelAddress[] {
    return [
      new ChannelAddress(this.component.id, "_PropertyPower"),
      new ChannelAddress(this.component.id, "_PropertyMode")
    ];
  }

  protected override onCurrentData(currentData: CurrentData) {
    this.chargeDischargePower = Utils.convertChargeDischargePower(this.translate, currentData.thisComponent['_PropertyPower']);
    this.propertyMode = currentData.thisComponent['_PropertyMode'];
  }

  async presentModal() {
    if (!this.isInitialized) {
      return;
    }
    const modal = await this.modalController.create({
      component: Modal,
      componentProps: {
        component: this.component
      }
    });
    return await modal.present();
  }
}
