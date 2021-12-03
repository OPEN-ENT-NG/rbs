import {ng} from 'entcore'
import http, {AxiosResponse} from 'axios';

export interface IExportBookingService {
    export(exportPayload: any): Promise<AxiosResponse>;
}

export const exportBookingService: IExportBookingService = {

    export: (exportPayload: any): Promise<AxiosResponse> => {
        return http.post('/rbs/bookings/export', exportPayload);
    }

};

export const ExportBookingService = ng.service('ExportBookingService', (): IExportBookingService => exportBookingService);
