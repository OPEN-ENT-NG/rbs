import {ng, notify} from 'entcore'
import {Observable, Subject} from "rxjs";
import http, {AxiosPromise, AxiosResponse} from "axios";
import {IResourceTypeResponse, ResourceType} from "../models/resource-type.model";
import {IResourceResponse, Resource} from "../models/resource.model";
import {Booking} from "../models/booking.model";

export interface IBookingService {
    getResourceTypes(structureId : String) : Promise<Array<ResourceType>>;
    getResources(structureId : String) : Promise<Array<Resource>>;
    getBookings(structureId : String) : Promise<Array<Booking>>;
    // getAvailability(resourceId : String) : Promise<AxiosResponse[]>;
    // getString() : String;
}

export class BookingService implements IBookingService {

    async getResourceTypes(structureId : String): Promise<Array<ResourceType>> {
        return http.get(`rbs/types?structureid=${structureId}`)
            .then((res: AxiosResponse) => res.data.map((type: IResourceTypeResponse) => new ResourceType().build(type)));
    }

    async getResources(typeId : String) : Promise<Array<Resource>> {
        return http.get(`rbs/resources?typeid=${typeId}`)
            .then((res: AxiosResponse) => res.data.map((resource: IResourceResponse) => new Resource().build(resource)));
    }

    async getBookings(resourceId : String) : Promise<Array<Booking>> {
        return http.get(`rbs/resource/${resourceId}/bookings`)
            .then((res: AxiosResponse) => console.log(res));
    }

    // async getAvailability(resourceId : String) : Promise<AxiosResponse[]> {
    //     return await Promise.all([
    //         http.get(`rbs/resource/${resourceId}/availability?is_unavailability=true`),
    //         http.get(`rbs/resource/${resourceId}/availability?is_unavailability=false`)
    //     ]);
    // }

    // async getSlotProfiles(structId : String) : Promise<AxiosResponse> {
    //     return await http.get('/rbs/slotprofiles/schools/' + structId).then((profiles : AxiosResponse) => (<any>profiles.data));
    // };
    //
    // async getSlots(slotProfileId : String) {
    //     return http.get('/rbs/slotprofiles/' + slotProfileId + '/slots').then((slots : AxiosResponse) => (<any>slots.data));
    // };

    // getString(): String {
    //     return "toto0";
    // }

}


export const bookingService = ng.service('BookingService', BookingService);