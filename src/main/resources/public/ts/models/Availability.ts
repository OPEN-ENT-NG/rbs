import {idiom, notify} from "entcore";
import {availabilityService} from "../services/AvailabilityService";
import moment from "../moment";

export class Availability {
    id: number;
    resource_id: number;
    start_date: any;
    end_date: any;
    start_time: any;
    end_time: any;
    days: boolean[];
    is_unavailability: boolean;
    quantity: number;
    iana: string;

    constructor(resource?: any) {
        let now = moment();
        let nextWeek = moment().add(7, 'days').add(1,'hours');
        this.id = null;
        this.resource_id = resource && resource.id ? resource.id : null;
        this.start_date = now;
        this.end_date = nextWeek;
        this.start_time = now;
        this.end_time = nextWeek;
        this.days = [false, false, false, false, false, false, false];
        this.is_unavailability = resource && resource.is_available ? resource.is_available : false;
        this.quantity = 1;
        this.iana = moment.tz.guess();
    }

    toJson() : Object {
        return {
            id: this.id,
            resource_id: this.resource_id,
            start_date: this.start_date,
            end_date: this.end_date,
            start_time: this.start_time,
            end_time: this.end_time,
            days: this.days,
            is_unavailability: this.is_unavailability,
            quantity: this.quantity
        }
    }

    setFromJson = (data: any) : void => {
        for (let key in data) {
            this[key] = data[key];
            if ((key === 'start_date' || key === 'end_date') && data[key]) {
                this[key] = moment.tz(moment.utc(this[key]), moment.tz.guess());
            }
            if ((key === 'start_time' || key === 'end_time') && data[key]) {
                let time = data[key].split(':');
                let momentTime = moment().hours(time[0]).minutes(time[1]);
                this[key] = moment.tz(moment.utc(momentTime), moment.tz.guess());
            }
            if (key === 'days' && !(data[key] instanceof Array)) {
                this[key] = [];
                for (let i = 0; i < data[key].length; i++) {
                    this[key].push(data[key][i] === '1');
                }
            }
        }
    };

    formatDateTimeToUnix = () : void => {
        this.start_date = moment(this.start_date).unix();
        this.end_date = moment(this.end_date).unix();
        this.start_time = moment(this.start_time).unix();
        this.end_time = moment(this.end_time).unix();
    };
}

export class Availabilities {
    all: Availability[];

    constructor() {
        this.all = [];
    }

    sync = async (resourceId: number, is_unavailability: boolean) : Promise<void> => {
        try {
            this.all = [];
            let { data } = await availabilityService.listByResource(resourceId, is_unavailability);
            for (let a of data) {
                let tempAvailability = new Availability();
                tempAvailability.setFromJson(a);
                this.all.push(tempAvailability);
            }
            console.log(this.all);
        } catch (e) {
            notify.error(idiom.translate('rbs.error.availability.sync'));
            throw e;
        }
    }
}