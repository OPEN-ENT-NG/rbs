import {ng, notify} from 'entcore'
import {Observable, Subject} from "rxjs";
import http, {AxiosPromise, AxiosResponse} from "axios";

export interface IBookingService {
    getResourceTypes(structureId : String) : Promise<AxiosResponse>;
    getResources(structureId : String) : Promise<AxiosResponse>;
    getAvailability(resourceId : String) : Promise<AxiosResponse[]>;
    getString() : String;
}

export class BookingService implements IBookingService {

    async getResourceTypes(structureId : String): Promise<AxiosResponse> {
        return await http.get(`rbs/types?structureid=${structureId}`).then((types : AxiosResponse) => (<any>types.data));
    }

    async getResources(typeId : String) : Promise<AxiosResponse> {
        return await http.get(`rbs/resources?typeid=${typeId}`).then((resources : AxiosResponse) => (<any>resources.data));
    }

    async getAvailability(resourceId : String) : Promise<AxiosResponse[]> {
        return await Promise.all([
            http.get(`rbs/resource/${resourceId}/availability?is_unavailability=true`),
            http.get(`rbs/resource/${resourceId}/availability?is_unavailability=false`)
        ]);
    }

    async getSlotProfiles(structId : String) : Promise<AxiosResponse> {
        return await http.get('/rbs/slotprofiles/schools/' + structId).then((profiles : AxiosResponse) => (<any>profiles.data));
            // .done(function (data) {
            //     returnData(callback, [data]);
            // }).error(function (e) {
            //     console.log('error get \'rbs/slotprofiles/schools\'', e);
            //     //var error = JSON.parse(e.responseText);
            //     //notify.error(error.error);
            // });
    };

    async getSlots(slotProfileId, callback) {
        return http.get('/rbs/slotprofiles/' + slotProfileId + '/slots').then((slots : AxiosResponse) => (<any>slots.data));
            // .done(function (data) {
            //     returnData(callback, [data]);
            // }).error(function (e) {
            //     var error = JSON.parse(e.responseText);
            //     notify.error(error.error);
            // });
    };


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