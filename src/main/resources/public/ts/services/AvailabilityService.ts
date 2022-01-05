import {idiom, ng, notify} from 'entcore';
import http, {AxiosResponse} from 'axios';
import moment from "../moment";

export interface AvailabilityService {
    list() : Promise<AxiosResponse>;
    listByResource(resourceId: number, isUnavailability: boolean) : Promise<AxiosResponse>;
    save(availability: any) : Promise<AxiosResponse>;
    create(availability: any) : Promise<AxiosResponse>;
    update(availability: any) : Promise<AxiosResponse>;
    delete(availability: any) : Promise<AxiosResponse>;
    deleteAll(resourceId: number) : Promise<AxiosResponse>;
}

export const availabilityService: AvailabilityService = {
    async list() : Promise<AxiosResponse> {
        try {
            return http.get(`/rbs/availability`);
        } catch (err) {
            notify.error(idiom.translate('rbs.availability.service.list.error'));
            throw err;
        }
    },

    async listByResource(resourceId, isUnavailability) : Promise<AxiosResponse> {
        try {
            return http.get(`/rbs/resource/${resourceId}/availability?is_unavailability=${isUnavailability}`);
        } catch (err) {
            notify.error(idiom.translate('rbs.availability.service.listByResource.error'));
            throw err;
        }
    },

    async save(availability) : Promise<AxiosResponse> {
        return availability.id ? await this.update(availability) : await this.create(availability);
    },

    async create(availability) : Promise<AxiosResponse> {
        try {
            availability.formatDateTimeToUnix();
            return http.post(`/rbs/resource/${availability.resource_id}/availability`, availability);
        } catch (err) {
            notify.error(idiom.translate('rbs.availability.service.create.error'));
            throw err;
        }
    },

    async update(availability) : Promise<AxiosResponse> {
        try {
            availability.formatDateTimeToUnix();
            return http.put(`/rbs/resource/${availability.resource_id}/availability/${availability.id}`, availability);
        } catch (err) {
            notify.error(idiom.translate('rbs.availability.service.update.error'));
            throw err;
        }
    },

    async delete(availability) : Promise<AxiosResponse> {
        try {
            return http.delete(`/rbs/resource/${availability.resource_id}/availability/${availability.id}`);
        } catch (e) {
            notify.error(idiom.translate('rbs.availability.service.delete.error'));
            throw e;
        }
    },

    async deleteAll(resourceId) : Promise<AxiosResponse> {
        try {
            return http.delete(`/rbs/resource/${resourceId}/availability/all`);
        } catch (e) {
            notify.error(idiom.translate('rbs.availability.service.delete.error'));
            throw e;
        }
    }
};

export const AvailabilityService = ng.service('AvailabilityService', (): AvailabilityService => availabilityService);