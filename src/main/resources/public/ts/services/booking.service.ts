import {ng} from 'entcore'
import {Observable, Subject} from "rxjs";
import http, {AxiosPromise, AxiosResponse} from "axios";

export interface IBookingService {
    getResourceTypes(structureId : String) : Promise<AxiosResponse>;
    getResources(structureId : String) : Promise<AxiosResponse>;
    getString() : String;
}

export class BookingService implements IBookingService {

    async getResourceTypes(structureId : String): Promise<AxiosResponse> {
        return await http.get(`rbs/types?structureid=${structureId}`).then((types : AxiosResponse) => (<any>types.data));
    }

    async getResources(typeId : String): Promise<AxiosResponse> {
        return await http.get(`rbs/resources?typeid=${typeId}`).then((types : AxiosResponse) => (<any>types.data));
    }

    // async delete() {
    //     await http.delete('/calendar/' + this.calendar[0]._id + '/event/' + this._id);
    // };

    // getResource(): Promise<AxiosPromise> {
    //     return http.get(`rbs/`)
    // }

    getString(): String {
        return "toto0";
    }

}

// export const bookingFormService = new BookingService();

export const bookingService = ng.service('BookingService', BookingService);