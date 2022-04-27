import {ng, notify} from 'entcore'
import {Observable, Subject} from "rxjs";
import http, {AxiosPromise, AxiosResponse} from "axios";
import {IResourceTypeResponse, ResourceType} from "../models/resource-type.model";
import {IResourceResponse, Resource} from "../models/resource.model";
import {Booking, IBookingResponse} from "../models/booking.model";
import {SlotLit, Slot, ISlotResponse} from "../models/slot.model";

export interface IBookingService {
    getResourceTypes(structureId: string): Promise<Array<ResourceType>>;
    getResources(structureId: string): Promise<Array<Resource>>;
    getResource(resourceId: string): Promise<Resource>;
    getBookings(structureId: string): Promise<Array<Booking>>;
    getBooking(bookingId: string): Promise<Booking>;
    getSlots(slotProfileId: string): Promise<SlotLit>;
    getStructureSlots(structureId: string): Promise<SlotLit>;
}

export class BookingService implements IBookingService {

    async getResourceTypes(structureId: string): Promise<Array<ResourceType>> {
        return http.get(`rbs/types?structureid=${structureId}`)
            .then((res: AxiosResponse) => res.data.map((type: IResourceTypeResponse) => new ResourceType().build(type)));
    };

    async getResources(typeId: string): Promise<Array<Resource>> {
        return http.get(`rbs/resources?typeid=${typeId}`)
            .then((res: AxiosResponse) => res.data.map((resource: IResourceResponse) => new Resource().build(resource)));
    };

    async getResource(resourceId: string): Promise<Resource> {
        return http.get(`rbs/resource/${resourceId}`)
            .then((res: AxiosResponse) => new Resource().build(res.data));
    };

    async getBookings(resourceId: string): Promise<Array<Booking>> {
        return http.get(`rbs/resource/${resourceId}/bookings`)
            .then((res: AxiosResponse) => res.data.map((booking : IBookingResponse) => new Booking().build(booking)));
    };

    async getBooking(bookingId: string): Promise<Booking> {
        return http.get(`rbs/booking/${bookingId}`)
            .then((res: AxiosResponse) => new Booking().build(res.data));
    };

    async getSlots(slotProfileId: string): Promise<SlotLit> {
        return http.get(`/rbs/slotprofiles/${slotProfileId}/slots`)
            .then((slotList : AxiosResponse) => slotList.data.slots.map((slot : ISlotResponse) => new Slot().build(slot)));
    };

    async getStructureSlots(structureId: string): Promise<SlotLit> {
        return http.get(`/rbs/slotprofiles/schools/${structureId}`)
            .then((slotList : AxiosResponse) => slotList.data[0].slots.map((slot: ISlotResponse) => new Slot().build(slot)));
    };
}


export const bookingService = ng.service('BookingService', BookingService);