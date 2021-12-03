import moment from "../moment";
import {exportBookingService} from "../services/export-booking.service";
import {AxiosResponse} from "axios";

export type ExportBookingBody = {
    format: string,
    view: string,
    startdate: Date,
    enddate: Date,
    resourceIds: Array<any>,
    usertimezone: string
};

export class ExportBooking {
    format: string;
    exportView: string;
    display: {
        state: number,
        STATE_FORMAT: number,
        STATE_RESOURCES: number,
        STATE_DATE: number,
        STATE_VIEW: number
    };
    startDate: any; // could be Date or Moment
    endDate: any; // could be Date or Moment
    resources: Array<any>;
    resourcesToTake: string;

    constructor() {
        this.format = "PDF";
        this.exportView = "WEEK";
        this.startDate = moment().day(1).toDate();
        this.endDate = moment().day(7).toDate();
        this.resources = [];
        this.resourcesToTake = "selected";
    }

    toJSON(): ExportBookingBody {
        return {
            format: this.format.toUpperCase(),
            view: this.exportView,
            startdate: this.startDate,
            enddate: this.endDate,
            resourceIds: this.resources,
            usertimezone: moment.tz.guess()
        };
    };

    send(): Promise<AxiosResponse> {
        return exportBookingService.export(this);
    }

}