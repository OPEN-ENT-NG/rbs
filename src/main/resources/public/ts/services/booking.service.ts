import {ng, notify} from 'entcore'
import {Observable, Subject} from "rxjs";
import http, {AxiosPromise, AxiosResponse} from "axios";
import {IResourceTypeResponse, ResourceType} from "../models/resource-type.model";
import {IResourceResponse, Resource} from "../models/resource.model";
import {Booking, IBookingResponse} from "../models/booking.model";
import {SlotLit, Slot, ISlotResponse} from "../models/slot.model";

export interface IBookingService {
    getResourceTypes(structureId: String): Promise<Array<ResourceType>>;
    getResources(structureId: String): Promise<Array<Resource>>;
    getBookings(structureId: String): Promise<Array<Booking>>;
    getSlots(slotProfileId: String): Promise<SlotLit>;
    getStructureSlots(structureId: String): Promise<SlotLit>;
}

export class BookingService implements IBookingService {

    async getResourceTypes(structureId : String): Promise<Array<ResourceType>> {
        return http.get(`rbs/types?structureid=${structureId}`)
            .then((res: AxiosResponse) => res.data.map((type: IResourceTypeResponse) => new ResourceType().build(type)));
    };

    async getResources(typeId : String) : Promise<Array<Resource>> {
        return http.get(`rbs/resources?typeid=${typeId}`)
            .then((res: AxiosResponse) => res.data.map((resource: IResourceResponse) => new Resource().build(resource)));
    };

    async getBookings(resourceId : String) : Promise<Array<Booking>> {
        return http.get(`rbs/resource/${resourceId}/bookings`)
            .then((res: AxiosResponse) => res.data.map((booking : IBookingResponse) => new Booking().build(booking)));
    };

    async getSlots(slotProfileId : String) : Promise<SlotLit> {
        return http.get(`/rbs/slotprofiles/${slotProfileId}/slots`)
            .then((slotList : AxiosResponse) => slotList.data.slots.map((slot : ISlotResponse) => new Slot().build(slot)));
    };

    async getStructureSlots(structureId : String): Promise<SlotLit> {
        return http.get(`/rbs/slotprofiles/schools/${structureId}`)
            .then((slotList : AxiosResponse) => slotList.data[0].slots.map((slot: ISlotResponse) => new Slot().build(slot)));
    };
}


export const bookingService = ng.service('BookingService', BookingService);