import {ng} from 'entcore'
import {http, HttpResponse} from 'entcore-toolkit';

export interface IExportBookingService {
    export(exportPayload: any): Promise<HttpResponse>;
}

export const exportBookingService: IExportBookingService = {

    export: (exportPayload: any): Promise<HttpResponse> => {
        return http.post('/rbs/bookings/export', exportPayload);
    }

};

export const ExportBookingService = ng.service('ExportBookingService', (): IExportBookingService => exportBookingService);
