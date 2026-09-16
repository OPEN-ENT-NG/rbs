import {idiom, ng, notify} from 'entcore';
import {http, HttpResponse} from 'entcore-toolkit';
import {Availability} from "../models/Availability";

export interface AvailabilityService {
    list() : Promise<HttpResponse>;
    listByResource(resourceId: number, isUnavailability: boolean) : Promise<HttpResponse>;
    save(availability: Availability) : Promise<HttpResponse>;
    create(availability: Availability) : Promise<HttpResponse>;
    update(availability: Availability) : Promise<HttpResponse>;
    delete(availability: Availability) : Promise<HttpResponse>;
    deleteAll(resourceId: number, deleteUnavailability: boolean) : Promise<HttpResponse>;
}

export const availabilityService: AvailabilityService = {
    async list() : Promise<HttpResponse> {
        try {
            return http.get(`/rbs/availability`);
        } catch (err) {
            notify.error(idiom.translate('rbs.availability.service.list.error'));
            throw err;
        }
    },

    async listByResource(resourceId, isUnavailability) : Promise<HttpResponse> {
        try {
            return http.get(`/rbs/resource/${resourceId}/availability?is_unavailability=${isUnavailability}`);
        } catch (err) {
            notify.error(idiom.translate('rbs.availability.service.listByResource.error'));
            throw err;
        }
    },

    async save(availability) : Promise<HttpResponse> {
        return availability.id ? await this.update(availability) : await this.create(availability);
    },

    async create(availability) : Promise<HttpResponse> {
        try {
            availability.formatDateTimeToUnix();
            return http.post(`/rbs/resource/${availability.resource_id}/availability`, availability);
        } catch (err) {
            notify.error(idiom.translate('rbs.availability.service.create.error'));
            throw err;
        }
    },

    async update(availability) : Promise<HttpResponse> {
        try {
            availability.formatDateTimeToUnix();
            return http.put(`/rbs/resource/${availability.resource_id}/availability/${availability.id}`, availability);
        } catch (err) {
            notify.error(idiom.translate('rbs.availability.service.update.error'));
            throw err;
        }
    },

    async delete(availability) : Promise<HttpResponse> {
        try {
            return http.delete(`/rbs/resource/${availability.resource_id}/availability/${availability.id}`);
        } catch (e) {
            notify.error(idiom.translate('rbs.availability.service.delete.error'));
            throw e;
        }
    },

    async deleteAll(resourceId, deleteUnavailability) : Promise<HttpResponse> {
        try {
            return http.delete(`/rbs/resource/${resourceId}/availability/all/${deleteUnavailability}`);
        } catch (e) {
            notify.error(idiom.translate('rbs.availability.service.delete.error'));
            throw e;
        }
    }
};

export const AvailabilityService = ng.service('AvailabilityService', (): AvailabilityService => availabilityService);